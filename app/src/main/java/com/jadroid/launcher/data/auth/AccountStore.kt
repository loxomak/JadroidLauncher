package com.jadroid.launcher.data.auth

import com.jadroid.launcher.core.AppLog
import com.jadroid.launcher.core.JadroidJson
import com.jadroid.launcher.core.LauncherPaths
import com.jadroid.launcher.core.ensureDir
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

@Serializable
private data class AccountFile(
    val accounts: List<Account> = emptyList(),
    val activeAccountId: String? = null
)

/**
 * Persists accounts in the app private storage (`minecraft/accounts.json`).
 * Tokens never leave the device; they are only sent to Microsoft / Xbox Live / Mojang endpoints.
 */
class AccountStore(private val paths: LauncherPaths) {

    private val file: File = File(paths.root, "accounts.json")

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts

    private val _activeAccount = MutableStateFlow<Account?>(null)
    val activeAccount: StateFlow<Account?> = _activeAccount

    private var activeAccountId: String? = null

    @Synchronized
    fun load() {
        paths.root.ensureDir()
        val parsed = if (file.isFile) {
            runCatching { JadroidJson.decodeFromString<AccountFile>(file.readText()) }.getOrNull()
        } else {
            null
        }
        _accounts.value = parsed?.accounts.orEmpty()
        activeAccountId = parsed?.activeAccountId
        refreshActive()
        AppLog.i("Loaded ${_accounts.value.size} account(s) from storage")
    }

    @Synchronized
    fun upsert(account: Account) {
        val next = _accounts.value.filterNot { it.id == account.id } + account
        _accounts.value = next.sortedWith(compareBy({ it.isLocal }, { it.username.lowercase() }))
        if (activeAccountId == null) activeAccountId = account.id
        persist()
        refreshActive()
    }

    @Synchronized
    fun remove(accountId: String) {
        _accounts.value = _accounts.value.filterNot { it.id == accountId }
        if (activeAccountId == accountId) activeAccountId = _accounts.value.firstOrNull()?.id
        persist()
        refreshActive()
    }

    @Synchronized
    fun setActive(accountId: String?) {
        activeAccountId = accountId
        persist()
        refreshActive()
    }

    @Synchronized
    fun clear() {
        _accounts.value = emptyList()
        activeAccountId = null
        persist()
        refreshActive()
    }

    private fun refreshActive() {
        _activeAccount.value = _accounts.value.firstOrNull { it.id == activeAccountId }
            ?: _accounts.value.firstOrNull()
    }

    private fun persist() {
        runCatching {
            paths.root.ensureDir()
            file.writeText(
                JadroidJson.encodeToString(AccountFile(accounts = _accounts.value, activeAccountId = activeAccountId))
            )
        }.onFailure { AppLog.w("Could not persist accounts", it) }
    }
}
