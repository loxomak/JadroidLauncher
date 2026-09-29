package com.jadroid.launcher.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jadroid.launcher.data.auth.Account
import com.jadroid.launcher.ui.components.SectionTitle
import com.jadroid.launcher.ui.viewmodel.AccountsViewModel
import com.jadroid.launcher.ui.viewmodel.SignInState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(accountsViewModel: AccountsViewModel) {
    val accounts by accountsViewModel.accounts.collectAsState()
    val active by accountsViewModel.activeAccount.collectAsState()
    val signInState by accountsViewModel.signInState.collectAsState()
    val message by accountsViewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var localName by remember { mutableStateOf("") }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            accountsViewModel.clearMessage()
        }
    }

    val hasMicrosoftAccount = accounts.any { it.isMicrosoft }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Accounts", fontWeight = FontWeight.SemiBold) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Card(modifier = Modifier.padding(top = 12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Microsoft account", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Jadroid uses the official Microsoft device-code flow: you open the link below, " +
                            "type the short code and approve the request. Tokens stay on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = accountsViewModel::signInWithMicrosoft,
                        enabled = signInState is SignInState.Idle ||
                            signInState is SignInState.Failed ||
                            signInState is SignInState.Success
                    ) { Text("Sign in with Microsoft") }
                }
            }

            SectionTitle("Accounts on this device")
            if (accounts.isEmpty()) {
                Text(
                    "No accounts yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            accounts.forEach { account ->
                AccountRow(
                    account = account,
                    selected = account.id == active?.id,
                    onSelect = { accountsViewModel.setActiveAccount(account.id) },
                    onRemove = { accountsViewModel.removeAccount(account.id) }
                )
            }

            SectionTitle("Local (offline) account")
            Card(
                modifier = Modifier.padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        if (hasMicrosoftAccount) {
                            "Create an offline profile. It can play single player and LAN worlds, " +
                                "but it cannot join online servers."
                        } else {
                            "Locked. Connect a Microsoft account first: local profiles are only " +
                                "unlocked once a real Minecraft account is linked to Jadroid."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = localName,
                        onValueChange = { localName = it },
                        label = { Text("Player name (3-16 characters)") },
                        singleLine = true,
                        enabled = hasMicrosoftAccount,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            accountsViewModel.createLocalAccount(localName)
                            localName = ""
                        },
                        enabled = hasMicrosoftAccount && localName.isNotBlank()
                    ) { Text("Create local account") }
                }
            }
        }
    }

    when (val state = signInState) {
        is SignInState.Idle -> Unit
        else -> SignInDialog(
            state = state,
            onCancel = accountsViewModel::cancelSignIn,
            onDismiss = accountsViewModel::dismissSignIn
        )
    }
}

@Composable
private fun AccountRow(
    account: Account,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(modifier = Modifier.weight(1f)) {
                Text(account.username, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (account.isMicrosoft) "Microsoft · ${account.uuid}" else "Local · ${account.uuid}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove account",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SignInDialog(
    state: SignInState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    when (state) {
        is SignInState.Starting -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Sign in with Microsoft") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Requesting a device code ...")
                }
            },
            confirmButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
        )

        is SignInState.Waiting -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Enter this code on microsoft.com/link") },
            text = {
                Column {
                    Text(
                        state.device.userCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(state.device.verificationUri, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { uriHandler.openUri(state.device.verificationUri) }) {
                    Text("Open the link")
                }
            },
            dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
        )

        is SignInState.Success -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Signed in") },
            text = { Text("Connected as ${state.username}. Local accounts are now unlocked.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
        )

        is SignInState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Sign-in failed") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )

        else -> Unit
    }
}
