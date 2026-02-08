package ai.openclaw.android

import android.content.Context
import ai.openclaw.android.chat.ChatController
import ai.openclaw.android.config.LlmConfig
import ai.openclaw.android.config.LlmConfigStore
import ai.openclaw.android.gateway.*
import ai.openclaw.android.voice.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Central orchestrator for the OpenClaw Voice Chat app.
 * Creates and wires together all managers and handles gateway lifecycle.
 */
class NodeRuntime(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Secure storage
    val securePrefs = SecurePrefs(context)

    // Gateway layer
    val identityStore = DeviceIdentityStore(context)
    val authStore = DeviceAuthStore(securePrefs)
    val gatewayTls = GatewayTls(context)
    val gatewayDiscovery = GatewayDiscovery(context)

    val operatorSession = GatewaySession(
        identityStore = identityStore,
        authStore = authStore,
        tls = gatewayTls,
        role = GatewayProtocol.ROLE_OPERATOR,
        clientMode = GatewayProtocol.CLIENT_MODE_UI,
    )

    // Chat layer
    val chatController = ChatController(operatorSession, scope)

    // Voice layer
    val speechEngine = SpeechRecognitionEngine(context, scope)
    val ttsEngine = TtsEngine(context, scope)
    val audioFocusManager = AudioFocusManager(context)
    val audioRoutingManager = AudioRoutingManager(context)

    val voiceChatManager = VoiceChatManager(
        chatController = chatController,
        speechEngine = speechEngine,
        ttsEngine = ttsEngine,
        audioFocus = audioFocusManager,
        audioRouting = audioRoutingManager,
        scope = scope,
    )

    // Config
    private val llmConfigStore = LlmConfigStore(securePrefs)
    private val _llmConfig = MutableStateFlow(LlmConfig())
    val llmConfig: StateFlow<LlmConfig> = _llmConfig

    private val _ttsConfig = MutableStateFlow(TtsEngine.TtsConfig())
    val ttsConfig: StateFlow<TtsEngine.TtsConfig> = _ttsConfig

    // Discovered endpoints
    private val _discoveredEndpoints = MutableStateFlow<List<GatewayEndpoint>>(emptyList())
    val discoveredEndpoints: StateFlow<List<GatewayEndpoint>> = _discoveredEndpoints

    private var discoveryJob: Job? = null

    fun initialize() {
        _llmConfig.value = llmConfigStore.load()
        loadTtsConfig()

        // Listen for gateway events and route to voice chat manager
        scope.launch {
            operatorSession.events.collect { event ->
                voiceChatManager.handleGatewayEvent(event)
            }
        }

        // Apply TTS config changes
        scope.launch {
            _ttsConfig.collect { config ->
                ttsEngine.config = config
            }
        }

        // Auto-connect to stored endpoint
        val storedUrl = context.getSharedPreferences("gateway_endpoints", Context.MODE_PRIVATE)
            .getString("manual_url", null)
        if (storedUrl != null) {
            val endpoint = gatewayDiscovery.parseEndpointUrl(storedUrl)
            if (endpoint != null) {
                connectToEndpoint(endpoint)
            }
        }
    }

    fun connectToEndpoint(endpoint: GatewayEndpoint) {
        operatorSession.connect(endpoint, scope)
    }

    fun connectToUrl(url: String) {
        gatewayDiscovery.saveManualEndpoint(url)
        val endpoint = gatewayDiscovery.parseEndpointUrl(url) ?: return
        connectToEndpoint(endpoint)
    }

    fun disconnect() {
        voiceChatManager.stop()
        operatorSession.disconnect()
    }

    fun setAuthToken(token: String) {
        authStore.setSharedToken(token)
    }

    fun startDiscovery() {
        discoveryJob?.cancel()
        _discoveredEndpoints.value = emptyList()
        discoveryJob = scope.launch {
            gatewayDiscovery.discover().collect { endpoint ->
                val current = _discoveredEndpoints.value
                if (current.none { it.host == endpoint.host && it.port == endpoint.port }) {
                    _discoveredEndpoints.value = current + endpoint
                }
            }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
    }

    fun updateLlmConfig(config: LlmConfig) {
        _llmConfig.value = config
        llmConfigStore.save(config)
    }

    fun updateTtsConfig(config: TtsEngine.TtsConfig) {
        _ttsConfig.value = config
        saveTtsConfig(config)
    }

    private fun loadTtsConfig() {
        _ttsConfig.value = TtsEngine.TtsConfig(
            elevenLabsApiKey = securePrefs.getString("tts_elevenlabs_key"),
            elevenLabsVoiceId = securePrefs.getString("tts_elevenlabs_voice") ?: "21m00Tcm4TlvDq8ikWAM",
            elevenLabsModel = securePrefs.getString("tts_elevenlabs_model") ?: "eleven_turbo_v2",
            useElevenLabs = securePrefs.getBoolean("tts_use_elevenlabs"),
            systemTtsSpeed = securePrefs.getString("tts_speed")?.toFloatOrNull() ?: 1.0f,
            systemTtsPitch = securePrefs.getString("tts_pitch")?.toFloatOrNull() ?: 1.0f,
        )
    }

    private fun saveTtsConfig(config: TtsEngine.TtsConfig) {
        config.elevenLabsApiKey?.let { securePrefs.putString("tts_elevenlabs_key", it) }
        securePrefs.putString("tts_elevenlabs_voice", config.elevenLabsVoiceId)
        securePrefs.putString("tts_elevenlabs_model", config.elevenLabsModel)
        securePrefs.putBoolean("tts_use_elevenlabs", config.useElevenLabs)
        securePrefs.putString("tts_speed", config.systemTtsSpeed.toString())
        securePrefs.putString("tts_pitch", config.systemTtsPitch.toString())
    }

    fun destroy() {
        voiceChatManager.stop()
        ttsEngine.destroy()
        speechEngine.destroy()
        operatorSession.disconnect()
        scope.cancel()
    }
}
