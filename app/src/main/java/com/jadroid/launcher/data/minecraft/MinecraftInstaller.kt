package com.jadroid.launcher.data.minecraft

import com.jadroid.launcher.core.AppLog
import com.jadroid.launcher.core.JadroidJson
import com.jadroid.launcher.core.LauncherPaths
import com.jadroid.launcher.core.deleteQuietly
import com.jadroid.launcher.core.ensureDir
import com.jadroid.launcher.core.ensureParent
import com.jadroid.launcher.data.download.DownloadItem
import com.jadroid.launcher.data.download.DownloadManager
import com.jadroid.launcher.data.download.DownloadProgress
import com.jadroid.launcher.data.download.DownloadSummary
import com.jadroid.launcher.data.instance.Instance
import com.jadroid.launcher.data.mojang.AssetsIndex
import com.jadroid.launcher.data.mojang.LaunchFeatures
import com.jadroid.launcher.data.mojang.Library
import com.jadroid.launcher.data.mojang.MavenName
import com.jadroid.launcher.data.mojang.MojangRepository
import com.jadroid.launcher.data.mojang.OsProfile
import com.jadroid.launcher.data.mojang.RuleEvaluator
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/** A native library jar that has to be unpacked into the instance natives directory. */
data class NativeLibrary(
    val jar: File,
    val name: String,
    val exclude: List<String>
)

data class InstallPlan(
    val instanceId: String,
    val resolved: ResolvedVersion,
    val gameDir: File,
    val nativesDir: File,
    val clientJar: File,
    val classpath: List<File>,
    val downloads: List<DownloadItem>,
    val natives: List<NativeLibrary>,
    val assetIndexId: String?,
    val virtualAssets: Boolean,
    val mapToResources: Boolean
) {
    val totalBytes: Long get() = downloads.sumOf { it.size ?: 0L }
}

data class InstallResult(
    val versionId: String,
    val summary: DownloadSummary,
    val nativesExtracted: Int,
    val classpath: List<File>
)

/**
 * Turns an instance into a concrete list of files from the **official Mojang repositories**
 * (`libraries.minecraft.net` + `resources.download.minecraft.net`) and installs them locally,
 * including native library extraction.
 */
class MinecraftInstaller(
    private val mojang: MojangRepository,
    private val resolver: VersionResolver,
    private val paths: LauncherPaths,
    private val downloads: DownloadManager
) {

    suspend fun plan(
        instance: Instance,
        features: LaunchFeatures,
        os: OsProfile,
        includeAssets: Boolean = true
    ): InstallPlan {
        val resolved = resolver.resolve(instance)
        val json = resolved.json
        val gameDir = paths.gameDir(instance.id)
        val nativesDir = paths.nativesDir(instance.id)
        val clientJar = paths.clientJar(instance.id, resolved.id)
        val classpath = mutableListOf<File>()
        val items = mutableListOf<DownloadItem>()
        val natives = mutableListOf<NativeLibrary>()

        val featureMap = features.toMap()
        json.libraries.forEach { library ->
            if (!RuleEvaluator.isAllowed(library.rules, os, featureMap)) return@forEach
            resolveArtifact(library)?.let { artifact ->
                val path = artifact.path
                    ?: MavenName.parse(library.name)?.path()
                    ?: return@let
                val target = paths.libraryFile(path)
                val label = MavenName.parse(library.name)?.let { "${it.group}:${it.artifact}" } ?: path
                items += DownloadItem(
                    url = artifact.url,
                    target = target,
                    sha1 = artifact.sha1,
                    size = artifact.size,
                    label = label
                )
                classpath += target
            }
            nativeArtifact(library, os)?.let { native ->
                natives += NativeLibrary(
                    jar = native.jar,
                    name = library.name,
                    exclude = library.extract?.exclude.orEmpty()
                )
                items += native.download
            }
        }

        json.downloads?.client?.let { client ->
            items += DownloadItem(
                url = client.url,
                target = clientJar,
                sha1 = client.sha1,
                size = client.size,
                label = "minecraft-${resolved.id}"
            )
            classpath += clientJar
        }

        var assetIndexId: String? = null
        var virtual = false
        var mapToResources = false
        val ref = json.assetIndex
        if (includeAssets && ref != null) {
            val index = mojang.assetIndex(ref)
            assetIndexId = ref.id
            virtual = index.virtual == true
            mapToResources = index.mapToResources == true
            paths.assetIndexFile(ref.id).ensureParent()
                .writeText(JadroidJson.encodeToString(index))
            index.objects.forEach { (name, obj) ->
                items += DownloadItem(
                    url = mojang.assetObjectUrl(obj.hash),
                    target = paths.assetObjectFile(obj.hash),
                    sha1 = obj.hash,
                    size = obj.size,
                    label = "asset $name"
                )
            }
        }

        return InstallPlan(
            instanceId = instance.id,
            resolved = resolved,
            gameDir = gameDir,
            nativesDir = nativesDir,
            clientJar = clientJar,
            classpath = classpath,
            downloads = items,
            natives = natives,
            assetIndexId = assetIndexId,
            virtualAssets = virtual,
            mapToResources = mapToResources
        )
    }

    suspend fun install(
        plan: InstallPlan,
        concurrency: Int = 4,
        onStage: suspend (String) -> Unit = {},
        onProgress: suspend (DownloadProgress) -> Unit = {}
    ): InstallResult {
        plan.gameDir.ensureDir()
        plan.nativesDir.ensureDir()
        onStage("Downloading ${plan.downloads.size} files")
        val summary = downloads.run(plan.downloads, concurrency, onProgress)
        if (!summary.isSuccess) {
            AppLog.w("${summary.failures.size} file(s) could not be downloaded; the game may not start")
        }

        onStage("Extracting native libraries")
        plan.nativesDir.deleteQuietly()
        plan.nativesDir.ensureDir()
        var extracted = 0
        plan.natives.forEach { native ->
            if (!native.jar.isFile) {
                AppLog.w("Native jar missing, skipping: ${native.jar.name}")
                return@forEach
            }
            extracted += extractNatives(native.jar, plan.nativesDir, native.exclude)
        }
        AppLog.i("Extracted $extracted native libraries for ${plan.instanceId}")

        installLegacyAssets(plan)
        onStage("Ready")
        return InstallResult(plan.resolved.id, summary, extracted, plan.classpath)
    }

    /** Resolves the concrete jar that has to be downloaded for a classpath library. */
    private fun resolveArtifact(library: Library): PlannedArtifact? {
        val artifact = library.downloads?.artifact
        if (artifact != null) {
            val path = artifact.path ?: MavenName.parse(library.name)?.path()
            val url = artifact.url?.takeIf { it.isNotBlank() }
                ?: path?.let { mojang.libraryUrl(it, library.url) }
            if (url.isNullOrBlank()) return null
            return PlannedArtifact(url, path, artifact.sha1 ?: library.sha1, artifact.size ?: library.size)
        }
        // Mod loader libraries only carry a maven coordinate plus the repository they live in.
        val maven = MavenName.parse(library.name) ?: return null
        val path = maven.path()
        return PlannedArtifact(mavenUrl(library.url, path), path, library.sha1, library.size)
    }

    /** Resolves the native (classifier) jar for the current device ABI. */
    private fun nativeArtifact(library: Library, os: OsProfile): NativePlan? {
        if (library.natives.isEmpty()) return null
        val baseClassifier = library.natives[os.name]
            ?: library.natives.entries.firstOrNull { os.name.startsWith(it.key) }?.value
            ?: return null
        val classifiers = library.downloads?.classifiers.orEmpty()
        val candidates = buildList {
            if (os.arch == "arm64" || os.arch == "aarch64") add("$baseClassifier-arm64")
            add(baseClassifier)
            if (os.arch.isNotBlank()) add("$baseClassifier-${os.arch}")
        }
        val key = candidates.firstOrNull { classifiers.containsKey(it) } ?: baseClassifier
        val artifact = classifiers[key]
        val path = artifact?.path ?: MavenName.parse(library.name)?.withClassifier(key)?.path() ?: return null
        val target = paths.libraryFile(path)
        return NativePlan(
            jar = target,
            download = DownloadItem(
                url = artifact?.url?.takeIf { it.isNotBlank() } ?: mavenUrl(library.url, path),
                target = target,
                sha1 = artifact?.sha1 ?: library.sha1,
                size = artifact?.size ?: library.size,
                label = "$key natives"
            )
        )
    }

    private fun mavenUrl(base: String?, path: String): String {
        val trimmed = base?.trim()?.trimEnd('/')
        return when {
            trimmed.isNullOrBlank() -> mojang.libraryUrl(path)
            trimmed.endsWith(".jar") -> trimmed
            else -> "$trimmed/$path"
        }
    }

    private fun extractNatives(jar: File, destDir: File, exclude: List<String>): Int {
        val patterns = exclude.ifEmpty { DEFAULT_EXCLUDES }
        var count = 0
        ZipInputStream(jar.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (!entry.isDirectory && patterns.none { name.startsWith(it) }) {
                    val target = File(destDir, name)
                    target.parentFile?.ensureDir()
                    FileOutputStream(target).use { output -> zip.copyTo(output) }
                    target.setReadable(true, false)
                    target.setExecutable(true, false)
                    count++
                }
                zip.closeEntry()
            }
        }
        return count
    }

    /** Handles the pre-1.7 `virtual` / `map_to_resources` asset layouts. */
    private fun installLegacyAssets(plan: InstallPlan) {
        val indexId = plan.assetIndexId ?: return
        if (!plan.virtualAssets && !plan.mapToResources) return
        val indexFile = paths.assetIndexFile(indexId)
        if (!indexFile.isFile) return
        val index = runCatching { JadroidJson.decodeFromString<AssetsIndex>(indexFile.readText()) }.getOrNull()
            ?: return
        val targetRoot = if (plan.mapToResources) {
            File(plan.gameDir, "resources")
        } else {
            File(paths.assets, "virtual/$indexId")
        }
        var copied = 0
        index.objects.forEach { (name, obj) ->
            val source = paths.assetObjectFile(obj.hash)
            if (!source.isFile) return@forEach
            val target = File(targetRoot, name)
            target.parentFile?.ensureDir()
            if (!target.isFile || target.length() != source.length()) {
                source.copyTo(target, overwrite = true)
                copied++
            }
        }
        AppLog.i("Prepared $copied legacy assets in ${targetRoot.absolutePath}")
    }

    private data class PlannedArtifact(val url: String, val path: String?, val sha1: String?, val size: Long?)

    private data class NativePlan(val jar: File, val download: DownloadItem)

    private companion object {
        val DEFAULT_EXCLUDES = listOf("META-INF/", "META-INF", "module-info.class")
    }
}
