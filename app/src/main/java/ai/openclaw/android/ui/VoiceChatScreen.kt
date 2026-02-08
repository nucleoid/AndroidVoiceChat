package ai.openclaw.android.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ai.openclaw.android.VoiceChatViewModel
import ai.openclaw.android.gateway.GatewaySession
import ai.openclaw.android.ui.components.*
import ai.openclaw.android.voice.VoiceChatManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceChatScreen(
    viewModel: VoiceChatViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToConnection: () -> Unit,
) {
    val voiceState by viewModel.voiceState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val conversation by viewModel.conversation.collectAsState()
    val partialUserText by viewModel.partialUserText.collectAsState()
    val streamingAssistantText by viewModel.streamingAssistantText.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(conversation.size, partialUserText, streamingAssistantText) {
        if (conversation.isNotEmpty()) {
            listState.animateScrollToItem(
                conversation.size + (if (partialUserText.isNotBlank()) 1 else 0) +
                    (if (streamingAssistantText.isNotBlank()) 1 else 0)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenClaw Voice") },
                actions = {
                    StatusPill(connectionState)
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onNavigateToConnection) {
                        Icon(Icons.Default.Wifi, contentDescription = "Connection")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Error message
            AnimatedVisibility(visible = errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            // Conversation area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(conversation, key = { "${it.role}:${it.timestampMs}" }) { entry ->
                    ConversationBubble(entry = entry)
                }

                // Partial user speech (live transcription)
                if (partialUserText.isNotBlank()) {
                    item(key = "partial_user") {
                        StreamingBubble(
                            text = partialUserText,
                            label = "You (speaking...)",
                            isUser = true,
                        )
                    }
                }

                // Streaming assistant response
                if (streamingAssistantText.isNotBlank()) {
                    item(key = "streaming_assistant") {
                        StreamingBubble(
                            text = streamingAssistantText,
                            label = "OpenClaw (responding...)",
                            isUser = false,
                        )
                    }
                }
            }

            // Voice orb and status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp),
            ) {
                // Status text
                Text(
                    text = statusText(voiceState, connectionState),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(16.dp))

                // Animated orb
                VoiceOrb(
                    state = voiceState,
                    modifier = Modifier.size(160.dp),
                )

                Spacer(Modifier.height(16.dp))

                // Control buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (voiceState) {
                        VoiceChatManager.State.IDLE -> {
                            val canStart = connectionState is GatewaySession.ConnectionState.Connected
                            FilledTonalButton(
                                onClick = { viewModel.startVoiceChat() },
                                enabled = canStart,
                                modifier = Modifier.height(56.dp),
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Start Talking")
                            }
                        }

                        VoiceChatManager.State.PAUSED -> {
                            FilledTonalButton(
                                onClick = { viewModel.resumeVoiceChat() },
                                modifier = Modifier.height(56.dp),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Resume")
                            }
                            OutlinedButton(
                                onClick = { viewModel.stopVoiceChat() },
                                modifier = Modifier.height(56.dp),
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("End")
                            }
                        }

                        else -> {
                            FilledTonalButton(
                                onClick = { viewModel.pauseVoiceChat() },
                                modifier = Modifier.height(56.dp),
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Pause")
                            }
                            OutlinedButton(
                                onClick = { viewModel.stopVoiceChat() },
                                modifier = Modifier.height(56.dp),
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("End")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun statusText(
    voiceState: VoiceChatManager.State,
    connectionState: GatewaySession.ConnectionState,
): String = when {
    connectionState is GatewaySession.ConnectionState.Disconnected -> "Not connected"
    connectionState is GatewaySession.ConnectionState.Connecting -> "Connecting..."
    connectionState is GatewaySession.ConnectionState.WaitingForPairing -> "Waiting for device approval..."
    connectionState is GatewaySession.ConnectionState.Error ->
        "Error: ${(connectionState as GatewaySession.ConnectionState.Error).message}"
    else -> when (voiceState) {
        VoiceChatManager.State.IDLE -> "Tap to start"
        VoiceChatManager.State.LISTENING -> "Listening..."
        VoiceChatManager.State.PROCESSING -> "Thinking..."
        VoiceChatManager.State.SPEAKING -> "Speaking..."
        VoiceChatManager.State.PAUSED -> "Paused"
    }
}
