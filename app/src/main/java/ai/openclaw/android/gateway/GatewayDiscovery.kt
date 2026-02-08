package ai.openclaw.android.gateway

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge

class GatewayDiscovery(private val context: Context) {

    companion object {
        private const val SERVICE_TYPE = "_openclaw._tcp."
    }

    fun discover(): Flow<GatewayEndpoint> {
        return merge(discoverMdns(), loadStored())
    }

    private fun loadStored(): Flow<GatewayEndpoint> {
        val prefs = context.getSharedPreferences("gateway_endpoints", Context.MODE_PRIVATE)
        val url = prefs.getString("manual_url", null) ?: return flowOf()
        val endpoint = parseEndpointUrl(url) ?: return flowOf()
        return flowOf(endpoint)
    }

    fun saveManualEndpoint(url: String) {
        context.getSharedPreferences("gateway_endpoints", Context.MODE_PRIVATE)
            .edit()
            .putString("manual_url", url)
            .apply()
    }

    fun parseEndpointUrl(url: String): GatewayEndpoint? {
        val cleaned = url.trim().removeSuffix("/")
        return try {
            val useTls = cleaned.startsWith("wss://") || cleaned.startsWith("https://")
            val hostPort = cleaned
                .removePrefix("wss://").removePrefix("ws://")
                .removePrefix("https://").removePrefix("http://")
            val parts = hostPort.split(":")
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: GatewayProtocol.DEFAULT_PORT
            GatewayEndpoint(host, port, useTls, source = DiscoverySource.STORED)
        } catch (_: Exception) {
            null
        }
    }

    private fun discoverMdns(): Flow<GatewayEndpoint> = callbackFlow {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsdManager == null) {
            close()
            return@callbackFlow
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        val host = si.host?.hostAddress ?: return
                        val port = si.port
                        val label = BonjourEscapes.decode(si.serviceName)
                        val endpoint = GatewayEndpoint(
                            host = host,
                            port = port,
                            label = label,
                            source = DiscoverySource.MDNS,
                        )
                        trySend(endpoint)
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (_: Exception) {}
        }
    }
}
