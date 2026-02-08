package ai.openclaw.android.voice

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AudioRoutingManager(private val context: Context) {

    enum class AudioRoute {
        SPEAKER,
        EARPIECE,
        BLUETOOTH,
        WIRED_HEADSET,
        CAR_AUDIO,
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _activeRoute = MutableStateFlow(AudioRoute.SPEAKER)
    val activeRoute: StateFlow<AudioRoute> = _activeRoute

    private val _isBluetoothAvailable = MutableStateFlow(false)
    val isBluetoothAvailable: StateFlow<Boolean> = _isBluetoothAvailable

    private var bluetoothReceiver: BroadcastReceiver? = null

    fun start() {
        updateActiveRoute()
        registerBluetoothReceiver()
    }

    fun stop() {
        unregisterBluetoothReceiver()
    }

    fun getAvailableRoutes(): List<AudioRoute> {
        val routes = mutableListOf<AudioRoute>()
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        for (device in devices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> routes.add(AudioRoute.SPEAKER)
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> routes.add(AudioRoute.EARPIECE)
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> routes.add(AudioRoute.BLUETOOTH)
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET -> routes.add(AudioRoute.WIRED_HEADSET)
                AudioDeviceInfo.TYPE_BUS -> routes.add(AudioRoute.CAR_AUDIO)
            }
        }
        return routes.distinct()
    }

    fun setPreferredRoute(route: AudioRoute) {
        when (route) {
            AudioRoute.BLUETOOTH -> startBluetoothSco()
            AudioRoute.SPEAKER -> {
                stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
            }
            AudioRoute.EARPIECE -> {
                stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
            else -> {}
        }
        _activeRoute.value = route
    }

    fun isCarAudioConnected(): Boolean {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { it.type == AudioDeviceInfo.TYPE_BUS }
    }

    private fun updateActiveRoute() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        _activeRoute.value = when {
            devices.any { it.type == AudioDeviceInfo.TYPE_BUS } -> AudioRoute.CAR_AUDIO
            devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } -> {
                _isBluetoothAvailable.value = true
                AudioRoute.BLUETOOTH
            }
            devices.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES } ->
                AudioRoute.WIRED_HEADSET
            else -> AudioRoute.SPEAKER
        }
    }

    private fun startBluetoothSco() {
        try {
            @Suppress("DEPRECATION")
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        } catch (_: Exception) {}
    }

    private fun stopBluetoothSco() {
        try {
            audioManager.isBluetoothScoOn = false
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
        } catch (_: Exception) {}
    }

    private fun registerBluetoothReceiver() {
        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                        updateActiveRoute()
                    }
                    AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                        updateActiveRoute()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
    }

    private fun unregisterBluetoothReceiver() {
        bluetoothReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        bluetoothReceiver = null
    }
}
