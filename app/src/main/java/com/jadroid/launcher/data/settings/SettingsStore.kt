package com.jadroid.launcher.data.settings

import android.content.Context
import com.jadroid.launcher.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.name == id } ?: SYSTEM
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val defaultRamMb: Int = 2048,
    val defaultJvmArgs: String = "",
    val javaPath: String = "",
    val downloadConcurrency: Int = 4,
    val showSnapshots: Boolean = false,
    val verifyBeforeLaunch: Boolean = true,
    val msClientId: String = BuildConfig.MS_CLIENT_ID,
    val autoRefreshTokens: Boolean = true
)

/** Simple SharedPreferences backed settings store exposed as a [StateFlow] for Compose. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readAll())
    val settings: StateFlow<AppSettings> = _settings

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(KEY_THEME, next.themeMode.name)
            .putBoolean(KEY_DYNAMIC, next.dynamicColor)
            .putInt(KEY_RAM, next.defaultRamMb)
            .putString(KEY_JVM_ARGS, next.defaultJvmArgs)
            .putString(KEY_JAVA_PATH, next.javaPath)
            .putInt(KEY_CONCURRENCY, next.downloadConcurrency)
            .putBoolean(KEY_SNAPSHOTS, next.showSnapshots)
            .putBoolean(KEY_VERIFY, next.verifyBeforeLaunch)
            .putString(KEY_CLIENT_ID, next.msClientId)
            .putBoolean(KEY_AUTO_REFRESH, next.autoRefreshTokens)
            .apply()
        _settings.value = next
    }

    private fun readAll(): AppSettings = AppSettings(
        themeMode = ThemeMode.fromId(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, false),
        defaultRamMb = prefs.getInt(KEY_RAM, 2048),
        defaultJvmArgs = prefs.getString(KEY_JVM_ARGS, "").orEmpty(),
        javaPath = prefs.getString(KEY_JAVA_PATH, "").orEmpty(),
        downloadConcurrency = prefs.getInt(KEY_CONCURRENCY, 4).coerceIn(1, 16),
        showSnapshots = prefs.getBoolean(KEY_SNAPSHOTS, false),
        verifyBeforeLaunch = prefs.getBoolean(KEY_VERIFY, true),
        msClientId = prefs.getString(KEY_CLIENT_ID, BuildConfig.MS_CLIENT_ID)
            ?.takeIf { it.isNotBlank() } ?: BuildConfig.MS_CLIENT_ID,
        autoRefreshTokens = prefs.getBoolean(KEY_AUTO_REFRESH, true)
    )

    companion object {
        private const val PREFS = "jadroid_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_RAM = "default_ram"
        private const val KEY_JVM_ARGS = "default_jvm_args"
        private const val KEY_JAVA_PATH = "java_path"
        private const val KEY_CONCURRENCY = "concurrency"
        private const val KEY_SNAPSHOTS = "show_snapshots"
        private const val KEY_VERIFY = "verify_before_launch"
        private const val KEY_CLIENT_ID = "ms_client_id"
        private const val KEY_AUTO_REFRESH = "auto_refresh_tokens"
    }
}
