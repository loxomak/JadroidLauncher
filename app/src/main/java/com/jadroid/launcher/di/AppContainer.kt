package com.jadroid.launcher.di

import android.content.Context
import com.jadroid.launcher.BuildConfig
import com.jadroid.launcher.core.AppLog
import com.jadroid.launcher.core.HttpClient
import com.jadroid.launcher.core.LauncherPaths
import com.jadroid.launcher.data.auth.AccountStore
import com.jadroid.launcher.data.auth.AuthRepository
import com.jadroid.launcher.data.auth.MicrosoftAuthService
import com.jadroid.launcher.data.auth.MinecraftAuthService
import com.jadroid.launcher.data.auth.XboxLiveService
import com.jadroid.launcher.data.download.DownloadManager
import com.jadroid.launcher.data.instance.InstanceRepository
import com.jadroid.launcher.data.minecraft.MinecraftInstaller
import com.jadroid.launcher.data.minecraft.VersionResolver
import com.jadroid.launcher.data.mods.ModManager
import com.jadroid.launcher.data.mods.ModrinthRepository
import com.jadroid.launcher.data.mojang.FabricRepository
import com.jadroid.launcher.data.mojang.MojangRepository
import com.jadroid.launcher.data.settings.SettingsStore
import com.jadroid.launcher.launch.GameService
import com.jadroid.launcher.launch.JavaRuntimeManager
import com.jadroid.launcher.launch.LaunchCommandBuilder

/**
 * Hand rolled dependency container: the graph is small enough that a DI framework would only add
 * build time and indirection.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val http = HttpClient("Jadroid/${BuildConfig.VERSION_NAME} (Minecraft launcher for Android)")
    val paths = LauncherPaths(appContext)
    val settings = SettingsStore(appContext)

    val downloads = DownloadManager(http)

    // Official Mojang repositories
    val mojang = MojangRepository(http)
    val fabric = FabricRepository(http)
    val modrinth = ModrinthRepository(http)

    val resolver = VersionResolver(mojang, fabric)
    val installer = MinecraftInstaller(mojang, resolver, paths, downloads)
    val instances = InstanceRepository(paths)
    val mods = ModManager(paths, downloads)

    val accountStore = AccountStore(paths)
    val auth = AuthRepository(
        store = accountStore,
        microsoft = MicrosoftAuthService(http),
        xbox = XboxLiveService(http),
        minecraft = MinecraftAuthService(http),
        clientIdProvider = { settings.settings.value.msClientId }
    )

    val javaRuntimes = JavaRuntimeManager(paths)
    val launchCommandBuilder = LaunchCommandBuilder(paths)
    val gameService = GameService(
        paths = paths,
        installer = installer,
        instances = instances,
        auth = auth,
        settings = settings,
        commandBuilder = launchCommandBuilder,
        javaRuntimes = javaRuntimes
    )

    fun initialize() {
        paths.ensureBaseDirs()
        instances.reload()
        auth.load()
        AppLog.i("Jadroid ${BuildConfig.VERSION_NAME} initialised (${paths.root.absolutePath})")
    }
}
