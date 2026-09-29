package com.jadroid.launcher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jadroid.launcher.core.AppLog
import com.jadroid.launcher.data.instance.Instance
import com.jadroid.launcher.data.instance.InstanceRepository
import com.jadroid.launcher.data.instance.LoaderInfo
import com.jadroid.launcher.data.instance.LoaderType
import com.jadroid.launcher.data.mods.InstalledMod
import com.jadroid.launcher.data.mojang.FabricLoaderVersion
import com.jadroid.launcher.data.mojang.LaunchFeatures
import com.jadroid.launcher.data.mojang.ManifestVersion
import com.jadroid.launcher.data.mojang.OsProfile
import com.jadroid.launcher.di.AppContainer
import com.jadroid.launcher.launch.GamePhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException

data class TaskState(
    val message: String,
    val fraction: Float? = null,
    val error: String? = null
)

class InstancesViewModel(private val container: AppContainer) : ViewModel() {

    val instances: StateFlow<List<Instance>> = container.instances.instances
    val gamePhase: StateFlow<GamePhase> = container.gameService.phase
    val gameLogs: StateFlow<List<String>> = container.gameService.logs
    val appLogs: StateFlow<List<String>> = AppLog.lines

    private val _tasks = MutableStateFlow<Map<String, TaskState>>(emptyMap())
    val tasks: StateFlow<Map<String, TaskState>> = _tasks

    private val _mods = MutableStateFlow<Map<String, List<InstalledMod>>>(emptyMap())
    val mods: StateFlow<Map<String, List<InstalledMod>>> = _mods

    private val _loaderVersions = MutableStateFlow<List<FabricLoaderVersion>>(emptyList())
    val loaderVersions: StateFlow<List<FabricLoaderVersion>> = _loaderVersions

    private val jobs = mutableMapOf<String, Job>()

    fun instance(id: String?): Instance? = id?.let { container.instances.get(it) }

    /** Creates the launcher profile. Files are fetched by [install] from the Mojang repositories. */
    fun createInstance(
        version: ManifestVersion,
        name: String,
        loaderType: LoaderType,
        loaderVersion: String,
        ramMb: Int
    ): Instance {
        val settings = container.settings.settings.value
        val id = InstanceRepository.newId(version.id)
        val instance = Instance(
            id = id,
            name = name.ifBlank { version.id },
            gameVersion = version.id,
            loader = LoaderInfo(loaderType, if (loaderType == LoaderType.VANILLA) "" else loaderVersion),
            versionUrl = version.url,
            allocatedRamMb = ramMb.coerceAtLeast(512),
            jvmArgs = settings.defaultJvmArgs
        )
        container.instances.createDirectories(id)
        container.instances.save(instance)
        AppLog.i("Created instance ${instance.name} (${instance.loaderLabel})")
        return instance
    }

    fun updateInstance(instance: Instance) {
        container.instances.save(instance)
    }

    fun deleteInstance(instance: Instance) {
        jobs.remove(instance.id)?.cancel()
        container.instances.delete(instance.id)
        _tasks.value = _tasks.value - instance.id
        _mods.value = _mods.value - instance.id
    }

    /** Downloads client, libraries, natives and assets from the official Mojang repositories. */
    fun install(instance: Instance) {
        if (jobs[instance.id]?.isActive == true) return
        jobs[instance.id] = viewModelScope.launch {
            setTask(instance.id, TaskState("Resolving ${instance.loaderLabel} ..."))
            try {
                val settings = container.settings.settings.value
                val features = LaunchFeatures(hasCustomResolution = instance.customResolution)
                val plan = container.installer.plan(instance, features, OsProfile.ANDROID, includeAssets = true)
                setTask(
                    instance.id,
                    TaskState(
                        "Downloading ${plan.downloads.size} files " +
                            "(${plan.totalBytes / 1024 / 1024} MB)",
                        0f
                    )
                )
                val result = container.installer.install(
                    plan = plan,
                    concurrency = settings.downloadConcurrency,
                    onStage = { stage -> setTask(instance.id, TaskState(stage)) },
                    onProgress = { progress ->
                        setTask(
                            instance.id,
                            TaskState(
                                "${progress.completed}/${progress.total} · ${progress.currentLabel}",
                                progress.fraction
                            )
                        )
                    }
                )
                container.instances.save(instance.copy(installed = true, resolvedVersionId = result.versionId))
                val failures = result.summary.failures.size
                val firstFailure = result.summary.failures.firstOrNull()
                setTask(
                    instance.id,
                    TaskState(
                        if (failures == 0) {
                            "Ready · ${result.nativesExtracted} native libraries extracted"
                        } else {
                            "Finished with $failures failed download(s)"
                        },
                        1f,
                        firstFailure?.let { "${it.item.label}: ${it.error}" }
                    )
                )
                refreshMods(instance.id)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("Install failed for ${instance.name}", t)
                setTask(instance.id, TaskState("Install failed", null, t.message ?: "Unknown error"))
            }
        }
    }

    fun launch(instance: Instance) {
        val account = container.auth.activeAccount.value
        if (account == null) {
            setTask(instance.id, TaskState("No account selected", null, "Add or select an account first."))
            return
        }
        jobs[instance.id] = viewModelScope.launch {
            container.gameService.launch(instance, account)
        }
    }

    fun stopGame() = container.gameService.stop()

    fun clearTask(instanceId: String) {
        _tasks.value = _tasks.value - instanceId
    }

    fun cancelTask(instanceId: String) {
        jobs[instanceId]?.cancel()
        clearTask(instanceId)
    }

    fun loadFabricLoaders(gameVersion: String) {
        viewModelScope.launch {
            _loaderVersions.value = runCatching { container.fabric.loaderVersions(gameVersion) }
                .getOrElse {
                    AppLog.w("Could not load Fabric loader versions", it)
                    emptyList()
                }
        }
    }

    fun refreshMods(instanceId: String) {
        _mods.value = _mods.value + (instanceId to container.mods.list(instanceId))
    }

    fun toggleMod(instanceId: String, mod: InstalledMod) {
        container.mods.toggle(mod)
        refreshMods(instanceId)
    }

    fun deleteMod(instanceId: String, mod: InstalledMod) {
        container.mods.delete(mod)
        refreshMods(instanceId)
    }

    fun importMod(instanceId: String, filename: String, openStream: () -> InputStream) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { container.mods.importStream(instanceId, filename, openStream()) }
            }
            _tasks.value = _tasks.value + (
                instanceId to result.fold(
                    onSuccess = { TaskState("Imported ${it.name}", 1f) },
                    onFailure = { TaskState("Import failed", null, it.message) }
                )
                )
            refreshMods(instanceId)
        }
    }

    private fun setTask(instanceId: String, task: TaskState) {
        _tasks.value = _tasks.value + (instanceId to task)
    }
}
