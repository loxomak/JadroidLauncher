package com.jadroid.launcher.data.auth

import com.jadroid.launcher.core.AppLog
import com.jadroid.launcher.core.HttpClient
import com.jadroid.launcher.core.HttpException
import com.jadroid.launcher.core.JadroidJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Minecraft services: exchanges the XSTS token for a Minecraft access token and reads the profile.
 * https://wiki.vg/Microsoft_Authentication_Scheme
 */
class MinecraftAuthService(private val http: HttpClient) {

    suspend fun loginWithXbox(userHash: String, xstsToken: String): McTokenResponse {
        val payload = """{"identityToken":"XBL3.0 x=$userHash;$xstsToken"}"""
        val body = try {
            http.postJson(
                "$API_BASE/authentication/login_with_xbox",
                payload,
                mapOf("Accept" to "application/json")
            )
        } catch (e: HttpException) {
            throw AuthException("Minecraft login failed (HTTP ${e.code})", code = e.code.toString(), cause = e)
        }
        return runCatching { JadroidJson.decodeFromString<McTokenResponse>(body) }
            .getOrElse { throw AuthException("Could not read the Minecraft token response", cause = it) }
    }

    /** Throws [AuthException] with code `NOT_OWNED` when the account does not own Java Edition. */
    suspend fun profile(accessToken: String): MinecraftProfile {
        val body = try {
            http.getText(
                "$API_BASE/minecraft/profile",
                mapOf("Accept" to "application/json", "Authorization" to "Bearer $accessToken")
            )
        } catch (e: HttpException) {
            val detail = runCatching {
                JadroidJson.parseToJsonElement(e.payload).jsonObject["errorMessage"]?.jsonPrimitive?.content
            }.getOrNull()
            val message = when (e.code) {
                401 -> "Minecraft session expired or was rejected. Sign in again."
                403, 404 -> "This Microsoft account does not own Minecraft: Java Edition."
                else -> detail ?: "Could not load the Minecraft profile (HTTP ${e.code})"
            }
            throw AuthException(message, code = if (e.code == 403 || e.code == 404) "NOT_OWNED" else e.code.toString(), cause = e)
        }
        val profile = runCatching { JadroidJson.decodeFromString<MinecraftProfile>(body) }
            .getOrElse { throw AuthException("Could not read the Minecraft profile", cause = it) }
        AppLog.i("Signed in as ${profile.name} (${profile.id})")
        return profile
    }

    companion object {
        const val API_BASE = "https://api.minecraftservices.com"
    }
}

@Serializable
data class MinecraftProfile(
    val id: String = "",
    val name: String = "",
    val skins: List<SkinEntry> = emptyList(),
    val capes: List<SkinEntry> = emptyList()
)

@Serializable
data class SkinEntry(
    val id: String = "",
    val state: String = "",
    val url: String = "",
    val variant: String = "",
    @SerialName("alias") val aliasName: String? = null
)
