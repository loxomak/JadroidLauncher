package com.jadroid.launcher.data.auth

import com.jadroid.launcher.core.HttpClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/**
 * Microsoft OAuth 2.0 **device code** flow.
 *
 * The user opens `https://microsoft.com/link` (or the returned verification uri), types the short
 * code shown by Jadroid and approves the request. This is the flow recommended for apps that cannot
 * embed a browser, and it is the flow used by the official Minecraft launcher ecosystem.
 */
class MicrosoftAuthService(private val http: HttpClient) {

    suspend fun requestDeviceCode(clientId: String): DeviceCodeResponse {
        val body = http.postForm(
            "$LOGIN_BASE/devicecode",
            mapOf(
                "client_id" to clientId,
                "scope" to SCOPE
            )
        )
        val json = com.jadroid.launcher.core.JadroidJson.parseToJsonElement(body).jsonObjectOrThrow()
        return DeviceCodeResponse(
            deviceCode = json["device_code"]?.jsonPrimitive?.content
                ?: throw AuthException("Microsoft did not return a device code"),
            userCode = json["user_code"]?.jsonPrimitive?.content.orEmpty(),
            verificationUri = json["verification_uri"]?.jsonPrimitive?.content ?: DEFAULT_LINK,
            message = json["message"]?.jsonPrimitive?.content.orEmpty(),
            intervalSeconds = json["interval"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5,
            expiresInSeconds = json["expires_in"]?.jsonPrimitive?.content?.toIntOrNull() ?: 900
        )
    }

    /** Polls the token endpoint. Returns [PollStatus.PENDING] while the user has not approved yet. */
    suspend fun pollForToken(clientId: String, deviceCode: String): PollResult {
        val body = runCatching {
            http.postForm(
                "$LOGIN_BASE/token",
                mapOf(
                    "client_id" to clientId,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                    "device_code" to deviceCode
                )
            )
        }.getOrElse { throwable ->
            return PollResult(PollStatus.ERROR, message = throwable.message)
        }

        val json = runCatching { com.jadroid.launcher.core.JadroidJson.parseToJsonElement(body) as? JsonObject }
            .getOrNull() ?: return PollResult(PollStatus.ERROR, message = "Malformed Microsoft response")

        json["error"]?.jsonPrimitive?.content?.let { error ->
            return when (error) {
                "authorization_pending" -> PollResult(PollStatus.PENDING)
                "slow_down" -> PollResult(PollStatus.SLOW_DOWN)
                "expired_token" -> PollResult(PollStatus.EXPIRED, message = error)
                "authorization_declined", "access_denied" -> PollResult(PollStatus.DECLINED, message = error)
                else -> PollResult(
                    PollStatus.ERROR,
                    message = json["error_description"]?.jsonPrimitive?.content ?: error
                )
            }
        }

        val accessToken = json["access_token"]?.jsonPrimitive?.content
            ?: return PollResult(PollStatus.ERROR, message = "No access token in response")
        return PollResult(
            status = PollStatus.SUCCESS,
            accessToken = accessToken,
            refreshToken = json["refresh_token"]?.jsonPrimitive?.content,
            expiresInSeconds = json["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
        )
    }

    /** Uses a stored refresh token to get a fresh access token without user interaction. */
    suspend fun refresh(clientId: String, refreshToken: String): PollResult {
        val body = runCatching {
            http.postForm(
                "$LOGIN_BASE/token",
                mapOf(
                    "client_id" to clientId,
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken,
                    "scope" to SCOPE
                )
            )
        }.getOrElse { return PollResult(PollStatus.ERROR, message = it.message) }

        val json = runCatching { com.jadroid.launcher.core.JadroidJson.parseToJsonElement(body) as? JsonObject }
            .getOrNull() ?: return PollResult(PollStatus.ERROR, message = "Malformed Microsoft response")

        val accessToken = json["access_token"]?.jsonPrimitive?.content
            ?: return PollResult(
                PollStatus.ERROR,
                message = json["error_description"]?.jsonPrimitive?.content
                    ?: "Refresh token rejected, sign in again"
            )
        return PollResult(
            status = PollStatus.SUCCESS,
            accessToken = accessToken,
            refreshToken = json["refresh_token"]?.jsonPrimitive?.content ?: refreshToken,
            expiresInSeconds = json["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
        )
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrThrow(): JsonObject =
        this as? JsonObject ?: throw AuthException("Unexpected Microsoft response")

    companion object {
        const val LOGIN_BASE = "https://login.microsoftonline.com/consumers/oauth2/v2.0"
        const val SCOPE = "XboxLive.signin offline_access"
        const val DEFAULT_LINK = "https://microsoft.com/link"
    }
}
