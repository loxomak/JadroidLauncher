package com.jadroid.launcher.data.mojang

import com.jadroid.launcher.core.AppLog
import com.jadroid.launcher.core.HttpClient
import com.jadroid.launcher.core.JadroidJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

/**
 * Fabric loader metadata. The `profile/json` endpoint returns a complete version json that
 * `inheritsFrom` the vanilla version we resolved from the official Mojang repository.
 */
class FabricRepository(private val http: HttpClient) {

    suspend fun loaderVersions(gameVersion: String): List<FabricLoaderVersion> {
        val json = http.getText("$META/versions/loader/$gameVersion")
        return JadroidJson.decodeFromString<List<FabricLoaderEntry>>(json).map { it.loader }
    }

    suspend fun profile(gameVersion: String, loaderVersion: String): VersionJson {
        AppLog.i("Fetching Fabric profile $gameVersion / $loaderVersion")
        val json = http.getText("$META/versions/loader/$gameVersion/$loaderVersion/profile/json")
        return JadroidJson.decodeFromString<VersionJson>(json)
    }

    companion object {
        const val META = "https://meta.fabricmc.net/v2"
    }
}

@Serializable
data class FabricLoaderEntry(
    val loader: FabricLoaderVersion = FabricLoaderVersion(),
    val intermediary: FabricIntermediary = FabricIntermediary(),
    @SerialName("launcherMeta") val launcherMeta: FabricLauncherMeta? = null
)

@Serializable
data class FabricLoaderVersion(
    val separator: String = "",
    val build: Int = 0,
    val maven: String = "",
    val version: String = "",
    val stable: Boolean = false
)

@Serializable
data class FabricIntermediary(
    val maven: String = "",
    val version: String = "",
    val stable: Boolean = false
)

@Serializable
data class FabricLauncherMeta(
    val libraries: Map<String, List<FabricLibrary>> = emptyMap(),
    @SerialName("mainClass") val mainClass: Map<String, String> = emptyMap()
)

@Serializable
data class FabricLibrary(
    val name: String = "",
    val url: String? = null,
    val sha1: String? = null,
    val size: Long? = null
)
