package com.jadroid.launcher.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jadroid.launcher.data.settings.ThemeMode
import com.jadroid.launcher.di.AppContainer
import com.jadroid.launcher.launch.JavaRuntime
import com.jadroid.launcher.ui.components.SectionTitle
import com.jadroid.launcher.ui.components.displayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState()

    var jvmArgs by remember(settings.defaultJvmArgs) { mutableStateOf(settings.defaultJvmArgs) }
    var javaPath by remember(settings.javaPath) { mutableStateOf(settings.javaPath) }
    var clientId by remember(settings.msClientId) { mutableStateOf(settings.msClientId) }
    var runtimes by remember { mutableStateOf<List<JavaRuntime>>(emptyList()) }
    var importing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runtimes = withContext(Dispatchers.IO) { container.javaRuntimes.scan() }
    }

    val pickRuntimeArchive = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = displayName(context, uri)
            importing = true
            status = "Importing $name ..."
            scope.launch {
                val result = runCatching {
                    container.javaRuntimes.importArchive(
                        archiveName = name,
                        source = {
                            context.contentResolver.openInputStream(uri)
                                ?: error("Cannot read $name")
                        }
                    )
                }
                importing = false
                status = result.fold(
                    onSuccess = { "Imported runtime: ${it.executable.absolutePath}" },
                    onFailure = { it.message ?: "Import failed" }
                )
                runtimes = withContext(Dispatchers.IO) { container.javaRuntimes.scan() }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings", fontWeight = FontWeight.SemiBold) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            SectionTitle("Appearance")
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Theme", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { container.settings.update { it.copy(themeMode = mode) } },
                                label = { Text(mode.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Use the system colour (Material You)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Off keeps the orange Jadroid palette.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.dynamicColor,
                            onCheckedChange = { value ->
                                container.settings.update { it.copy(dynamicColor = value) }
                            }
                        )
                    }
                }
            }

            SectionTitle("Java runtime")
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Android has no built-in JVM. Import an Android-compatible runtime archive " +
                            "(a .zip that contains bin/java) once, then every launch reuses it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { pickRuntimeArchive.launch(arrayOf("application/zip", "*/*")) },
                            enabled = !importing
                        ) { Text("Import runtime (.zip)") }
                        if (importing) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                    status?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = javaPath,
                        onValueChange = { javaPath = it },
                        label = { Text("Custom java executable (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { container.settings.update { it.copy(javaPath = javaPath.trim()) } }
                    ) { Text("Save path") }
                    Spacer(Modifier.height(10.dp))
                    val detected = container.javaRuntimes.detect(settings.javaPath)
                    Text(
                        detected?.let { "Active runtime: ${it.executable.absolutePath}" }
                            ?: "No runtime detected yet.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    runtimes.forEach { runtime ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                runtime.executable.absolutePath,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    container.javaRuntimes.delete(runtime)
                                    scope.launch {
                                        runtimes = withContext(Dispatchers.IO) {
                                            container.javaRuntimes.scan()
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete runtime",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            SectionTitle("Performance")
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Default memory · ${settings.defaultRamMb} MB",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = settings.defaultRamMb.toFloat(),
                        onValueChange = { value ->
                            container.settings.update { it.copy(defaultRamMb = value.toInt()) }
                        },
                        valueRange = 512f..8192f,
                        steps = 14
                    )
                    Text("Default JVM arguments", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = jvmArgs,
                        onValueChange = { jvmArgs = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { container.settings.update { it.copy(defaultJvmArgs = jvmArgs) } }
                    ) { Text("Save arguments") }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Parallel downloads · ${settings.downloadConcurrency}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = settings.downloadConcurrency.toFloat(),
                        onValueChange = { value ->
                            container.settings.update { it.copy(downloadConcurrency = value.toInt()) }
                        },
                        valueRange = 1f..16f,
                        steps = 14
                    )
                }
            }

            SectionTitle("Behaviour")
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    SwitchRow(
                        title = "Verify files before launch",
                        subtitle = "Re-check sizes and SHA-1 hashes against the Mojang repository.",
                        checked = settings.verifyBeforeLaunch,
                        onChange = { value ->
                            container.settings.update { it.copy(verifyBeforeLaunch = value) }
                        }
                    )
                    SwitchRow(
                        title = "Show snapshots",
                        subtitle = "Include snapshot versions in the version picker.",
                        checked = settings.showSnapshots,
                        onChange = { value ->
                            container.settings.update { it.copy(showSnapshots = value) }
                        }
                    )
                    SwitchRow(
                        title = "Refresh the Minecraft token on launch",
                        subtitle = "Keeps long sessions valid; also re-issues expired tokens automatically.",
                        checked = settings.autoRefreshTokens,
                        onChange = { value ->
                            container.settings.update { it.copy(autoRefreshTokens = value) }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Microsoft application (client) id", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { container.settings.update { it.copy(msClientId = clientId.trim()) } }
                    ) { Text("Save client id") }
                }
            }

            SectionTitle("Storage")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    StorageLine("Launcher root", container.paths.root.absolutePath)
                    StorageLine("Instances", container.paths.instances.absolutePath)
                    StorageLine("Libraries", container.paths.libraries.absolutePath)
                    StorageLine("Assets", container.paths.assets.absolutePath)
                    StorageLine("Runtimes", container.paths.runtime.absolutePath)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StorageLine(label: String, path: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(path, style = MaterialTheme.typography.labelSmall)
    }
}
