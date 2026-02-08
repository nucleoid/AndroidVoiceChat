package ai.openclaw.android.gateway

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class GatewaySession(
    private val identityStore: DeviceIdentityStore,
    private val authStore: DeviceAuthStore,
    private val tls: GatewayTls,
    private val role: String = GatewayProtocol.ROLE_OPERATOR,
    private val clientMode: String = GatewayProtocol.CLIENT_MODE_UI,
) {
    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data object WaitingForPairing : ConnectionState()
        data class Connected(val mainSessionKey: String?) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _events = MutableSharedFlow<GatewayEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GatewayEvent> = _events

    private var webSocket: WebSocket? = null
    private var endpoint: GatewayEndpoint? = null
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private var connectNonce: String? = null
    private var sessionScope: CoroutineScope? = null
    private var reconnectJob: Job? = null
    private var mainSessionKey: String? = null

    val isConnected: Boolean
        get() = _connectionState.value is ConnectionState.Connected

    fun connect(endpoint: GatewayEndpoint, scope: CoroutineScope) {
        this.endpoint = endpoint
        this.sessionScope = scope
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            runReconnectLoop(endpoint)
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
    }

    private suspend fun runReconnectLoop(endpoint: GatewayEndpoint) {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            _connectionState.value = ConnectionState.Connecting
            try {
                connectOnce(endpoint)
                attempt = 0
                // connectOnce only returns when the socket closes
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
            }
            // Exponential backoff: 350ms * 1.7^attempt, max 8s
            val delayMs = (350.0 * Math.pow(1.7, attempt.coerceAtMost(10).toDouble()))
                .toLong().coerceAtMost(8000)
            attempt++
            delay(delayMs)
        }
    }

    private suspend fun connectOnce(endpoint: GatewayEndpoint) {
        val completable = CompletableDeferred<Unit>()

        val clientBuilder = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS)

        if (endpoint.useTls) {
            val (sslFactory, trustManager) = tls.createTofuSocketFactory(endpoint.host)
            clientBuilder.sslSocketFactory(sslFactory, trustManager)
                .hostnameVerifier { _, _ -> true }
        }

        val client = clientBuilder.build()
        val request = Request.Builder().url(endpoint.wsUrl).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                webSocket = ws
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.Error(t.message ?: "WebSocket failure")
                if (!completable.isCompleted) completable.completeExceptionally(t)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                webSocket = null
                _connectionState.value = ConnectionState.Disconnected
                if (!completable.isCompleted) completable.complete(Unit)
            }
        }

        client.newWebSocket(request, listener)

        // Wait for challenge event with timeout
        val challengeReceived = withTimeoutOrNull(5000) {
            while (connectNonce == null && currentCoroutineContext().isActive) {
                delay(50)
            }
        }

        if (connectNonce == null) {
            throw Exception("No connect challenge received")
        }

        sendConnectRequest()

        // Wait for connection to close (or error)
        completable.await()
    }

    private fun handleMessage(text: String) {
        try {
            val json = Json.parseToJsonElement(text).jsonObject
            when (json["type"]?.jsonPrimitive?.content) {
                GatewayProtocol.TYPE_RESPONSE -> handleResponse(json)
                GatewayProtocol.TYPE_EVENT -> handleEvent(json)
            }
        } catch (_: Exception) {}
    }

    private fun handleResponse(json: JsonObject) {
        val id = json["id"]?.jsonPrimitive?.content ?: return
        val deferred = pendingRequests.remove(id) ?: return
        val ok = json["ok"]?.jsonPrimitive?.boolean ?: false
        if (ok) {
            val payload = json["payload"]?.jsonObject ?: buildJsonObject {}
            deferred.complete(payload)
        } else {
            val errorMsg = json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                ?: "Unknown error"
            val errorCode = json["error"]?.jsonObject?.get("code")?.jsonPrimitive?.content
            if (errorCode == "device.pairing_required") {
                _connectionState.value = ConnectionState.WaitingForPairing
            }
            deferred.completeExceptionally(GatewayException(errorCode ?: "unknown", errorMsg))
        }
    }

    private fun handleEvent(json: JsonObject) {
        val eventName = json["event"]?.jsonPrimitive?.content ?: return
        val payload = json["payload"]?.jsonObject ?: buildJsonObject {}

        when (eventName) {
            GatewayProtocol.EVENT_CONNECT_CHALLENGE -> {
                connectNonce = payload["nonce"]?.jsonPrimitive?.content
            }
            else -> {
                sessionScope?.launch {
                    _events.emit(GatewayEvent(eventName, payload))
                }
            }
        }
    }

    private fun sendConnectRequest() {
        val identity = identityStore.getOrCreateIdentity()
        val token = authStore.getActiveToken() ?: ""
        val nonce = connectNonce ?: ""
        val signedAtMs = System.currentTimeMillis()

        val signPayload = "v2|${identity.deviceId}|${GatewayProtocol.CLIENT_ID}|$clientMode|$role||$signedAtMs|$token|$nonce"
        val signature = identityStore.sign(signPayload, identity)

        val params = buildJsonObject {
            put("protocolVersion", GatewayProtocol.VERSION)
            putJsonObject("auth") {
                put("token", token)
            }
            putJsonObject("device") {
                put("id", identity.deviceId)
                put("publicKey", identity.publicKeyBase64Url)
                put("signature", signature)
                put("signedAt", signedAtMs)
                put("nonce", nonce)
            }
            putJsonObject("client") {
                put("id", GatewayProtocol.CLIENT_ID)
                put("mode", clientMode)
                put("role", role)
                put("name", "OpenClaw Voice (Android)")
                put("version", "1.0.0")
            }
        }

        sessionScope?.launch {
            try {
                val result = request(GatewayProtocol.METHOD_CONNECT, params)
                // Store device token if provided
                result["deviceToken"]?.jsonPrimitive?.content?.let { dt ->
                    authStore.setDeviceToken(dt)
                }
                mainSessionKey = result["mainSessionKey"]?.jsonPrimitive?.content
                _connectionState.value = ConnectionState.Connected(mainSessionKey)
            } catch (e: GatewayException) {
                if (e.code == "device.pairing_required") {
                    _connectionState.value = ConnectionState.WaitingForPairing
                } else {
                    _connectionState.value = ConnectionState.Error(e.message ?: "Connect failed")
                }
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "Connect failed")
            }
        }
    }

    suspend fun request(method: String, params: JsonObject = buildJsonObject {}): JsonObject {
        val id = UUID.randomUUID().toString()
        val frame = buildJsonObject {
            put("type", GatewayProtocol.TYPE_REQUEST)
            put("id", id)
            put("method", method)
            put("params", params)
        }
        val deferred = CompletableDeferred<JsonObject>()
        pendingRequests[id] = deferred

        val sent = webSocket?.send(frame.toString()) ?: false
        if (!sent) {
            pendingRequests.remove(id)
            throw Exception("WebSocket not connected")
        }

        return withTimeout(30_000) { deferred.await() }
    }

    fun sendEvent(event: String, payload: JsonObject = buildJsonObject {}) {
        val frame = buildJsonObject {
            put("type", GatewayProtocol.TYPE_EVENT)
            put("event", event)
            put("payload", payload)
        }
        webSocket?.send(frame.toString())
    }
}

data class GatewayEvent(
    val name: String,
    val payload: JsonObject,
)

class GatewayException(val code: String, message: String) : Exception(message)
