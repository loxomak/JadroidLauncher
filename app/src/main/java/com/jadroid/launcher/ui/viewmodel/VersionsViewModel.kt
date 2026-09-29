package com.jadroid.launcher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jadroid.launcher.core.AppLog
import com.jadroid.launcher.data.mojang.ManifestVersion
import com.jadroid.launcher.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

enum class VersionFilter(val label: String) {
    RELEASE("Releases"),
    SNAPSHOT("Snapshots"),
    ALL("All")
}

class VersionsViewModel(private val container: AppContainer) : ViewModel() {

    private val _types = MutableStateFlow<List<String>>(emptyList())
    val versionTypes: StateFlow<List<String>> = _types

    private val _latestRelease = MutableStateFlow("")
    val latestRelease: StateFlow<String> = _latestRelease

    private val _latestSnapshot = MutableStateFlow("")
    val latestSnapshot: StateFlow<String> = _latestSnapshot

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _filter = MutableStateFlow(
        if (container.settings.settings.value.showSnapshots) VersionFilter.ALL else VersionFilter.RELEASE
    )
    val filter: StateFlow<VersionFilter> = _filter

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _allVersions = MutableStateFlow<List<ManifestVersion>>(emptyList())

    val versions: StateFlow<List<ManifestVersion>> =
        combine(_allVersions, _query, _filter) { versions, query, filter ->
            versions.asSequence()
                .filter { version ->
                    when (filter) {
                        VersionFilter.RELEASE -> version.type == "release"
                        VersionFilter.SNAPSHOT -> version.type == "snapshot"
                        VersionFilter.ALL -> true
                    }
                }
                .filter { query.isBlank() || it.id.contains(query, ignoreCase = true) }
                .take(300)
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (_allVersions.value.isEmpty()) refresh()
    }

    fun refresh() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val manifest = container.mojang.manifest(forceRefresh = _allVersions.value.isNotEmpty())
                _allVersions.value = manifest.versions
                _latestRelease.value = manifest.latest.release
                _latestSnapshot.value = manifest.latest.snapshot
                _types.value = manifest.versions.map { it.type }.distinct()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("Could not load the Mojang version manifest", t)
                _error.value = t.message ?: "Could not load the version list"
            } finally {
                _loading.value = false
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setFilter(value: VersionFilter) {
        _filter.value = value
    }
}
