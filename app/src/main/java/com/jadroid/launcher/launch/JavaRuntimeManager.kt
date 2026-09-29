package com.jadroid.launcher.launch

import com.jadroid.launcher.core.AppLog
import com.jadroid.launcher.core.LauncherPaths
import com.jadroid.launcher.core.ensureDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

data class JavaRuntime(
    val executable: File,
    val home: File,
    val source: String
) {
    val label: String get() = "$source · ${home.name}"
}

/**
 * Finds the JVM that will run the game.
 *
 * Android has no system JVM, so Jadroid uses an Android-compatible Java runtime that the user
 * imports once (Settings › Java runtime › Import). Any archive containing `bin/java` works,
 * including the runtimes shipped by PojavLauncher-style builds and Zulu/OpenJDK Android builds.
 */
class JavaRuntimeManager(private val paths: LauncherPaths) {

    fun detect(configuredPath: String? = null): JavaRuntime? {
        // 1. explicit path from Settings
        if (!configuredPath.isNullOrBlank()) {
            resolveCandidate(File(configuredPath), "configured")?.let { return it }
        }
        // 2. imported runtimes, newest first
        scan().firstOrNull()?.let { return it }
        // 3. a `java` binary on PATH (works when running under Termux/ADB shell)
        val pathEnv = System.getenv("PATH").orEmpty()
        pathEnv.split(File.pathSeparator).forEach { dir ->
            val candidate = File(dir, "java")
            if (candidate.isFile && candidate.canExecute()) {
                return JavaRuntime(candidate, candidate.parentFile ?: dir.let { File(it) }, "PATH")
            }
        }
        return null
    }

    fun scan(): List<JavaRuntime> = paths.runtime.ensureDir()
        .listFiles()
        .orEmpty()
        .filter { it.isDirectory }
        .mapNotNull { resolveCandidate(it, "imported") }
        .sortedByDescending { it.home.name }

    /** Unpacks a java runtime archive into `<filesDir>/runtime/<name>` and returns it. */
    suspend fun importArchive(
        archiveName: String,
        source: () -> InputStream,
        onProgress: (String) -> Unit = {}
    ): JavaRuntime {
        val safeName = archiveName.substringAfterLast('/')
            .substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "java-runtime" }
        val staging = File(paths.runtime, "staging-$safeName")
        staging.deleteRecursively()
        staging.ensureDir()
        onProgress("Extracting $safeName ...")
        runInterruptible(Dispatchers.IO) {
            source().use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entries = 0
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val target = File(staging, entry.name)
                        if (!target.canonicalPath.startsWith(staging.canonicalPath)) {
                            throw IOException("Refusing to extract outside of the runtime directory")
                        }
                        if (entry.isDirectory) {
                            target.ensureDir()
                        } else {
                            target.parentFile?.ensureDir()
                            target.outputStream().use { output -> zip.copyTo(output) }
                            if (target.name == "java" || target.name.endsWith(".so")) {
                                target.setExecutable(true, false)
                            }
                            entries++
                        }
                        zip.closeEntry()
                    }
                    AppLog.i("Extracted $entries files from $safeName")
                }
            }
        }
        // The archive may contain a single wrapping directory, collapse it.
        val effective = collapseSingleDirectory(staging)
        val finalDir = File(paths.runtime, safeName)
        finalDir.deleteRecursively()
        if (!effective.renameTo(finalDir)) {
            effective.copyRecursively(finalDir, overwrite = true)
            effective.deleteRecursively()
        }
        val runtime = resolveCandidate(finalDir, "imported")
            ?: throw IOException("This archive does not contain a java runtime (bin/java was not found)")
        AppLog.i("Java runtime imported: ${runtime.executable.absolutePath}")
        return runtime
    }

    fun delete(runtime: JavaRuntime): Boolean {
        if (runtime.source != "imported") return false
        return runtime.home.deleteRecursively()
    }

    private fun collapseSingleDirectory(root: File): File {
        val children = root.listFiles().orEmpty()
        return if (children.size == 1 && children[0].isDirectory && !File(children[0], "bin").isDirectory) {
            children[0]
        } else {
            root
        }
    }

    private fun resolveCandidate(candidate: File, source: String): JavaRuntime? {
        if (!candidate.exists()) return null
        val executable = when {
            candidate.isFile && candidate.name == "java" -> candidate
            File(candidate, "bin/java").isFile -> File(candidate, "bin/java")
            candidate.isDirectory && candidate.listFiles().orEmpty().any { it.isDirectory } -> {
                candidate.listFiles().orEmpty()
                    .asSequence()
                    .filter { it.isDirectory }
                    .mapNotNull { File(it, "bin/java").takeIf { java -> java.isFile } }
                    .firstOrNull()
            }
            else -> null
        } ?: return null
        if (!executable.canExecute()) executable.setExecutable(true, false)
        return JavaRuntime(executable, executable.parentFile?.parentFile ?: executable.parentFile!!, source)
    }
}
