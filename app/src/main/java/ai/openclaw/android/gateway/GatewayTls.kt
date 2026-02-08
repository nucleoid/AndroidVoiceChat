package ai.openclaw.android.gateway

import android.content.Context
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Trust-On-First-Use (TOFU) TLS pinning.
 * On first connect, accepts any certificate and stores its SHA-256 fingerprint.
 * On subsequent connects, verifies the fingerprint matches.
 */
class GatewayTls(context: Context) {

    private val prefs = context.getSharedPreferences("gateway_tls", Context.MODE_PRIVATE)

    fun getPinnedFingerprint(host: String): String? {
        return prefs.getString("fp:$host", null)
    }

    fun pinFingerprint(host: String, fingerprint: String) {
        prefs.edit().putString("fp:$host", fingerprint).apply()
    }

    fun clearPin(host: String) {
        prefs.edit().remove("fp:$host").apply()
    }

    fun createTofuSocketFactory(host: String): Pair<SSLSocketFactory, X509TrustManager> {
        val pinnedFp = getPinnedFingerprint(host)
        val trustManager = TofuTrustManager(host, pinnedFp) { fp ->
            pinFingerprint(host, fp)
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)
        return sslContext.socketFactory to trustManager
    }

    private class TofuTrustManager(
        private val host: String,
        private val pinnedFingerprint: String?,
        private val onFirstConnect: (String) -> Unit,
    ) : X509TrustManager {

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val cert = chain?.firstOrNull()
                ?: throw java.security.cert.CertificateException("No certificate")

            val fingerprint = sha256Fingerprint(cert)
            if (pinnedFingerprint == null) {
                onFirstConnect(fingerprint)
            } else if (fingerprint != pinnedFingerprint) {
                throw java.security.cert.CertificateException(
                    "TLS fingerprint mismatch for $host. Expected $pinnedFingerprint, got $fingerprint"
                )
            }
        }

        private fun sha256Fingerprint(cert: X509Certificate): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(cert.encoded).joinToString(":") { "%02X".format(it) }
        }
    }
}
