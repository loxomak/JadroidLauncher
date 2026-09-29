package com.jadroid.launcher.data.auth

import com.jadroid.launcher.core.AppLog
import com.jadroid.launcher.core.HttpClient
import com.jadroid.launcher.core.JadroidJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class XboxAuthResult(
    val token: String,
    val userHash: String,
    val xuid: String?
)

/**
 * Xbox Live authentication, the mandatory hop between a Microsoft account and Minecraft services.
 * See https://wiki.vg/Microsoft_Authentication_Scheme
 */
class XboxLiveService(private val http: HttpClient) {

    suspend fun authenticateXboxLive(msAccessToken: String): XboxAuthResult {
        val payload = """
            {"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com",
            "RpsTicket":"d=$msAccessToken"},
            "RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}
        """.trimIndent().replace("\n", "")
        val body = http.postJson(
            "$XBL_BASE/user/authenticate",
            payload,
            mapOf("Accept" to "application/json", "x-xbl-contract-version" to "1")
        )
        return parse(body, "Xbox Live sign-in failed")
    }

    suspend fun authorizeXsts(xblToken: String, relyingParty: String = MINECRAFT_RELYING_PARTY): XboxAuthResult {
        val payload = """
            {"Properties":{"SandboxId":"RETAIL","UserTokens":["$xblToken"]},
            "RelyingParty":"$relyingParty","TokenType":"JWT"}
        """.trimIndent().replace("\n", "")
        val body = http.postJson(
            "$XSTS_BASE/xsts/authorize",
            payload,
            mapOf("Accept" to "application/json", "x-xbl-contract-version" to "1")
        )
        val json = runCatching { JadroidJson.parseToJsonElement(body).jsonObject }.getOrNull()
        json?.get("XErr")?.jsonPrimitive?.longOrNull?.let { xerr ->
            throw AuthException(xstsErrorMessage(xerr), code = "XERR_$xerr")
        }
        return parse(body, "XSTS authorization failed")
    }

    private fun parse(body: String, fallbackMessage: String): XboxAuthResult {
        val json = runCatching { JadroidJson.parseToJsonElement(body).jsonObject }
            .getOrElse { throw AuthException(fallbackMessage, cause = it) }
        val token = json["Token"]?.jsonPrimitive?.content
            ?: run {
                AppLog.w("Unexpected Xbox response: $body")
                throw AuthException(fallbackMessage)
            }
        val claim = json["DisplayClaims"]?.jsonObject?.get("xui")?.jsonArray?.firstOrNull()?.jsonObject
        return XboxAuthResult(
            token = token,
            userHash = claim?.get("uhs")?.jsonPrimitive?.content.orEmpty(),
            xuid = claim?.get("xid")?.jsonPrimitive?.content
        )
    }

    private fun xstsErrorMessage(xerr: Long): String = when (xerr) {
        2148916227L -> "This Microsoft account is banned from Xbox Live."
        2148916229L -> "This Microsoft account has been banned from Xbox Live (child account)."
        2148916233L ->
            "This Microsoft account has no Xbox Live profile. Create one for free at xbox.com, then try again."
        2148916235L -> "Xbox Live is not available in this region for this account."
        2148916236L -> "This account needs to be verified for an adult at xbox.com (only required in some regions)."
        2148916237L -> "This account needs to be verified for an adult at xbox.com (only required in some regions)."
        2148916238L ->
            "This is a child account. An adult from the Microsoft family group must add it to a group first."
        2148916253L -> "Xbox Live sign-in was blocked for this account. Check the account's privacy settings."
        else -> "Xbox Live authorization failed (error $xerr)."
    }

    companion object {
        const val XBL_BASE = "https://user.auth.xboxlive.com"
        const val XSTS_BASE = "https://xsts.auth.xboxlive.com"
        const val MINECRAFT_RELYING_PARTY = "rp://api.minecraftservices.com/"
    }
}
