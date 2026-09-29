package com.jadroid.launcher.data.mojang

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** https://piston-meta.mojang.com/mc/game/version_manifest_v2.json */
@Serializable
data class VersionManifest(
    val latest: ManifestLatest = ManifestLatest(),
    val versions: List<ManifestVersion> = emptyList()
)

@Serializable
data class ManifestLatest(
    val release: String = "",
    val snapshot: String = ""
)

@Serializable
data class ManifestVersion(
    val id: String,
    val type: String = "release",
    val url: String = "",
    val time: String = "",
    val releaseTime: String = "",
    val sha1: String? = null,
    @SerialName("complianceLevel") val complianceLevel: Int? = null
)

/** A version json (the per-version descriptor hosted on piston-meta / piston-data). */
@Serializable
data class VersionJson(
    val id: String,
    val type: String? = null,
    val mainClass: String? = null,
    val assets: String? = null,
    val assetIndex: AssetIndexRef? = null,
    val downloads: VersionDownloads? = null,
    val libraries: List<Library> = emptyList(),
    val arguments: Arguments? = null,
    /** Pre-1.13 versions use a single argument string instead of the `arguments` object. */
    @SerialName("minecraftArguments") val legacyMinecraftArguments: String? = null,
    @SerialName("javaVersion") val javaVersion: JavaVersionInfo? = null,
    @SerialName("inheritsFrom") val inheritsFrom: String? = null,
    @SerialName("releaseTime") val releaseTime: String? = null,
    @SerialName("complianceLevel") val complianceLevel: Int? = null,
    val logging: JsonObject? = null
)

@Serializable
data class JavaVersionInfo(
    val component: String = "jre",
    @SerialName("majorVersion") val majorVersion: Int = 8
)

@Serializable
data class VersionDownloads(
    val client: DownloadInfo? = null,
    val server: DownloadInfo? = null,
    @SerialName("client_mappings") val clientMappings: DownloadInfo? = null,
    @SerialName("server_mappings") val serverMappings: DownloadInfo? = null
)

@Serializable
data class DownloadInfo(
    val sha1: String? = null,
    val size: Long? = null,
    val url: String = ""
)

@Serializable
data class AssetIndexRef(
    val id: String,
    val sha1: String? = null,
    val size: Long? = null,
    @SerialName("totalSize") val totalSize: Long? = null,
    val url: String = ""
)

@Serializable
data class AssetsIndex(
    val objects: Map<String, AssetObject> = emptyMap(),
    val virtual: Boolean? = null,
    @SerialName("map_to_resources") val mapToResources: Boolean? = null
)

@Serializable
data class AssetObject(
    val hash: String,
    val size: Long = 0
)

@Serializable
data class Arguments(
    val game: List<ArgumentValue> = emptyList(),
    val jvm: List<ArgumentValue> = emptyList()
)

@Serializable
data class Library(
    val name: String,
    val downloads: LibraryDownloads? = null,
    /** Maps an OS name ("linux", "windows", "osx") to a maven classifier such as "natives-linux". */
    val natives: Map<String, String> = emptyMap(),
    val rules: List<Rule> = emptyList(),
    val extract: Extract? = null,
    /** Maven repository root for entries without an explicit `downloads.artifact.url` (used by Fabric). */
    val url: String? = null,
    val sha1: String? = null,
    val size: Long? = null
)

@Serializable
data class LibraryDownloads(
    val artifact: LibraryArtifact? = null,
    val classifiers: Map<String, LibraryArtifact> = emptyMap()
)

@Serializable
data class LibraryArtifact(
    val path: String? = null,
    val sha1: String? = null,
    val size: Long? = null,
    val url: String? = null
)

@Serializable
data class Extract(
    val exclude: List<String> = emptyList()
)

@Serializable
data class Rule(
    val action: String = "allow",
    val os: OsRule? = null,
    val features: Map<String, Boolean> = emptyMap()
)

@Serializable
data class OsRule(
    val name: String? = null,
    val version: String? = null,
    val arch: String? = null
)
