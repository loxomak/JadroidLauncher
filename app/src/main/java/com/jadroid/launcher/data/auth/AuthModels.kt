package com.jadroid.launcher.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AccountType {
    /** A real Minecraft account, authenticated through Microsoft / Xbox Live. */
    MICROSOFT,

    /** An offline account that can only join LAN servers; requires a linked Microsoft account. */
    LOCAL
}

@Serializable
data class SkinInfo(
    val id: String = "",
    val state: String = "",
    val url: String = "",
    val variant: String = ""
)

@Serializable
data class Account(
    val id: String,
    val type: AccountType,
    val username: String,
    val uuid: String,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    /** Epoch millis when [accessToken] stops working, 0 for local accounts. */
    val expiresAt: Long = 0L,
    val xuid: String? = null,
    val skins: List<SkinInfo> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val isMicrosoft: Boolean get() = type == AccountType.MICROSOFT
    val isLocal: Boolean get() = type == AccountType.LOCAL

    /** UUID without dashes, the format the game expects on the command line. */
    val compactUuid: String get() = uuid.replace("-", "")

    fun tokenNeedsRefresh(): Boolean = isMicrosoft && expiresAt <= System.currentTimeMillis() + REFRESH_MARGIN_MS

    companion object {
        private const val REFRESH_MARGIN_MS = 5 * 60 * 1000L
    }
}

/** Response of the Microsoft device-code endpoint. */
data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val message: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int
)

enum class PollStatus {
    PENDING,
    SLOW_DOWN,
    SUCCESS,
    EXPIRED,
    DECLINED,
    ERROR
}

data class PollResult(
    val status: PollStatus,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresInSeconds: Long = 0,
    val message: String? = null
)

@Serializable
data class McTokenResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("token_type") val tokenType: String? = null
)

class AuthException(message: String, val code: String? = null, cause: Throwable? = null) :
    Exception(message, cause)
