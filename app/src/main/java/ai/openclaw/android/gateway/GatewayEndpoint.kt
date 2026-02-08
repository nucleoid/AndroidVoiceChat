package ai.openclaw.android.gateway

data class GatewayEndpoint(
    val host: String,
    val port: Int = GatewayProtocol.DEFAULT_PORT,
    val useTls: Boolean = false,
    val label: String? = null,
    val source: DiscoverySource = DiscoverySource.MANUAL,
) {
    val wsUrl: String
        get() {
            val scheme = if (useTls) "wss" else "ws"
            return "$scheme://$host:$port"
        }

    val httpUrl: String
        get() {
            val scheme = if (useTls) "https" else "http"
            return "$scheme://$host:$port"
        }

    val displayName: String
        get() = label ?: "$host:$port"
}

enum class DiscoverySource {
    MANUAL,
    MDNS,
    STORED,
}
