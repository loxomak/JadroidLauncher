package com.jadroid.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jadroid.launcher.data.instance.Instance
import com.jadroid.launcher.launch.GamePhase
import com.jadroid.launcher.ui.components.TaskBanner
import com.jadroid.launcher.ui.components.formatDateTime
import com.jadroid.launcher.ui.viewmodel.AccountsViewModel
import com.jadroid.launcher.ui.viewmodel.InstancesViewModel
import com.jadroid.launcher.ui.viewmodel.TaskState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    instancesViewModel: InstancesViewModel,
    accountsViewModel: AccountsViewModel,
    onOpenInstance: (String) -> Unit,
    onOpenVersions: () -> Unit,
    onOpenAccounts: () -> Unit
) {
    val instances by instancesViewModel.instances.collectAsState()
    val activeAccount by accountsViewModel.activeAccount.collectAsState()
    val phase by instancesViewModel.gamePhase.collectAsState()
    val tasks by instancesViewModel.tasks.collectAsState()
    var pendingDelete by remember { mutableStateOf<Instance?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Jadroid", fontWeight = FontWeight.SemiBold)
                        Text(
                            activeAccount?.let {
                                "${it.username} · ${if (it.isMicrosoft) "Microsoft" else "local"}"
                            } ?: "No account connected",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = { TextButton(onClick = onOpenAccounts) { Text("Accounts") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenVersions,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New instance") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GamePhaseBanner(phase = phase, onStop = instancesViewModel::stopGame)

            if (instances.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No instances yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Pick a Minecraft version from the official Mojang manifest and Jadroid will " +
                            "download the client, libraries and assets for you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onOpenVersions) { Text("Browse versions") }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(instances, key = { it.id }) { instance ->
                        InstanceCard(
                            instance = instance,
                            running = phase is GamePhase.Running,
                            task = tasks[instance.id],
                            onPlay = { instancesViewModel.launch(instance) },
                            onStop = instancesViewModel::stopGame,
                            onInstall = { instancesViewModel.install(instance) },
                            onOpen = { onOpenInstance(instance.id) },
                            onDismissTask = { instancesViewModel.clearTask(instance.id) },
                            onDelete = { pendingDelete = instance }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { instance ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${instance.name}?") },
            text = { Text("The instance folder, including its mods, saves and logs, will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        instancesViewModel.deleteInstance(instance)
                        pendingDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun InstanceCard(
    instance: Instance,
    running: Boolean,
    task: TaskState?,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onDismissTask: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        instance.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(instance.loaderLabel, style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (instance.installed) {
                            "Last played ${formatDateTime(instance.lastPlayed)}"
                        } else {
                            "Not installed yet"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (running) {
                    Button(onClick = onStop) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Stop")
                    }
                } else {
                    Button(onClick = onPlay, enabled = instance.installed) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
            if (task != null) {
                Spacer(Modifier.height(6.dp))
                TaskBanner(task = task, onDismiss = onDismissTask)
            }
            Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onOpen) { Text("Details") }
                Spacer(Modifier.width(8.dp))
                if (!instance.installed) {
                    OutlinedButton(onClick = onInstall) { Text("Install") }
                } else {
                    Text(
                        instance.resolvedVersionId ?: instance.gameVersion,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun GamePhaseBanner(phase: GamePhase, onStop: () -> Unit) {
    if (phase is GamePhase.Idle) return
    val containerColor = when (phase) {
        is GamePhase.Failed -> MaterialTheme.colorScheme.errorContainer
        is GamePhase.Running -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val message = when (phase) {
        is GamePhase.Preparing -> "Preparing · ${phase.message}"
        is GamePhase.Running ->
            if (phase.pid > 0) "Minecraft is running (pid ${phase.pid})" else "Minecraft is running"
        is GamePhase.Exited -> "Minecraft exited with code ${phase.code}"
        is GamePhase.Failed -> "Launch failed: ${phase.message}"
        else -> ""
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (phase is GamePhase.Running) {
                    Button(onClick = onStop) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Stop")
                    }
                }
            }
            if (phase is GamePhase.Preparing) {
                Spacer(Modifier.height(6.dp))
                val progress = phase.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
