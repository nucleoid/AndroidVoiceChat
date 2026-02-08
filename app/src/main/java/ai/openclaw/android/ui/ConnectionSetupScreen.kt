package ai.openclaw.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.openclaw.android.VoiceChatViewModel
import ai.openclaw.android.gateway.GatewayEndpoint
import ai.openclaw.android.gateway.GatewaySession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSetupScreen(
    viewModel: VoiceChatViewModel,
    onNavigateBack: () -> Unit,
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val discoveredEndpoints by viewModel.discoveredEndpoints.collectAsState()

    var manualUrl by remember { mutableStateOf("") }
    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gateway Connection") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Connection status
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (connectionState) {
                            is GatewaySession.ConnectionState.Connected ->
                                MaterialTheme.colorScheme.primaryContainer
                            is GatewaySession.ConnectionState.Error ->
                                MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = when (connectionState) {
                                is GatewaySession.ConnectionState.Connected -> Icons.Default.CheckCircle
                                is GatewaySession.ConnectionState.Error -> Icons.Default.Error
                                is GatewaySession.ConnectionState.Connecting -> Icons.Default.Sync
                                else -> Icons.Default.WifiOff
                            },
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = when (connectionState) {
                                    is GatewaySession.ConnectionState.Connected -> "Connected"
                                    is GatewaySession.ConnectionState.Connecting -> "Connecting..."
                                    is GatewaySession.ConnectionState.WaitingForPairing ->
                                        "Waiting for device approval"
                                    is GatewaySession.ConnectionState.Error ->
                                        "Error: ${(connectionState as GatewaySession.ConnectionState.Error).message}"
                                    is GatewaySession.ConnectionState.Disconnected -> "Disconnected"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (connectionState is GatewaySession.ConnectionState.WaitingForPairing) {
                                Text(
                                    text = "Approve this device in the OpenClaw CLI: openclaw devices approve",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            // Disconnect button when connected
            if (connectionState is GatewaySession.ConnectionState.Connected) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.disconnect() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Disconnect")
                    }
                }
            }

            // Manual URL entry
            item {
                Text(
                    text = "Manual Connection",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = manualUrl,
                        onValueChange = { manualUrl = it },
                        label = { Text("Gateway URL") },
                        placeholder = { Text("ws://192.168.1.100:18789") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    FilledTonalButton(
                        onClick = {
                            if (manualUrl.isNotBlank()) {
                                viewModel.connectToUrl(manualUrl)
                            }
                        },
                        enabled = manualUrl.isNotBlank(),
                    ) {
                        Text("Connect")
                    }
                }
            }

            // Auth token
            item {
                OutlinedButton(
                    onClick = { showTokenDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Key, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Set Auth Token")
                }
            }

            // mDNS discovered gateways
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Discovered Gateways",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = { viewModel.refreshDiscovery() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }

            if (discoveredEndpoints.isEmpty()) {
                item {
                    Text(
                        text = "Searching for OpenClaw gateways on the local network...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(discoveredEndpoints) { endpoint ->
                DiscoveredEndpointCard(
                    endpoint = endpoint,
                    onClick = { viewModel.connectToEndpoint(endpoint) },
                )
            }
        }
    }

    // Token dialog
    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("Auth Token") },
            text = {
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("Token") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setAuthToken(tokenInput)
                    showTokenDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun DiscoveredEndpointCard(
    endpoint: GatewayEndpoint,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Router,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = endpoint.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "${endpoint.host}:${endpoint.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
            )
        }
    }
}
