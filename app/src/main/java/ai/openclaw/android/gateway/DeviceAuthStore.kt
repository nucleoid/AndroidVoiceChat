package ai.openclaw.android.gateway

import ai.openclaw.android.SecurePrefs

class DeviceAuthStore(private val securePrefs: SecurePrefs) {

    fun getDeviceToken(): String? {
        return securePrefs.getString("device_token")
    }

    fun setDeviceToken(token: String) {
        securePrefs.putString("device_token", token)
    }

    fun clearDeviceToken() {
        securePrefs.remove("device_token")
    }

    fun getSharedToken(): String? {
        return securePrefs.getString("shared_token")
    }

    fun setSharedToken(token: String) {
        securePrefs.putString("shared_token", token)
    }

    fun getActiveToken(): String? {
        return getDeviceToken() ?: getSharedToken()
    }
}
