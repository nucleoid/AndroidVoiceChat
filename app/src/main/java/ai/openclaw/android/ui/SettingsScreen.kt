package ai.openclaw.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.openclaw.android.VoiceChatViewModel
import ai.openclaw.android.config.LlmBackend
import ai.openclaw.android.config.LlmConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: VoiceChatViewModel,
    onNavigateBack: () -> Unit,
) {
    val llmConfig by viewModel.llmConfig.collectAsState()
    val ttsConfig by viewModel.ttsConfig.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // LLM Backend Section
            Text(
                text = "LLM Backend",
                style = MaterialTheme.typography.titleMedium,
            )

            // Backend selector
            var backendExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = backendExpanded,
                onExpandedChange = { backendExpanded = it },
            ) {
                OutlinedTextField(
                    value = llmConfig.backend.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Backend") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = backendExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = backendExpanded,
                    onDismissRequest = { backendExpanded = false },
                ) {
                    LlmBackend.entries.forEach { backend ->
                        DropdownMenuItem(
                            text = { Text(backend.displayName) },
                            onClick = {
                                viewModel.updateLlmConfig(llmConfig.copy(backend = backend))
                                backendExpanded = false
                            },
                        )
                    }
                }
            }

            // API Key
            var apiKeyVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = llmConfig.apiKey,
                onValueChange = { viewModel.updateLlmConfig(llmConfig.copy(apiKey = it)) },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (apiKeyVisible)
                    androidx.compose.ui.text.input.VisualTransformation.None
                else
                    androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Text(if (apiKeyVisible) "Hide" else "Show")
                    }
                },
            )

            // Model
            OutlinedTextField(
                value = llmConfig.model,
                onValueChange = { viewModel.updateLlmConfig(llmConfig.copy(model = it)) },
                label = { Text("Model") },
                placeholder = { Text(LlmConfig.defaultModelForBackend(llmConfig.backend)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Custom endpoint (only for CUSTOM backend)
            if (llmConfig.backend == LlmBackend.CUSTOM) {
                OutlinedTextField(
                    value = llmConfig.customEndpoint,
                    onValueChange = { viewModel.updateLlmConfig(llmConfig.copy(customEndpoint = it)) },
                    label = { Text("Custom Endpoint URL") },
                    placeholder = { Text("https://api.example.com/v1/chat/completions") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            HorizontalDivider()

            // Voice / TTS Section
            Text(
                text = "Voice Output",
                style = MaterialTheme.typography.titleMedium,
            )

            // ElevenLabs toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use ElevenLabs TTS", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Higher quality voice, requires API key",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = ttsConfig.useElevenLabs,
                    onCheckedChange = {
                        viewModel.updateTtsConfig(ttsConfig.copy(useElevenLabs = it))
                    },
                )
            }

            if (ttsConfig.useElevenLabs) {
                OutlinedTextField(
                    value = ttsConfig.elevenLabsApiKey ?: "",
                    onValueChange = {
                        viewModel.updateTtsConfig(ttsConfig.copy(elevenLabsApiKey = it))
                    },
                    label = { Text("ElevenLabs API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = ttsConfig.elevenLabsVoiceId,
                    onValueChange = {
                        viewModel.updateTtsConfig(ttsConfig.copy(elevenLabsVoiceId = it))
                    },
                    label = { Text("Voice ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // System TTS speed
            Text(
                text = "Speech Speed: %.1fx".format(ttsConfig.systemTtsSpeed),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = ttsConfig.systemTtsSpeed,
                onValueChange = {
                    viewModel.updateTtsConfig(ttsConfig.copy(systemTtsSpeed = it))
                },
                valueRange = 0.5f..2.0f,
                steps = 5,
            )

            // System prompt
            HorizontalDivider()
            Text(
                text = "System Prompt",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = llmConfig.systemPrompt,
                onValueChange = { viewModel.updateLlmConfig(llmConfig.copy(systemPrompt = it)) },
                label = { Text("System Prompt") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 6,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
