package com.jadroid.launcher.data.instance

import com.jadroid.launcher.core.JadroidJson
import com.jadroid.launcher.core.LauncherPaths
import com.jadroid.launcher.core.deleteQuietly
import com.jadroid.launcher.core.ensureDir
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

/** File backed store for instances: `<minecraft>/instances/<id>/instance.json`. */
class InstanceRepository(private val paths: LauncherPaths) {

    private val _instances = MutableStateFlow<List<Instance>>(emptyList())
    val instances: StateFlow<List<Instance>> = _instances

    @Synchronized
    fun reload() {
        paths.instances.ensureDir()
        val loaded = paths.instances.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { dir -> readInstance(File(dir, FILE_NAME)) }
            .sortedByDescending { if (it.lastPlayed > 0) it.lastPlayed else it.created }
        _instances.value = loaded
    }

    fun get(id: String): Instance? = _instances.value.firstOrNull { it.id == id }

    @Synchronized
    fun save(instance: Instance) {
        val dir = paths.instanceDir(instance.id).ensureDir()
        File(dir, FILE_NAME).writeText(JadroidJson.encodeToString(instance))
        val updated = _instances.value.filterNot { it.id == instance.id } + instance
        _instances.value = updated.sortedByDescending { if (it.lastPlayed > 0) it.lastPlayed else it.created }
    }

    @Synchronized
    fun delete(id: String) {
        paths.instanceDir(id).deleteQuietly()
        _instances.value = _instances.value.filterNot { it.id == id }
    }

    /** Creates the on-disk skeleton (game dir, mods, logs) for a new instance. */
    fun createDirectories(id: String) {
        paths.instanceDir(id).ensureDir()
        paths.gameDir(id).ensureDir()
        paths.modsDir(id).ensureDir()
        File(paths.gameDir(id), "logs").ensureDir()
        File(paths.gameDir(id), "saves").ensureDir()
    }

    private fun readInstance(file: File): Instance? = runCatching {
        JadroidJson.decodeFromString<Instance>(file.readText())
    }.getOrNull()

    companion object {
        const val FILE_NAME = "instance.json"

        fun newId(gameVersion: String): String {
            val stamp = System.currentTimeMillis().toString(36)
            return "${gameVersion.replace('.', '_')}-$stamp"
        }
    }
}
