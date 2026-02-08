package ai.openclaw.android.gateway

object GatewayProtocol {
    const val VERSION = 3
    const val DEFAULT_PORT = 18789
    const val CLIENT_ID = "openclaw-voice-android"
    const val CLIENT_MODE_UI = "ui"
    const val CLIENT_MODE_NODE = "node"
    const val ROLE_OPERATOR = "operator"
    const val ROLE_NODE = "node"

    // Frame types
    const val TYPE_REQUEST = "req"
    const val TYPE_RESPONSE = "res"
    const val TYPE_EVENT = "event"

    // Methods
    const val METHOD_CONNECT = "connect"
    const val METHOD_CHAT_SEND = "chat.send"
    const val METHOD_CHAT_HISTORY = "chat.history"
    const val METHOD_CHAT_ABORT = "chat.abort"
    const val METHOD_SESSIONS_LIST = "sessions.list"
    const val METHOD_SESSIONS_PATCH = "sessions.patch"
    const val METHOD_CONFIG_GET = "config.get"
    const val METHOD_CONFIG_SET = "config.set"
    const val METHOD_HEALTH = "health"
    const val METHOD_NODE_EVENT = "node.event"

    // Events
    const val EVENT_CONNECT_CHALLENGE = "connect.challenge"
    const val EVENT_CHAT = "chat"
    const val EVENT_AGENT = "agent"
    const val EVENT_TICK = "tick"
    const val EVENT_SHUTDOWN = "shutdown"
    const val EVENT_HEALTH = "health"

    // Chat states
    const val CHAT_STATE_FINAL = "final"
    const val CHAT_STATE_ABORTED = "aborted"
    const val CHAT_STATE_ERROR = "error"

    // Agent stream types
    const val STREAM_ASSISTANT = "assistant"
    const val STREAM_TOOL = "tool"
    const val STREAM_ERROR = "error"
}
