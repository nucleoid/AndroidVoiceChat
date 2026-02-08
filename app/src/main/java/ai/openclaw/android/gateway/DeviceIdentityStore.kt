package ai.openclaw.android.gateway

import android.content.Context
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

class DeviceIdentityStore(context: Context) {

    private val prefs = context.getSharedPreferences("device_identity", Context.MODE_PRIVATE)

    data class DeviceIdentity(
        val deviceId: String,
        val publicKeyBase64Url: String,
        val privateKeyPkcs8: ByteArray,
    )

    fun getOrCreateIdentity(): DeviceIdentity {
        val existing = loadIdentity()
        if (existing != null) return existing
        return generateAndStore()
    }

    private fun loadIdentity(): DeviceIdentity? {
        val deviceId = prefs.getString("deviceId", null) ?: return null
        val pubKey = prefs.getString("publicKey", null) ?: return null
        val privKeyB64 = prefs.getString("privateKey", null) ?: return null
        val privKey = Base64.decode(privKeyB64, Base64.NO_WRAP)
        return DeviceIdentity(deviceId, pubKey, privKey)
    }

    private fun generateAndStore(): DeviceIdentity {
        val kpg = KeyPairGenerator.getInstance("Ed25519")
        val keyPair = kpg.generateKeyPair()

        // Extract raw 32-byte public key from SPKI encoding
        val spki = keyPair.public.encoded
        val rawPubKey = if (spki.size == 44) spki.copyOfRange(12, 44) else spki

        val deviceId = MessageDigest.getInstance("SHA-256")
            .digest(rawPubKey)
            .joinToString("") { "%02x".format(it) }

        val pubKeyB64Url = Base64.encodeToString(rawPubKey, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val privKeyPkcs8 = keyPair.private.encoded

        prefs.edit()
            .putString("deviceId", deviceId)
            .putString("publicKey", pubKeyB64Url)
            .putString("privateKey", Base64.encodeToString(privKeyPkcs8, Base64.NO_WRAP))
            .apply()

        return DeviceIdentity(deviceId, pubKeyB64Url, privKeyPkcs8)
    }

    fun sign(payload: String, identity: DeviceIdentity): String {
        val keySpec = PKCS8EncodedKeySpec(identity.privateKeyPkcs8)
        val privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(keySpec)
        val sig = Signature.getInstance("Ed25519")
        sig.initSign(privateKey)
        sig.update(payload.toByteArray(Charsets.UTF_8))
        val signatureBytes = sig.sign()
        return Base64.encodeToString(signatureBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
