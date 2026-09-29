package com.jadroid.launcher.data.download

import com.jadroid.launcher.core.AppLog
import com.jadroid.launcher.core.HttpClient
import com.jadroid.launcher.core.sha1Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class DownloadItem(
    val url: String,
    val target: File,
    val sha1: String? = null,
    val size: Long? = null,
    val label: String = ""
)

data class DownloadProgress(
    val completed: Int = 0,
    val total: Int = 0,
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
    val currentLabel: String = ""
) {
    val fraction: Float get() = if (total <= 0) 0f else completed.toFloat() / total.toFloat()
}

data class DownloadFailure(val item: DownloadItem, val error: String)

data class DownloadSummary(
    val downloaded: Int,
    val skipped: Int,
    val bytes: Long,
    val failures: List<DownloadFailure>
) {
    val isSuccess: Boolean get() = failures.isEmpty()
}

/**
 * Parallel, checksum-verifying downloader for game files from the official Mojang repositories.
 * Files that already match the published size + SHA-1 are skipped, so re-running an install is cheap.
 */
class DownloadManager(private val http: HttpClient) {

    suspend fun run(
        items: List<DownloadItem>,
        concurrency: Int = 4,
        onProgress: suspend (DownloadProgress) -> Unit = {}
    ): DownloadSummary = coroutineScope {
        if (items.isEmpty()) return@coroutineScope DownloadSummary(0, 0, 0, emptyList())

        val totalBytes = items.sumOf { it.size ?: 0L }
        val completed = AtomicInteger(0)
        val downloaded = AtomicInteger(0)
        val skipped = AtomicInteger(0)
        val bytesDone = AtomicLong(0)
        val failures = ConcurrentLinkedQueue<DownloadFailure>()
        val progress = MutableStateFlow(DownloadProgress(0, items.size, 0, totalBytes, ""))
        val throttleLock = Any()
        var lastEmit = 0L

        fun publish(label: String, force: Boolean) {
            val now = System.currentTimeMillis()
            synchronized(throttleLock) {
                if (!force && now - lastEmit < 150L) return
                lastEmit = now
                progress.value = DownloadProgress(
                    completed = completed.get(),
                    total = items.size,
                    bytesDone = bytesDone.get(),
                    bytesTotal = totalBytes,
                    currentLabel = label
                )
            }
        }

        val watcher = launch { progress.collect { onProgress(it) } }
        val semaphore = Semaphore(concurrency.coerceIn(1, 16))

        val jobs = items.map { item ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        val didDownload = process(item) { delta ->
                            bytesDone.addAndGet(delta)
                            publish(item.label, force = false)
                        }
                        if (didDownload) downloaded.incrementAndGet() else skipped.incrementAndGet()
                    } catch (t: Throwable) {
                        if (t is kotlinx.coroutines.CancellationException) throw t
                        AppLog.w("Download failed: ${item.url}", t)
                        failures += DownloadFailure(item, t.message ?: t.javaClass.simpleName)
                    } finally {
                        completed.incrementAndGet()
                        publish(item.label, force = true)
                    }
                }
            }
        }
        jobs.awaitAll()
        watcher.cancelAndJoin()
        onProgress(DownloadProgress(items.size, items.size, bytesDone.get(), totalBytes, "finished"))
        AppLog.i(
            "Downloads finished: ${downloaded.get()} fetched, ${skipped.get()} up to date, " +
                "${failures.size} failed"
        )
        DownloadSummary(downloaded.get(), skipped.get(), bytesDone.get(), failures.toList())
    }

    /** Returns true when the file was actually fetched, false when it was already valid locally. */
    private suspend fun process(item: DownloadItem, onBytes: (Long) -> Unit): Boolean {
        if (isUpToDate(item)) return false
        val target = item.target
        val parent = target.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Cannot create directory ${parent.absolutePath}")
        }
        val partial = File(parent, target.name + ".part")
        if (partial.exists() && item.size != null && partial.length() > item.size) partial.delete()

        runInterruptible(Dispatchers.IO) { http.downloadBlocking(item.url, partial, onBytes) }

        val actualSize = partial.length()
        val expectedSize = item.size
        if (expectedSize != null && expectedSize > 0 && actualSize != expectedSize) {
            partial.delete()
            throw IOException("Size mismatch for ${target.name}: got $actualSize expected $expectedSize")
        }
        val expectedSha1 = item.sha1
        if (!expectedSha1.isNullOrBlank() && !partial.sha1Hex().equals(expectedSha1, true)) {
            partial.delete()
            throw IOException("Checksum mismatch for ${target.name}")
        }
        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        return true
    }

    private fun isUpToDate(item: DownloadItem): Boolean {
        val file = item.target
        if (!file.isFile) return false
        val expectedSize = item.size
        if (expectedSize != null && expectedSize > 0 && file.length() != expectedSize) return false
        val expectedSha1 = item.sha1
        if (!expectedSha1.isNullOrBlank() && !file.sha1Hex().equals(expectedSha1, true)) return false
        return true
    }
}
