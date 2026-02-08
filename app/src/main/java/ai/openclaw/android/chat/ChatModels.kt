package ai.openclaw.android.chat

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: List<ChatMessageContent>,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    val textContent: String
        get() = content
            .filter { it.type == "text" }
            .joinToString("\n") { it.text ?: "" }

    val isUser: Boolean get() = role == "user"
    val isAssistant: Boolean get() = role == "assistant"
}

data class ChatMessageContent(
    val type: String,
    val text: String? = null,
    val mimeType: String? = null,
    val fileName: String? = null,
    val base64: String? = null,
)

data class ChatPendingToolCall(
    val toolCallId: String,
    val name: String,
    val startedAtMs: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
)

data class ConversationTurn(
    val userMessage: ChatMessage,
    val assistantMessage: ChatMessage?,
    val isStreaming: Boolean = false,
)
