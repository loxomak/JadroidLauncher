package com.jadroid.launcher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jadroid.launcher.core.AppLog
import com.jadroid.launcher.data.mods.ModrinthHit
import com.jadroid.launcher.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class ModSearchViewModel(private val container: AppContainer) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<List<ModrinthHit>>(emptyList())
    val results: StateFlow<List<ModrinthHit>> = _results

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun setQuery(value: String) {
        _query.value = value
    }

    fun search(gameVersion: String?, loader: String?) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _message.value = null
            try {
                val response = container.modrinth.search(
                    query = _query.value.trim(),
                    gameVersion = gameVersion,
                    loader = loader
                )
                _results.value = response.hits
                if (response.hits.isEmpty()) _message.value = "No mods matched this search."
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("Modrinth search failed", t)
                _message.value = t.message ?: "Modrinth search failed"
            } finally {
                _loading.value = false
            }
        }
    }

    /** Resolves the best matching file and downloads it into the instance mods folder. */
    fun install(
        projectId: String,
        title: String,
        gameVersion: String,
        loader: String,
        instanceId: String,
        onDone: (String) -> Unit
    ) {
        viewModelScope.launch {
            _message.value = "Resolving $title ..."
            try {
                val version = container.modrinth.bestVersion(projectId, gameVersion, loader)
                    ?: throw IllegalStateException(
                        "No build of \"$title\" exists for Minecraft $gameVersion ($loader)."
                    )
                val file = version.primaryFile
                    ?: throw IllegalStateException("\"$title\" has no downloadable file.")
                container.mods.installFromUrl(
                    instanceId = instanceId,
                    url = file.url,
                    filename = file.filename,
                    sha1 = file.hashes.sha1,
                    size = file.size.takeIf { it > 0 }
                )
                _message.value = "Installed ${file.filename}"
                onDone(instanceId)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("Mod install failed", t)
                _message.value = t.message ?: "Could not install the mod"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
