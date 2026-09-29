package com.jadroid.launcher.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jadroid.launcher.data.instance.LoaderType
import com.jadroid.launcher.data.mods.InstalledMod
import com.jadroid.launcher.data.mods.ModrinthHit
import com.jadroid.launcher.ui.components.LoadingRow
import com.jadroid.launcher.ui.components.SectionTitle
import com.jadroid.launcher.ui.components.TaskBanner
import com.jadroid.launcher.ui.components.displayName
import com.jadroid.launcher.ui.components.formatSize
import com.jadroid.launcher.ui.viewmodel.InstancesViewModel
import com.jadroid.launcher.ui.viewmodel.ModSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModsScreen(
    modSearchViewModel: ModSearchViewModel,
    instancesViewModel: InstancesViewModel
) {
    val context = LocalContext.current
    val instances by instancesViewModel.instances.collectAsState()
    val modsByInstance by instancesViewModel.mods.collectAsState()
    val tasks by instancesViewModel.tasks.collectAsState()
    val query by modSearchViewModel.query.collectAsState()
    val results by modSearchViewModel.results.collectAsState()
    val loading by modSearchViewModel.loading.collectAsState()
    val message by modSearchViewModel.message.collectAsState()

    var selectedId by remember(instances) { mutableStateOf(instances.firstOrNull()?.id) }
    var instanceMenuOpen by remember { mutableStateOf(false) }
    val selected = instances.firstOrNull { it.id == selectedId }

    LaunchedEffect(selectedId) {
        selectedId?.let { instancesViewModel.refreshMods(it) }
    }

    val pickModFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val instanceId = selectedId
        if (uri != null && instanceId != null) {
            val name = displayName(context, uri)
            instancesViewModel.importMod(instanceId, name) {
                context.contentResolver.openInputStream(uri) ?: error("Cannot read $name")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mods", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(
                        onClick = {
                            selected?.let {
                                modSearchViewModel.search(it.gameVersion, loaderFor(it.loader.type))
                            }
                        }
                    ) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (instances.isEmpty()) {
                Text(
                    "Create an instance first: mods are always installed into a specific instance.",
                    style = MaterialTheme.typography.bodyMedium
                )
                return@Column
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { instanceMenuOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selected?.let { "${it.name} · ${it.loaderLabel}" } ?: "Select an instance")
                }
                DropdownMenu(
                    expanded = instanceMenuOpen,
                    onDismissRequest = { instanceMenuOpen = false }
                ) {
                    instances.forEach { instance ->
                        DropdownMenuItem(
                            text = { Text("${instance.name} · ${instance.loaderLabel}") },
                            onClick = {
                                selectedId = instance.id
                                instanceMenuOpen = false
                            }
                        )
                    }
                }
            }

            selected?.let { instance ->
                val loader = loaderFor(instance.loader.type)
                if (loader == null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            "This instance is vanilla, so mods cannot load. Create an instance with " +
                                "the Fabric loader to use mods.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = modSearchViewModel::setQuery,
                        label = { Text("Search Modrinth") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { modSearchViewModel.search(instance.gameVersion, loader) },
                        enabled = loader != null
                    ) { Text("Search") }
                }

                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (loading) LoadingRow("Searching Modrinth ...")

                results.forEach { hit ->
                    ModResultRow(
                        hit = hit,
                        enabled = loader != null,
                        onInstall = {
                            modSearchViewModel.install(
                                projectId = hit.projectId,
                                title = hit.title,
                                gameVersion = instance.gameVersion,
                                loader = loader.orEmpty(),
                                instanceId = instance.id
                            ) { instanceId -> instancesViewModel.refreshMods(instanceId) }
                        }
                    )
                }

                SectionTitle("Installed mods (${modsByInstance[instance.id].orEmpty().size})")
                OutlinedButton(
                    onClick = {
                        pickModFile.launch(arrayOf("application/java-archive", "application/zip", "*/*"))
                    }
                ) { Text("Import a .jar file") }
                tasks[instance.id]?.let { task ->
                    TaskBanner(task = task, onDismiss = { instancesViewModel.clearTask(instance.id) })
                }
                modsByInstance[instance.id].orEmpty().forEach { mod ->
                    InstalledModRow(
                        mod = mod,
                        onToggle = { instancesViewModel.toggleMod(instance.id, mod) },
                        onDelete = { instancesViewModel.deleteMod(instance.id, mod) }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun loaderFor(type: LoaderType): String? = when (type) {
    LoaderType.FABRIC -> "fabric"
    LoaderType.VANILLA -> null
}

@Composable
private fun ModResultRow(hit: ModrinthHit, enabled: Boolean, onInstall: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(hit.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    hit.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${formatSize(hit.downloads)} downloads · ${hit.categories.take(3).joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onInstall, enabled = enabled) { Text("Install") }
        }
    }
}

@Composable
private fun InstalledModRow(mod: InstalledMod, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(mod.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${if (mod.enabled) "enabled" else "disabled"} · ${formatSize(mod.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = mod.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete mod",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
