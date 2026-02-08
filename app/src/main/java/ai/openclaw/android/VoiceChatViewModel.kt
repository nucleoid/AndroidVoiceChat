package ai.openclaw.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import ai.openclaw.android.config.LlmConfig
import ai.openclaw.android.gateway.GatewayEndpoint
import ai.openclaw.android.gateway.GatewaySession
import ai.openclaw.android.voice.TtsEngine
import ai.openclaw.android.voice.VoiceChatManager
import kotlinx.coroutines.flow.StateFlow

class VoiceChatViewModel(application: Application) : AndroidViewModel(application) {

    private val runtime: NodeRuntime
        get() = (getApplication<NodeApp>()).runtime

    // Connection state
    val connectionState: StateFlow<GatewaySession.ConnectionState>
        get() = runtime.operatorSession.connectionState

    val discoveredEndpoints: StateFlow<List<GatewayEndpoint>>
        get() = runtime.discoveredEndpoints

    // Voice state
    val voiceState: StateFlow<VoiceChatManager.State>
        get() = runtime.voiceChatManager.state

    val conversation: StateFlow<List<VoiceChatManager.ConversationEntry>>
        get() = runtime.voiceChatManager.conversation

    val partialUserText: StateFlow<String>
        get() = runtime.voiceChatManager.partialUserText

    val streamingAssistantText: StateFlow<String>
        get() = runtime.voiceChatManager.streamingAssistantText

    val errorMessage: StateFlow<String?>
        get() = runtime.voiceChatManager.errorMessage

    // Config
    val llmConfig: StateFlow<LlmConfig>
        get() = runtime.llmConfig

    val ttsConfig: StateFlow<TtsEngine.TtsConfig>
        get() = runtime.ttsConfig

    // Connection actions
    fun connectToEndpoint(endpoint: GatewayEndpoint) = runtime.connectToEndpoint(endpoint)
    fun connectToUrl(url: String) = runtime.connectToUrl(url)
    fun disconnect() = runtime.disconnect()
    fun setAuthToken(token: String) = runtime.setAuthToken(token)
    fun refreshDiscovery() = runtime.startDiscovery()

    // Voice actions
    fun startVoiceChat() = runtime.voiceChatManager.start()
    fun stopVoiceChat() = runtime.voiceChatManager.stop()
    fun pauseVoiceChat() = runtime.voiceChatManager.pause()
    fun resumeVoiceChat() = runtime.voiceChatManager.resume()

    // Config actions
    fun updateLlmConfig(config: LlmConfig) = runtime.updateLlmConfig(config)
    fun updateTtsConfig(config: TtsEngine.TtsConfig) = runtime.updateTtsConfig(config)

    override fun onCleared() {
        super.onCleared()
        runtime.stopDiscovery()
    }
}
