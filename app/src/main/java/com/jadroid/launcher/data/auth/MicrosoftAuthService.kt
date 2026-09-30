package com.jadroid.launcher.data.auth

import com.jadroid.launcher.core.AppLog
import com.jadroid.launcher.core.HttpClient
import com.jadroid.launcher.core.HttpException
import com.jadroid.launcher.core.JadroidJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The two Microsoft endpoint families, which are *not* interchangeable. */
enum class MicrosoftAuthEndpoint(val description: String) {
    /** `login.microsoftonline.com/consumers/oauth2/v2.0` – needs a registered Azure app id (GUID). */
    AZURE("Azure AD (login.microsoftonline.com)"),

    /** `login.live.com` – the Live SDK flow, accepts the legacy Minecraft application id. */
    LEGACY_LIVE("Live SDK (login.live.com)")
}

/**
 * Microsoft OAuth 2.0 **device code** login with automatic endpoint selection.
 *
 * * [MicrosoftAuthEndpoint.AZURE] exists since the Minecraft account migration. It only accepts an
 *   application **registered in Azure AD** (a GUID client id with "allow public client flows"
 *   enabled). Passing the legacy Minecraft id here returns `400 AADSTS700016` ("application not
 *   found"), which is the classic "login 400" people hit in custom launchers.
 * * [MicrosoftAuthEndpoint.LEGACY_LIVE] is the Live SDK flow: it accepts the legacy Minecraft
 *   application id `00000000402b5328` and hands out an MSA token that Xbox Live accepts as
 *   `d=<token>`, exactly like the classic launchers.
 *
 * The client id shape therefore decides the endpoint, so the built-in id works out of the box and a
 * self registered Azure app id is used through the modern flow.
 */
class MicrosoftAuthService(private val http: HttpClient) {

    suspend fun requestDeviceCode(clientId: String): DeviceCodeResponse {
        val endpoint = endpointFor(clientId)
        AppLog.i("Microsoft device-code login via ${endpoint.description}")
        val body = authPost(
            url = deviceCodeUrlFor(clientId),
            fields = when (endpoint) {
                MicrosoftAuthEndpoint.AZURE -> mapOf(
                    "client_id" to clientId,
                    "scope" to AZURE_SCOPE
                )
                MicrosoftAuthEndpoint.LEGACY_LIVE -> mapOf(
                    "client_id" to clientId,
                    "scope" to LEGACY_SCOPE,
                    "response_type" to "device_code"
                )
            },
            context = "device code request"
        )
        val json = parseObject(body)
            ?: throw AuthException("Microsoft did not return a device code: ${snippet(body)}")
        return DeviceCodeResponse(
            deviceCode = json["device_code"]?.jsonPrimitive?.content
                ?: throw AuthException("Microsoft did not return a device code: ${snippet(body)}"),
            userCode = json["user_code"]?.jsonPrimitive?.content.orEmpty(),
            verificationUri = json["verification_uri"]?.jsonPrimitive?.content
                ?: json["verification_url"]?.jsonPrimitive?.content
                ?: DEFAULT_LINK,
            message = json["message"]?.jsonPrimitive?.content.orEmpty(),
            intervalSeconds = json["interval"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5,
            expiresInSeconds = json["expires_in"]?.jsonPrimitive?.content?.toIntOrNull() ?: 900
        )
    }


    /** Polls the token endpoint. Returns [PollStatus.PENDING] while the user has not approved yet. */
    suspend fun pollForToken(clientId: String, deviceCode: String): PollResult {
        val body = try {
            http.postForm(
                tokenUrlFor(clientId),
                mapOf(
                    "client_id" to clientId,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                    "device_code" to deviceCode
                )
            )
        } catch (e: HttpException) {
            // Both endpoints answer "authorization_pending" with HTTP 400 while the user has not
            // approved the code yet, so the body has to be parsed out of the exception instead of
            // treating the status code as fatal.
            parseTokenPayload(e.payload)?.let { return it }
            AppLog.w("Microsoft token poll failed: ${e.message} · ${snippet(e.payload)}")
            return PollResult(PollStatus.ERROR, message = describeFailure(e))
        }
        return parseTokenPayload(body)
            ?: PollResult(PollStatus.ERROR, message = "Malformed Microsoft response: ${snippet(body)}")
    }

    /**
     * Parses a token endpoint payload: either an access token or one of the OAuth device-flow
     * states (`authorization_pending`, `slow_down`, `expired_token`, `access_denied`).
     */
    internal fun parseTokenPayload(body: String): PollResult? {
        val json = parseObject(body) ?: return null
        json["error"]?.jsonPrimitive?.content?.let { error ->
            when (error) {
                "authorization_pending" -> return PollResult(PollStatus.PENDING)
                "slow_down" -> return PollResult(PollStatus.SLOW_DOWN)
                "expired_token" ->
                    return PollResult(PollStatus.EXPIRED, message = "The device code expired, start again.")
                "authorization_declined", "access_denied" ->
                    return PollResult(PollStatus.DECLINED, message = "Sign-in was declined on the Microsoft page.")
            }
            return PollResult(
                PollStatus.ERROR,
                message = describeAuthError(error, json["error_description"]?.jsonPrimitive?.content)
            )
        }
        val accessToken = json["access_token"]?.jsonPrimitive?.content ?: return null
        return PollResult(
            status = PollStatus.SUCCESS,
            accessToken = accessToken,
            refreshToken = json["refresh_token"]?.jsonPrimitive?.content,
            expiresInSeconds = json["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
        )
    }

    /** Uses a stored refresh token to get a fresh access token without user interaction. */
    suspend fun refresh(clientId: String, refreshToken: String): PollResult {
        val body = try {
            http.postForm(
                tokenUrlFor(clientId),
                mapOf(
                    "client_id" to clientId,
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken
                )
            )
        } catch (e: HttpException) {
            parseTokenPayload(e.payload)?.let { return it }
            AppLog.w("Microsoft token refresh failed: ${e.message} · ${snippet(e.payload)}")
            return PollResult(PollStatus.ERROR, message = describeFailure(e))
        }
        val parsed = parseTokenPayload(body)
            ?: return PollResult(
                PollStatus.ERROR,
                message = "Malformed Microsoft refresh response: ${snippet(body)}"
            )
        return if (parsed.status == PollStatus.SUCCESS && parsed.refreshToken == null) {
            parsed.copy(refreshToken = refreshToken)
        } else {
            parsed
        }
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun authPost(url: String, fields: Map<String, String>, context: String): String =
        try {
            http.postForm(url, fields, mapOf("Accept" to "application/json"))
        } catch (e: HttpException) {
            AppLog.w("Microsoft $context failed: ${e.message} · ${snippet(e.payload)}")
            val json = parseObject(e.payload)
            val error = json?.get("error")?.jsonPrimitive?.content
            val description = json?.get("error_description")?.jsonPrimitive?.content
            throw AuthException(
                if (error != null || description != null) {
                    describeAuthError(error ?: "error", description)
                } else {
                    describeFailure(e)
                },
                code = error
            )
        }

    /** Turns an OAuth error into a message that explains what the user has to change. */
    internal fun describeAuthError(error: String, description: String?): String {
        val detail = description?.takeIf { it.isNotBlank() } ?: error
        val hint = when {
            detail.contains("AADSTS700016") || error == "unauthorized_client" || error == "invalid_client" ->
                "\n\nThis client id is not a registered Azure AD application. Register one in the Azure " +
                    "portal (public client, redirect URI not required) and paste its GUID in Settings, or " +
                    "keep the built-in legacy id so Jadroid signs in through login.live.com."
            detail.contains("AADSTS500011") ->
                "\n\nThe application is registered in a different tenant. Use an app id registered in " +
                    "the 'consumers' tenant."
            else -> ""
        }
        return "$detail$hint"
    }

    private fun describeFailure(e: HttpException): String =
        "Microsoft returned HTTP ${e.code}." + if (e.payload.isNotBlank()) " ${snippet(e.payload)}" else ""

    private fun parseObject(body: String): JsonObject? =
        runCatching { JadroidJson.parseToJsonElement(body) as? JsonObject }.getOrNull()

    /** Short, single line excerpt of a response body for error messages and logs. */
    internal fun snippet(body: String): String = body
        .replace(Regex("<[^>]*>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(300)

    fun endpointFor(clientId: String): MicrosoftAuthEndpoint =
        if (isAzureApplicationId(clientId)) {
            MicrosoftAuthEndpoint.AZURE
        } else {
            MicrosoftAuthEndpoint.LEGACY_LIVE
        }

    private fun deviceCodeUrlFor(clientId: String): String = when (endpointFor(clientId)) {
        MicrosoftAuthEndpoint.AZURE -> "$AZURE_BASE/devicecode"
        MicrosoftAuthEndpoint.LEGACY_LIVE -> LEGACY_CONNECT
    }

    private fun tokenUrlFor(clientId: String): String = when (endpointFor(clientId)) {
        MicrosoftAuthEndpoint.AZURE -> "$AZURE_BASE/token"
        MicrosoftAuthEndpoint.LEGACY_LIVE -> LEGACY_TOKEN
    }

    companion object {
        /** Azure AD v2 (consumers) endpoints – require a registered application id. */
        const val AZURE_BASE = "https://login.microsoftonline.com/consumers/oauth2/v2.0"
        const val AZURE_SCOPE = "XboxLive.signin offline_access"

        /** Legacy Live SDK endpoints – accept the legacy Minecraft application id. */
        const val LEGACY_CONNECT = "https://login.live.com/oauth20_connect.srf"
        const val LEGACY_TOKEN = "https://login.live.com/oauth20_token.srf"
        const val LEGACY_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"

        const val DEFAULT_LINK = "https://microsoft.com/link"

        private val AZURE_APP_ID = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )

        /**
         * Azure application ids are GUIDs; the legacy Minecraft id (`00000000402b5328`) is not, so the
         * client id shape tells us which endpoint family can serve it.
         */
        fun isAzureApplicationId(clientId: String): Boolean = AZURE_APP_ID.matches(clientId.trim())
    }
}
