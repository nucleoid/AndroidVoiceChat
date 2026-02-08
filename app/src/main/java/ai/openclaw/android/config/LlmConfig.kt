package ai.openclaw.android.config

import ai.openclaw.android.SecurePrefs
import kotlinx.serialization.Serializable

@Serializable
data class LlmConfig(
    val backend: LlmBackend = LlmBackend.OPENAI,
    val apiKey: String = "",
    val model: String = "",
    val customEndpoint: String = "",
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "You are OpenClaw, a helpful personal AI assistant. You are having a voice conversation. " +
            "Keep your responses concise and conversational. " +
            "Respond naturally as if speaking, avoiding markdown formatting, bullet points, or code blocks. " +
            "Use short sentences suitable for text-to-speech."

        fun defaultModelForBackend(backend: LlmBackend): String = when (backend) {
            LlmBackend.OPENAI -> "gpt-4o"
            LlmBackend.ANTHROPIC -> "claude-sonnet-4-5-20250514"
            LlmBackend.GEMINI -> "gemini-2.0-flash"
            LlmBackend.CUSTOM -> ""
        }
    }

    val effectiveModel: String
        get() = model.ifBlank { defaultModelForBackend(backend) }
}

enum class LlmBackend(val displayName: String) {
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic Claude"),
    GEMINI("Google Gemini"),
    CUSTOM("Custom Endpoint"),
}

class LlmConfigStore(private val securePrefs: SecurePrefs) {

    fun load(): LlmConfig {
        val backendName = securePrefs.getString("llm_backend") ?: LlmBackend.OPENAI.name
        val backend = try { LlmBackend.valueOf(backendName) } catch (_: Exception) { LlmBackend.OPENAI }
        return LlmConfig(
            backend = backend,
            apiKey = securePrefs.getString("llm_api_key") ?: "",
            model = securePrefs.getString("llm_model") ?: "",
            customEndpoint = securePrefs.getString("llm_custom_endpoint") ?: "",
            systemPrompt = securePrefs.getString("llm_system_prompt") ?: LlmConfig.DEFAULT_SYSTEM_PROMPT,
        )
    }

    fun save(config: LlmConfig) {
        securePrefs.putString("llm_backend", config.backend.name)
        securePrefs.putString("llm_api_key", config.apiKey)
        securePrefs.putString("llm_model", config.model)
        securePrefs.putString("llm_custom_endpoint", config.customEndpoint)
        securePrefs.putString("llm_system_prompt", config.systemPrompt)
    }
}
