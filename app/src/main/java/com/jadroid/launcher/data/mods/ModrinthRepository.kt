package com.jadroid.launcher.data.mods

import com.jadroid.launcher.core.HttpClient
import com.jadroid.launcher.core.JadroidJson
import kotlinx.serialization.decodeFromString
import java.net.URLEncoder

/**
 * Modrinth is the community mod repository used by every modern launcher (it is the successor of
 * CurseForge for Fabric/Quilt/Forge mods). Mod **files** are downloaded straight from the URLs it
 * publishes, while the game itself always comes from the official Mojang repositories.
 */
class ModrinthRepository(private val http: HttpClient) {

    /**
     * @param gameVersion when set, only mods with a build for that Minecraft version are returned.
     * @param loader      "fabric", "quilt", "forge", "neoforge".
     */
    suspend fun search(
        query: String,
        gameVersion: String? = null,
        loader: String? = null,
        projectType: String = "mod",
        offset: Int = 0,
        limit: Int = 20
    ): ModrinthSearchResponse {
        val facets = mutableListOf<List<String>>()
        facets.add(listOf("project_type:$projectType"))
        if (!gameVersion.isNullOrBlank()) facets.add(listOf("versions:$gameVersion"))
        if (!loader.isNullOrBlank()) facets.add(listOf("categories:$loader"))

        val url = buildString {
            append("$API/search?query=").append(encode(query))
            append("&limit=").append(limit.coerceIn(1, 50))
            append("&offset=").append(offset.coerceAtLeast(0))
            append("&index=").append(if (query.isBlank()) "downloads" else "relevance")
            append("&facets=").append(
                encode(
                    facets.joinToString(",") { facet -> "[" + facet.joinToString(",") { "\"$it\"" } + "]" }
                )
            )
        }
        val body = http.getText(url, mapOf("Accept" to "application/json"))
        return runCatching { JadroidJson.decodeFromString<ModrinthSearchResponse>(body) }
            .getOrElse { throw IllegalStateException("Could not read the Modrinth search result", it) }
    }

    suspend fun versions(
        projectIdOrSlug: String,
        gameVersion: String? = null,
        loader: String? = null
    ): List<ModrinthVersion> {
        val url = buildString {
            append("$API/project/").append(encode(projectIdOrSlug)).append("/version")
            val params = mutableListOf<String>()
            if (!gameVersion.isNullOrBlank()) params += "game_versions=" + encode("[\"$gameVersion\"]")
            if (!loader.isNullOrBlank()) params += "loaders=" + encode("[\"$loader\"]")
            if (params.isNotEmpty()) append('?').append(params.joinToString("&"))
        }
        val body = http.getText(url, mapOf("Accept" to "application/json"))
        return runCatching { JadroidJson.decodeFromString<List<ModrinthVersion>>(body) }
            .getOrElse { throw IllegalStateException("Could not read the Modrinth versions", it) }
    }

    /** Best matching version for a game version + loader, preferring stable releases. */
    suspend fun bestVersion(
        projectIdOrSlug: String,
        gameVersion: String,
        loader: String
    ): ModrinthVersion? {
        val all = versions(projectIdOrSlug, gameVersion, loader)
        if (all.isEmpty()) return null
        return all.sortedByDescending { it.datePublished }
            .firstOrNull { it.versionType == "release" }
            ?: all.first()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        /** https://docs.modrinth.com/api/ – Jadroid sends a descriptive User-Agent via HttpClient. */
        const val API = "https://api.modrinth.com/v2"
    }
}
