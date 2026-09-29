package com.jadroid.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jadroid.launcher.data.instance.Instance
import com.jadroid.launcher.launch.GamePhase
import com.jadroid.launcher.ui.LocalJadroid
import com.jadroid.launcher.ui.components.InfoRow
import com.jadroid.launcher.ui.components.SectionTitle
import com.jadroid.launcher.ui.components.TaskBanner
import com.jadroid.launcher.ui.components.displayName
import com.jadroid.launcher.ui.components.formatDateTime
import com.jadroid.launcher.ui.viewmodel.InstancesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceDetailScreen(
    instanceId: String?,
    instancesViewModel: InstancesViewModel,
    onBack: () -> Unit
) {
    val instances by instancesViewModel.instances.collectAsState()
    val instance = instances.firstOrNull { it.id == instanceId }
    val tasks by instancesViewModel.tasks.collectAsState()
    val phase by instancesViewModel.gamePhase.collectAsState()
    val gameLogs by instancesViewModel.gameLogs.collectAsState()
    val appLogs by instancesViewModel.appLogs.collectAsState()
    val modsByInstance by instancesViewModel.mods.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(instanceId) {
        instanceId?.let { instancesViewModel.refreshMods(it) }
    }

    if (instance == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("This instance no longer exists.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBack) { Text("Back") }
        }
        return
    }

    val running = phase is GamePhase.Running

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(instance.name, fontWeight = FontWeight.SemiBold)
                        Text(instance.loaderLabel, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (running) {
                    Button(onClick = instancesViewModel::stopGame, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Stop the game")
                    }
                } else {
                    Button(
                        onClick = { instancesViewModel.launch(instance) },
                        enabled = instance.installed,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { instancesViewModel.install(instance) }) {
                        Text(if (instance.installed) "Verify" else "Install")
                    }
                }
            }

            tasks[instance.id]?.let { task ->
                TaskBanner(task = task, onDismiss = { instancesViewModel.clearTask(instance.id) })
            }

            TabRow(selectedTabIndex = tab) {
                listOf("Overview", "Mods", "Logs").forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                }
            }

            when (tab) {
                0 -> OverviewTab(
                    instance = instance,
                    instancesViewModel = instancesViewModel,
                    onDelete = { confirmDelete = true }
                )
                1 -> ModsTab(
                    instance = instance,
                    instancesViewModel = instancesViewModel,
                    modCount = modsByInstance[instance.id].orEmpty().size
                )
                else -> LogsTab(gameLogs = gameLogs, appLogs = appLogs)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${instance.name}?") },
            text = { Text("Mods, saves and logs inside the instance folder are deleted too.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        instancesViewModel.deleteInstance(instance)
                        confirmDelete = false
                        onBack()
                    }
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun OverviewTab(
    instance: Instance,
    instancesViewModel: InstancesViewModel,
    onDelete: () -> Unit
) {
    val container = LocalJadroid.current
    var ramMb by remember(instance.id, instance.allocatedRamMb) { mutableStateOf(instance.allocatedRamMb) }
    var jvmArgs by remember(instance.id, instance.jvmArgs) { mutableStateOf(instance.jvmArgs) }
    var customResolution by remember(instance.id, instance.customResolution) {
        mutableStateOf(instance.customResolution)
    }
    var width by remember(instance.id, instance.resolutionWidth) {
        mutableStateOf(instance.resolutionWidth.toString())
    }
    var height by remember(instance.id, instance.resolutionHeight) {
        mutableStateOf(instance.resolutionHeight.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        SectionTitle("Instance")
        Card {
            Column(modifier = Modifier.padding(14.dp)) {
                InfoRow("Minecraft", instance.gameVersion)
                InfoRow("Loader", instance.loaderLabel)
                InfoRow("Installed", if (instance.installed) "yes" else "not yet")
                InfoRow("Resolved version", instance.resolvedVersionId ?: "not resolved")
                InfoRow("Last played", formatDateTime(instance.lastPlayed))
                InfoRow("Created", formatDateTime(instance.created))
            }
        }

        SectionTitle("Game directory")
        Card {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    container.paths.gameDir(instance.id).absolutePath,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    "Jadroid exports launch.sh here on every start; mods live in the mods/ folder.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionTitle("Memory & arguments")
        Card {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Memory · $ramMb MB", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = ramMb.toFloat(),
                    onValueChange = { ramMb = it.toInt() },
                    valueRange = 512f..8192f,
                    steps = 14
                )
                OutlinedTextField(
                    value = jvmArgs,
                    onValueChange = { jvmArgs = it },
                    label = { Text("Extra JVM arguments") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Custom resolution", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Passes --width/--height instead of using the default window size.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = customResolution, onCheckedChange = { customResolution = it })
                }
                if (customResolution) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = width,
                            onValueChange = { input -> width = input.filter { it.isDigit() } },
                            label = { Text("Width") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = height,
                            onValueChange = { input -> height = input.filter { it.isDigit() } },
                            label = { Text("Height") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        instancesViewModel.updateInstance(
                            instance.copy(
                                allocatedRamMb = ramMb.coerceAtLeast(512),
                                jvmArgs = jvmArgs,
                                customResolution = customResolution,
                                resolutionWidth = width.toIntOrNull() ?: 854,
                                resolutionHeight = height.toIntOrNull() ?: 480
                            )
                        )
                    }
                ) { Text("Save instance settings") }
            }
        }

        SectionTitle("Danger zone")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "Deleting removes the whole instance folder, including mods, saves and logs.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete instance")
                }
            }
        }
    }
}

@Composable
private fun ModsTab(
    instance: Instance,
    instancesViewModel: InstancesViewModel,
    modCount: Int
) {
    val context = LocalContext.current
    val modsByInstance by instancesViewModel.mods.collectAsState()
    val mods = modsByInstance[instance.id].orEmpty()
    val pickModFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = displayName(context, uri)
            instancesViewModel.importMod(instance.id, name) {
                context.contentResolver.openInputStream(uri) ?: error("Cannot read $name")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        SectionTitle("Installed mods ($modCount)")
        if (mods.isEmpty()) {
            Text(
                "No mods in this instance yet. Use the Mods tab in the bottom bar to browse " +
                    "Modrinth, or import a jar you already have.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        mods.forEach { mod ->
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
                            "${if (mod.enabled) "enabled" else "disabled"} · ${mod.file.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = mod.enabled,
                        onCheckedChange = { instancesViewModel.toggleMod(instance.id, mod) }
                    )
                    IconButton(onClick = { instancesViewModel.deleteMod(instance.id, mod) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete mod",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                pickModFile.launch(arrayOf("application/java-archive", "application/zip", "*/*"))
            }
        ) { Text("Import a .jar file") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LogsTab(gameLogs: List<String>, appLogs: List<String>) {
    val container = LocalJadroid.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        SectionTitle("Game output")
        OutlinedButton(onClick = { container.gameService.clearLogs() }) { Text("Clear") }
        if (gameLogs.isEmpty()) {
            Text(
                "No output captured yet. Start the game and its stdout/stderr will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        gameLogs.takeLast(400).forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
        }

        SectionTitle("Launcher log")
        appLogs.takeLast(300).forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
