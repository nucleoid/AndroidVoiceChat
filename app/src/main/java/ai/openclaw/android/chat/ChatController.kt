package ai.openclaw.android.chat

import ai.openclaw.android.SessionKey
import ai.openclaw.android.gateway.GatewayEvent
import ai.openclaw.android.gateway.GatewayProtocol
import ai.openclaw.android.gateway.GatewaySession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*
import java.util.UUID

class ChatController(
    private val session: GatewaySession,
    private val scope: CoroutineScope,
) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _pendingToolCalls = MutableStateFlow<List<ChatPendingToolCall>>(emptyList())
    val pendingToolCalls: StateFlow<List<ChatPendingToolCall>> = _pendingToolCalls

    private var sessionKey: String = SessionKey.DEFAULT
    private var activeRunId: String? = null
    private var runTimeoutJob: Job? = null

    init {
        scope.launch {
            session.events.collect { event ->
                handleEvent(event)
            }
        }
    }

    fun setSessionKey(key: String) {
        sessionKey = SessionKey.normalize(key)
    }

    suspend fun sendMessage(text: String): String {
        val runId = UUID.randomUUID().toString()
        activeRunId = runId
        _isProcessing.value = true
        _streamingText.value = ""
        _pendingToolCalls.value = emptyList()

        val userMessage = ChatMessage(
            role = "user",
            content = listOf(ChatMessageContent(type = "text", text = text)),
        )
        _messages.value = _messages.value + userMessage

        // Arm timeout
        runTimeoutJob?.cancel()
        runTimeoutJob = scope.launch {
            delay(120_000)
            if (activeRunId == runId) {
                _isProcessing.value = false
                activeRunId = null
            }
        }

        val params = buildJsonObject {
            put("sessionKey", sessionKey)
            put("message", text)
            put("thinking", "off")
            put("timeoutMs", 30000)
            put("idempotencyKey", runId)
        }

        try {
            val result = session.request(GatewayProtocol.METHOD_CHAT_SEND, params)
            result["runId"]?.jsonPrimitive?.content?.let { activeRunId = it }
        } catch (e: Exception) {
            _isProcessing.value = false
            activeRunId = null
            runTimeoutJob?.cancel()
        }

        return runId
    }

    suspend fun abortCurrentRun() {
        val params = buildJsonObject {
            put("sessionKey", sessionKey)
        }
        try {
            session.request(GatewayProtocol.METHOD_CHAT_ABORT, params)
        } catch (_: Exception) {}
        _isProcessing.value = false
        activeRunId = null
        runTimeoutJob?.cancel()
    }

    suspend fun loadHistory() {
        val params = buildJsonObject {
            put("sessionKey", sessionKey)
        }
        try {
            val result = session.request(GatewayProtocol.METHOD_CHAT_HISTORY, params)
            val messagesJson = result["messages"]?.jsonArray ?: return
            _messages.value = messagesJson.mapNotNull { parseMessage(it.jsonObject) }
        } catch (_: Exception) {}
    }

    private fun handleEvent(event: GatewayEvent) {
        when (event.name) {
            GatewayProtocol.EVENT_AGENT -> handleAgentEvent(event.payload)
            GatewayProtocol.EVENT_CHAT -> handleChatEvent(event.payload)
        }
    }

    private fun handleAgentEvent(payload: JsonObject) {
        val stream = payload["stream"]?.jsonPrimitive?.content ?: return
        val data = payload["data"]?.jsonObject ?: return

        when (stream) {
            GatewayProtocol.STREAM_ASSISTANT -> {
                val text = data["text"]?.jsonPrimitive?.content ?: return
                _streamingText.value += text
            }
            GatewayProtocol.STREAM_TOOL -> {
                val phase = data["phase"]?.jsonPrimitive?.content ?: return
                val toolCallId = data["toolCallId"]?.jsonPrimitive?.content ?: return
                when (phase) {
                    "start" -> {
                        val name = data["name"]?.jsonPrimitive?.content ?: "tool"
                        _pendingToolCalls.value = _pendingToolCalls.value +
                            ChatPendingToolCall(toolCallId, name)
                    }
                    "result" -> {
                        _pendingToolCalls.value = _pendingToolCalls.value
                            .filter { it.toolCallId != toolCallId }
                    }
                }
            }
        }
    }

    private fun handleChatEvent(payload: JsonObject) {
        val state = payload["state"]?.jsonPrimitive?.content ?: return
        when (state) {
            GatewayProtocol.CHAT_STATE_FINAL -> {
                finalizeStreamingResponse()
                scope.launch { loadHistory() }
            }
            GatewayProtocol.CHAT_STATE_ABORTED,
            GatewayProtocol.CHAT_STATE_ERROR -> {
                finalizeStreamingResponse()
            }
        }
    }

    private fun finalizeStreamingResponse() {
        val streamedText = _streamingText.value
        if (streamedText.isNotBlank()) {
            val assistantMessage = ChatMessage(
                role = "assistant",
                content = listOf(ChatMessageContent(type = "text", text = streamedText)),
            )
            _messages.value = _messages.value + assistantMessage
        }
        _streamingText.value = ""
        _isProcessing.value = false
        _pendingToolCalls.value = emptyList()
        activeRunId = null
        runTimeoutJob?.cancel()
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    private fun parseMessage(json: JsonObject): ChatMessage? {
        val role = json["role"]?.jsonPrimitive?.content ?: return null
        val contentArray = json["content"]?.jsonArray ?: return null
        val content = contentArray.mapNotNull { parseContent(it.jsonObject) }
        val timestamp = json["timestamp"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
        return ChatMessage(role = role, content = content, timestampMs = timestamp)
    }

    private fun parseContent(json: JsonObject): ChatMessageContent? {
        val type = json["type"]?.jsonPrimitive?.content ?: return null
        return ChatMessageContent(
            type = type,
            text = json["text"]?.jsonPrimitive?.content,
            mimeType = json["mimeType"]?.jsonPrimitive?.content,
            fileName = json["fileName"]?.jsonPrimitive?.content,
            base64 = json["content"]?.jsonPrimitive?.content,
        )
    }
}
