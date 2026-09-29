package com.jadroid.launcher.data.mojang

import com.jadroid.launcher.core.AppLog
import com.jadroid.launcher.core.JadroidJson
import com.jadroid.launcher.core.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString

/**
 * Wraps the **official Mojang repositories**:
 *  - version manifest / version json: `piston-meta.mojang.com`
 *  - libraries: `libraries.minecraft.net`
 *  - assets: `resources.download.minecraft.net`
 */
class MojangRepository(private val http: HttpClient) {

    private val mutex = Mutex()
    private var manifestCache: VersionManifest? = null
    private val versionCache = mutableMapOf<String, VersionJson>()
    private val assetIndexCache = mutableMapOf<String, AssetsIndex>()

    suspend fun manifest(forceRefresh: Boolean = false): VersionManifest = mutex.withLock {
        manifestCache?.takeIf { !forceRefresh }?.let { return@withLock it }
        AppLog.i("Fetching Mojang version manifest from $VERSION_MANIFEST_URL")
        val json = http.getText(VERSION_MANIFEST_URL)
        val manifest = JadroidJson.decodeFromString<VersionManifest>(json)
        manifestCache = manifest
        manifest
    }

    suspend fun versionJson(versionId: String, manifestUrl: String? = null): VersionJson = mutex.withLock {
        versionCache[versionId]?.let { return@withLock it }
        val url = manifestUrl?.takeIf { it.isNotBlank() } ?: manifest().versions
            .firstOrNull { it.id == versionId }
            ?.url
            ?: throw IllegalStateException("Version $versionId not found in the Mojang manifest")
        AppLog.i("Fetching version json for $versionId")
        val json = http.getText(url)
        val parsed = JadroidJson.decodeFromString<VersionJson>(json)
        versionCache[versionId] = parsed
        parsed
    }

    suspend fun assetIndex(ref: AssetIndexRef): AssetsIndex = mutex.withLock {
        assetIndexCache[ref.id]?.let { return@withLock it }
        AppLog.i("Fetching asset index ${ref.id} (${ref.totalSize ?: 0} bytes total)")
        val json = http.getText(ref.url)
        val parsed = JadroidJson.decodeFromString<AssetsIndex>(json)
        assetIndexCache[ref.id] = parsed
        parsed
    }

    fun cachedVersionJson(versionId: String): VersionJson? = versionCache[versionId]

    /** URL used for a library artifact that has no explicit download url in the version json. */
    fun libraryUrl(path: String, baseOverride: String? = null): String =
        (baseOverride ?: LIBRARIES_URL).trimEnd('/') + "/" + path

    fun assetObjectUrl(hash: String): String = "$RESOURCES_URL/${hash.take(2)}/$hash"

    companion object {
        const val VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        const val LIBRARIES_URL = "https://libraries.minecraft.net"
        const val RESOURCES_URL = "https://resources.download.minecraft.net"
    }
}
