package com.jadroid.launcher.data.mods

import com.jadroid.launcher.core.AppLog
import com.jadroid.launcher.core.LauncherPaths
import com.jadroid.launcher.core.ensureDir
import com.jadroid.launcher.core.sha1Hex
import com.jadroid.launcher.data.download.DownloadItem
import com.jadroid.launcher.data.download.DownloadManager
import java.io.File
import java.io.InputStream

data class InstalledMod(
    val file: File,
    val name: String,
    val version: String?,
    val enabled: Boolean,
    val sizeBytes: Long
) {
    val isFabricJar: Boolean get() = file.name.endsWith(".jar")
}

/**
 * Manages the `mods` folder of an instance: listing, enable/disable (`.disabled` suffix),
 * installs from Modrinth and manual imports.
 */
class ModManager(
    private val paths: LauncherPaths,
    private val downloads: DownloadManager
) {

    fun list(instanceId: String): List<InstalledMod> {
        val dir = paths.modsDir(instanceId).ensureDir()
        return dir.listFiles()
            .orEmpty()
            .filter { it.isFile && (it.name.endsWith(".jar") || it.name.endsWith(".jar.disabled")) }
            .map { file -> toMod(file) }
            .sortedBy { it.name.lowercase() }
    }

    fun toggle(mod: InstalledMod): InstalledMod? {
        val target = if (mod.enabled) {
            File(mod.file.parentFile, mod.file.name + DISABLED_SUFFIX)
        } else {
            File(mod.file.parentFile, mod.file.name.removeSuffix(DISABLED_SUFFIX))
        }
        if (!mod.file.renameTo(target)) {
            AppLog.w("Could not rename ${mod.file.name}")
            return null
        }
        AppLog.i("${if (mod.enabled) "Disabled" else "Enabled"} ${target.name}")
        return toMod(target)
    }

    fun delete(mod: InstalledMod): Boolean {
        val deleted = mod.file.delete()
        if (deleted) AppLog.i("Deleted mod ${mod.file.name}")
        return deleted
    }

    /** Downloads a mod jar (from Modrinth) into the instance mods folder, verifying its SHA-1. */
    suspend fun installFromUrl(
        instanceId: String,
        url: String,
        filename: String,
        sha1: String? = null,
        size: Long? = null
    ): File {
        val dir = paths.modsDir(instanceId).ensureDir()
        val destination = File(dir, sanitize(filename))
        val summary = downloads.run(
            listOf(
                DownloadItem(
                    url = url,
                    target = destination,
                    sha1 = sha1,
                    size = size,
                    label = destination.name
                )
            ),
            concurrency = 1
        )
        val failure = summary.failures.firstOrNull()
        if (failure != null) throw IllegalStateException("Download failed: ${failure.error}")
        return destination
    }

    /** Imports a local jar (picked through the system file picker) into the mods folder. */
    fun importStream(instanceId: String, filename: String, stream: InputStream): File {
        val dir = paths.modsDir(instanceId).ensureDir()
        val destination = File(dir, sanitize(filename))
        stream.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
        AppLog.i("Imported mod ${destination.name} (${destination.sha1Hex().take(8)})")
        return destination
    }

    fun parseModName(file: File): String = file.name.removeSuffix(DISABLED_SUFFIX)

    private fun toMod(file: File): InstalledMod {
        val enabled = !file.name.endsWith(DISABLED_SUFFIX)
        val base = file.name.removeSuffix(DISABLED_SUFFIX).removeSuffix(".jar")
        val version = base.substringAfterLast('-').takeIf { it.firstOrNull()?.isDigit() == true }
        return InstalledMod(
            file = file,
            name = base,
            version = version,
            enabled = enabled,
            sizeBytes = file.length()
        )
    }

    private fun sanitize(filename: String): String =
        filename.replace(Regex("[^A-Za-z0-9._+\\-]"), "_").take(120)

    companion object {
        const val DISABLED_SUFFIX = ".disabled"
    }
}
