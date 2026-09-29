package com.jadroid.launcher.data.mods

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModrinthSearchResponse(
    val hits: List<ModrinthHit> = emptyList(),
    val offset: Int = 0,
    val limit: Int = 0,
    @SerialName("total_hits") val totalHits: Int = 0
)

@Serializable
data class ModrinthHit(
    @SerialName("project_id") val projectId: String = "",
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    val author: String = "",
    val downloads: Long = 0,
    val follows: Long = 0,
    @SerialName("icon_url") val iconUrl: String? = null,
    val categories: List<String> = emptyList(),
    val versions: List<String> = emptyList(),
    @SerialName("project_type") val projectType: String = "mod"
)

@Serializable
data class ModrinthVersion(
    val id: String = "",
    @SerialName("project_id") val projectId: String = "",
    val name: String = "",
    @SerialName("version_number") val versionNumber: String = "",
    @SerialName("game_versions") val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    @SerialName("version_type") val versionType: String = "release",
    @SerialName("date_published") val datePublished: String = "",
    val downloads: Long = 0,
    val files: List<ModrinthFile> = emptyList(),
    val dependencies: List<ModrinthDependency> = emptyList()
) {
    /** The jar that should be dropped into the `mods` folder. */
    val primaryFile: ModrinthFile?
        get() = files.firstOrNull { it.primary } ?: files.firstOrNull()
}

@Serializable
data class ModrinthFile(
    val url: String = "",
    val filename: String = "",
    val primary: Boolean = false,
    val size: Long = 0,
    val hashes: ModrinthHashes = ModrinthHashes()
)

@Serializable
data class ModrinthHashes(
    val sha1: String? = null,
    val sha512: String? = null
)

@Serializable
data class ModrinthDependency(
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("version_id") val versionId: String? = null,
    @SerialName("dependency_type") val dependencyType: String = ""
)
