package com.jadroid.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jadroid.launcher.data.instance.LoaderType
import com.jadroid.launcher.data.mojang.ManifestVersion
import com.jadroid.launcher.ui.LocalJadroid
import com.jadroid.launcher.ui.components.LoadingRow
import com.jadroid.launcher.ui.viewmodel.InstancesViewModel
import com.jadroid.launcher.ui.viewmodel.VersionFilter
import com.jadroid.launcher.ui.viewmodel.VersionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionsScreen(
    versionsViewModel: VersionsViewModel,
    instancesViewModel: InstancesViewModel,
    onInstanceCreated: (String) -> Unit
) {
    val versions by versionsViewModel.versions.collectAsState()
    val loading by versionsViewModel.loading.collectAsState()
    val error by versionsViewModel.error.collectAsState()
    val query by versionsViewModel.query.collectAsState()
    val filter by versionsViewModel.filter.collectAsState()
    val latestRelease by versionsViewModel.latestRelease.collectAsState()
    var pendingVersion by remember { mutableStateOf<ManifestVersion?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Minecraft versions", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (latestRelease.isBlank()) "piston-meta.mojang.com"
                            else "Latest release: $latestRelease",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    IconButton(onClick = versionsViewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = versionsViewModel::setQuery,
                label = { Text("Search a version") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VersionFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { versionsViewModel.setFilter(option) },
                        label = { Text(option.label) }
                    )
                }
            }
            error?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            if (loading) LoadingRow("Loading the version manifest ...")
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(versions, key = { it.id }) { version ->
                    VersionRow(version = version, onCreate = { pendingVersion = version })
                }
            }
        }
    }

    pendingVersion?.let { version ->
        CreateInstanceDialog(
            version = version,
            instancesViewModel = instancesViewModel,
            onDismiss = { pendingVersion = null },
            onCreated = { instanceId ->
                pendingVersion = null
                onInstanceCreated(instanceId)
            }
        )
    }
}

@Composable
private fun VersionRow(version: ManifestVersion, onCreate: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(version.id, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${version.type} · ${version.releaseTime.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onCreate) { Text("New instance") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateInstanceDialog(
    version: ManifestVersion,
    instancesViewModel: InstancesViewModel,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit
) {
    val settings by LocalJadroid.current.settings.settings.collectAsState()
    val loaders by instancesViewModel.loaderVersions.collectAsState()
    var name by remember { mutableStateOf(version.id) }
    var loader by remember { mutableStateOf(LoaderType.VANILLA) }
    var loaderVersion by remember { mutableStateOf("") }
    var ramMb by remember { mutableFloatStateOf(settings.defaultRamMb.toFloat()) }
    var loaderMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(version.id, loader) {
        if (loader == LoaderType.FABRIC && loaders.isEmpty()) {
            instancesViewModel.loadFabricLoaders(version.id)
        }
    }
    LaunchedEffect(loaders, loader) {
        if (loader == LoaderType.FABRIC && loaderVersion.isBlank()) {
            loaderVersion = loaders.firstOrNull { it.stable }?.version
                ?: loaders.firstOrNull()?.version.orEmpty()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New instance · ${version.id}") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Instance name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Mod loader", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = loader == LoaderType.VANILLA,
                        onClick = { loader = LoaderType.VANILLA },
                        label = { Text("Vanilla") }
                    )
                    FilterChip(
                        selected = loader == LoaderType.FABRIC,
                        onClick = { loader = LoaderType.FABRIC },
                        label = { Text("Fabric") }
                    )
                }
                if (loader == LoaderType.FABRIC) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { loaderMenuOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                loaderVersion.ifBlank {
                                    if (loaders.isEmpty()) "Loading loader versions ..."
                                    else "Choose a loader version"
                                }
                            )
                        }
                        DropdownMenu(
                            expanded = loaderMenuOpen,
                            onDismissRequest = { loaderMenuOpen = false }
                        ) {
                            loaders.take(50).forEach { entry ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${entry.version}${if (entry.stable) " · stable" else ""}")
                                    },
                                    onClick = {
                                        loaderVersion = entry.version
                                        loaderMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Memory · ${ramMb.toInt()} MB", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = ramMb,
                    onValueChange = { ramMb = it },
                    valueRange = 512f..8192f,
                    steps = 14
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (loader == LoaderType.VANILLA || loaderVersion.isNotBlank()),
                onClick = {
                    val instance = instancesViewModel.createInstance(
                        version = version,
                        name = name,
                        loaderType = loader,
                        loaderVersion = loaderVersion,
                        ramMb = ramMb.toInt()
                    )
                    instancesViewModel.install(instance)
                    onCreated(instance.id)
                }
            ) { Text("Create & download") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
