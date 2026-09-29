package com.jadroid.launcher.launch

import com.jadroid.launcher.core.AppLog
import com.jadroid.launcher.core.LauncherPaths
import com.jadroid.launcher.core.ensureDir
import com.jadroid.launcher.data.auth.Account
import com.jadroid.launcher.data.auth.AuthRepository
import com.jadroid.launcher.data.instance.Instance
import com.jadroid.launcher.data.instance.InstanceRepository
import com.jadroid.launcher.data.minecraft.MinecraftInstaller
import com.jadroid.launcher.data.minecraft.ResolvedVersion
import com.jadroid.launcher.data.mojang.LaunchFeatures
import com.jadroid.launcher.data.mojang.OsProfile
import com.jadroid.launcher.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

sealed interface GamePhase {
    data object Idle : GamePhase
    data class Preparing(val message: String, val progress: Float? = null) : GamePhase
    data class Running(val pid: Long) : GamePhase
    data class Exited(val code: Int) : GamePhase
    data class Failed(val message: String) : GamePhase
}

/**
 * Orchestrates a launch:
 *  1. refresh the account token,
 *  2. resolve + verify the instance against the official Mojang repositories,
 *  3. locate the imported java runtime (see [JavaRuntimeManager]),
 *  4. build the command line, export `launch.sh` and stream the game log into the UI.
 */
class GameService(
    private val paths: LauncherPaths,
    private val installer: MinecraftInstaller,
    private val instances: InstanceRepository,
    private val auth: AuthRepository,
    private val settings: SettingsStore,
    private val commandBuilder: LaunchCommandBuilder,
    private val javaRuntimes: JavaRuntimeManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _phase = MutableStateFlow<GamePhase>(GamePhase.Idle)
    val phase: StateFlow<GamePhase> = _phase

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private var process: Process? = null
    private var logJob: Job? = null

    val isRunning: Boolean get() = process?.isAlive == true

    suspend fun launch(instance: Instance, account: Account): Result<Unit> = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext Result.failure(IllegalStateException("A game is already running."))
        clearLogs()
        try {
            val currentSettings = settings.settings.value
            publish(GamePhase.Preparing("Checking your session ..."))
            val session = if (currentSettings.autoRefreshTokens) {
                auth.accountWithValidToken(account)
            } else {
                account
            }

            publish(GamePhase.Preparing("Resolving ${instance.gameVersion} from the Mojang repository ..."))
            val plan = installer.plan(
                instance = instance,
                features = featuresOf(instance),
                os = OsProfile.ANDROID,
                includeAssets = !instance.installed
            )

            if (currentSettings.verifyBeforeLaunch) {
                publish(GamePhase.Preparing("Verifying game files ..."))
                installer.install(
                    plan = plan,
                    concurrency = currentSettings.downloadConcurrency,
                    onStage = { stage -> publish(GamePhase.Preparing(stage)) },
                    onProgress = { progress ->
                        publish(
                            GamePhase.Preparing(
                                "${progress.completed}/${progress.total} · ${progress.currentLabel}",
                                progress.fraction
                            )
                        )
                    }
                )
            }
            startProcess(instance, session, plan.resolved, plan.classpath, plan.nativesDir, currentSettings.javaPath)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            AppLog.e("Launch failed for ${instance.name}", t)
            appendLog("ERROR: ${t.message}")
            publish(GamePhase.Failed(t.message ?: t.javaClass.simpleName))
            Result.failure(t)
        }
    }

    private suspend fun startProcess(
        instance: Instance,
        session: Account,
        resolved: ResolvedVersion,
        classpath: List<File>,
        nativesDirectory: File,
        javaPath: String
    ): Result<Unit> {
        val runtime = javaRuntimes.detect(javaPath)
            ?: throw IllegalStateException(NO_RUNTIME_MESSAGE)
        publish(GamePhase.Preparing("Building the launch command ..."))

        val command = commandBuilder.build(
            instance = instance,
            account = session,
            resolved = resolved,
            classpath = classpath.filter { it.isFile },
            nativesDirectory = nativesDirectory,
            javaExecutable = runtime.executable,
            os = OsProfile.ANDROID,
            features = featuresOf(instance),
            settings = settings.settings.value
        )

        runCatching {
            File(paths.instanceDir(instance.id), SCRIPT_NAME).writeText(command.toScript())
        }.onFailure { AppLog.w("Could not export $SCRIPT_NAME", it) }

        appendLog("Java: ${runtime.executable.absolutePath}")
        appendLog("Command: ${command.toDisplayString()}")

        publish(GamePhase.Preparing("Starting the game ..."))
        val builder = ProcessBuilder(command.toArgumentList())
        builder.directory(command.workingDir.ensureDir())
        builder.environment().putAll(command.environment)
        builder.redirectErrorStream(true)
        val started = builder.start()
        process = started

        instances.save(
            instance.copy(
                installed = true,
                resolvedVersionId = resolved.id,
                lastPlayed = System.currentTimeMillis()
            )
        )
        streamLogs(instance, started)
        publish(GamePhase.Running(pidOf(started)))

        val exit = started.waitFor()
        process = null
        appendLog("Process finished with exit code $exit")
        publish(GamePhase.Exited(exit))
        return Result.success(Unit)
    }

    private fun streamLogs(instance: Instance, running: Process) {
        logJob?.cancel()
        logJob = scope.launch {
            val logFile = paths.instanceLogFile(instance.id)
            runCatching {
                logFile.parentFile?.ensureDir()
                logFile.writeText("")
            }
            runCatching {
                BufferedReader(InputStreamReader(running.inputStream)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        appendLog(line)
                        runCatching { logFile.appendText(line + "\n") }
                    }
                }
            }.onFailure { AppLog.w("Log streaming stopped", it) }
        }
    }

    /** Stops the running game, escalating to a forced kill if it does not exit quickly. */
    fun stop() {
        val running = process ?: return
        AppLog.i("Stopping the game process")
        runCatching { running.destroy() }
        scope.launch {
            delay(5000)
            if (running.isAlive) {
                AppLog.w("Force killing the game process")
                runCatching { running.destroyForcibly() }
            }
        }
    }

    fun appendLog(line: String) {
        val trimmed = if (line.length > 500) line.take(500) + " ..." else line
        _logs.value = (_logs.value + trimmed).takeLast(MAX_LOG_LINES)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun resetPhase() {
        if (!isRunning && _phase.value !is GamePhase.Preparing) publish(GamePhase.Idle)
    }

    private fun publish(phase: GamePhase) {
        _phase.value = phase
    }

    private fun featuresOf(instance: Instance) =
        LaunchFeatures(hasCustomResolution = instance.customResolution)

    /**
     * `Process.pid()` only exists on newer Android releases, so it is looked up reflectively and
     * gracefully degrades to `-1` (the UI simply hides the pid then).
     */
    private fun pidOf(running: Process): Long = runCatching {
        val method = running.javaClass.getMethod("pid")
        (method.invoke(running) as? Long) ?: (method.invoke(running) as? Int)?.toLong() ?: -1L
    }.getOrDefault(-1L)

    companion object {
        private const val MAX_LOG_LINES = 1000
        const val SCRIPT_NAME = "launch.sh"

        val NO_RUNTIME_MESSAGE = buildString {
            append("No Java runtime found. Android has no built-in JVM, so Jadroid needs an ")
            append("Android-compatible Java runtime. Open Settings › Java runtime and import one ")
            append("(a .zip containing bin/java, e.g. an OpenJDK/Zulu Android build or the runtime ")
            append("bundled with PojavLauncher-style builds).")
        }
    }
}
