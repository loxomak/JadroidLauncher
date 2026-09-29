package com.jadroid.launcher.data.auth

import com.jadroid.launcher.core.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.util.UUID

/**
 * High level account API:
 *  - sign in with a **Microsoft account** (device code flow -> Xbox Live -> Minecraft services),
 *  - keep the Minecraft token fresh on every launch,
 *  - create **local (offline) accounts, which become available once a Microsoft account is connected**.
 */
class AuthRepository(
    private val store: AccountStore,
    private val microsoft: MicrosoftAuthService,
    private val xbox: XboxLiveService,
    private val minecraft: MinecraftAuthService,
    private val clientIdProvider: () -> String
) {

    val accounts: StateFlow<List<Account>> = store.accounts
    val activeAccount: StateFlow<Account?> = store.activeAccount

    fun load() = store.load()

    // ---------------------------------------------------------------- Microsoft

    suspend fun beginMicrosoftLogin(): DeviceCodeResponse =
        microsoft.requestDeviceCode(clientIdProvider())

    /**
     * Polls until the user approved the code, then walks the whole Xbox Live / Minecraft chain.
     * [onStatus] receives human readable progress for the sign-in dialog.
     */
    suspend fun completeMicrosoftLogin(
        deviceCode: DeviceCodeResponse,
        onStatus: (String) -> Unit
    ): Account {
        val clientId = clientIdProvider()
        val deadline = System.currentTimeMillis() + deviceCode.expiresInSeconds * 1000L
        var interval = deviceCode.intervalSeconds.coerceAtLeast(1) * 1000L
        var msAccessToken: String? = null
        var msRefreshToken: String? = null

        onStatus("Waiting for approval on microsoft.com/link ...")
        while (msAccessToken == null) {
            if (System.currentTimeMillis() >= deadline) {
                throw AuthException("The code expired before it was approved. Try again.")
            }
            val result = microsoft.pollForToken(clientId, deviceCode.deviceCode)
            when (result.status) {
                PollStatus.SUCCESS -> {
                    msAccessToken = result.accessToken
                    msRefreshToken = result.refreshToken
                }
                PollStatus.PENDING -> delay(interval)
                PollStatus.SLOW_DOWN -> {
                    interval += 2000L
                    delay(interval)
                }
                PollStatus.EXPIRED -> throw AuthException("The code expired before it was approved. Try again.")
                PollStatus.DECLINED -> throw AuthException("Sign-in was declined on the Microsoft page.")
                PollStatus.ERROR -> throw AuthException(result.message ?: "Microsoft sign-in failed")
            }
        }

        onStatus("Signing in to Xbox Live ...")
        val xbl = xbox.authenticateXboxLive(msAccessToken!!)

        onStatus("Authorizing with XSTS ...")
        val xsts = xbox.authorizeXsts(xbl.token)

        onStatus("Connecting to Minecraft services ...")
        val mcToken = minecraft.loginWithXbox(xsts.userHash.ifEmpty { xbl.userHash }, xsts.token)

        onStatus("Loading your Minecraft profile ...")
        val profile = minecraft.profile(mcToken.accessToken)

        val account = Account(
            id = "ms:" + profile.id.ifEmpty { UUID.randomUUID().toString() },
            type = AccountType.MICROSOFT,
            username = profile.name,
            uuid = formatUuid(profile.id),
            accessToken = mcToken.accessToken,
            refreshToken = msRefreshToken,
            expiresAt = System.currentTimeMillis() + mcToken.expiresIn * 1000L,
            xuid = xsts.xuid ?: xbl.xuid,
            skins = profile.skins.map { SkinInfo(it.id, it.state, it.url, it.variant) }
        )
        store.upsert(account)
        store.setActive(account.id)
        AppLog.i("Microsoft account connected: ${account.username}")
        return account
    }

    /** Refreshes the Minecraft token through the stored Microsoft refresh token. */
    suspend fun refreshAccount(account: Account): Account {
        val refreshToken = account.refreshToken
            ?: throw AuthException("This account has no refresh token, sign in again.", code = "NO_REFRESH")
        val ms = microsoft.refresh(clientIdProvider(), refreshToken)
        if (ms.status != PollStatus.SUCCESS || ms.accessToken == null) {
            throw AuthException(ms.message ?: "Could not refresh the Microsoft token", code = "REFRESH_FAILED")
        }
        val xbl = xbox.authenticateXboxLive(ms.accessToken)
        val xsts = xbox.authorizeXsts(xbl.token)
        val mcToken = minecraft.loginWithXbox(xsts.userHash.ifEmpty { xbl.userHash }, xsts.token)
        val refreshed = account.copy(
            accessToken = mcToken.accessToken,
            refreshToken = ms.refreshToken ?: refreshToken,
            expiresAt = System.currentTimeMillis() + mcToken.expiresIn * 1000L
        )
        store.upsert(refreshed)
        return refreshed
    }

    /** Returns an account with a usable Minecraft access token, refreshing it when needed. */
    suspend fun accountWithValidToken(account: Account): Account =
        if (account.isMicrosoft && account.tokenNeedsRefresh()) refreshAccount(account) else account

    // ---------------------------------------------------------------- local accounts

    /**
     * Local (offline) accounts can only be created while a real Minecraft account is connected,
     * which keeps Jadroid honest about which profiles can actually join online servers.
     */
    fun hasConnectedMinecraftAccount(): Boolean = store.accounts.value.any { it.isMicrosoft }

    fun createLocalAccount(username: String): Account {
        val name = username.trim()
        if (name.length < 3 || name.length > 16) {
            throw AuthException("Player names must be between 3 and 16 characters.")
        }
        if (!name.matches(Regex("^[A-Za-z0-9_]+$"))) {
            throw AuthException("Player names may only contain letters, numbers and underscore.")
        }
        if (!hasConnectedMinecraftAccount()) {
            throw AuthException(
                "Connect a Microsoft account first. Local accounts are only unlocked " +
                    "once a Minecraft account is linked.",
                code = "NO_MICROSOFT_ACCOUNT"
            )
        }
        if (store.accounts.value.any { it.username.equals(name, ignoreCase = true) }) {
            throw AuthException("An account named \"$name\" already exists.")
        }
        val uuid = offlineUuid(name)
        val account = Account(
            id = "local:$uuid",
            type = AccountType.LOCAL,
            username = name,
            uuid = uuid,
            accessToken = "0",
            expiresAt = Long.MAX_VALUE
        )
        store.upsert(account)
        AppLog.i("Local account created: $name ($uuid)")
        return account
    }

    fun removeAccount(accountId: String) = store.remove(accountId)

    fun setActiveAccount(accountId: String?) = store.setActive(accountId)

    companion object {
        /** Deterministic offline UUID, identical to what vanilla servers derive in offline mode. */
        fun offlineUuid(username: String): String {
            val digest = MessageDigest.getInstance("MD5").digest("OfflinePlayer:$username".toByteArray(Charsets.UTF_8))
            digest[6] = ((digest[6].toInt() and 0x0F) or 0x30).toByte()
            digest[8] = ((digest[8].toInt() and 0x3F) or 0x80).toByte()
            return formatUuid(digest)
        }

        private fun formatUuid(bytes: ByteArray): String {
            val hex = bytes.joinToString("") { "%02x".format(it) }
            return buildString {
                append(hex, 0, 8).append('-')
                append(hex, 8, 12).append('-')
                append(hex, 12, 16).append('-')
                append(hex, 16, 20).append('-')
                append(hex, 20, 32)
            }
        }

        /** Mojang profile ids are 32 hex chars; be tolerant of dashed input too. */
        fun formatUuid(raw: String): String {
            val compact = raw.replace("-", "").lowercase()
            if (compact.length != 32) return raw
            return buildString {
                append(compact, 0, 8).append('-')
                append(compact, 8, 12).append('-')
                append(compact, 12, 16).append('-')
                append(compact, 16, 20).append('-')
                append(compact, 20, 32)
            }
        }
    }
}
