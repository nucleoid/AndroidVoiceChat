package ai.openclaw.android.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AudioFocusManager(context: Context) {

    enum class FocusState {
        NONE,
        GAINED,
        LOST_TRANSIENT,
        LOST,
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _focusState = MutableStateFlow(FocusState.NONE)
    val focusState: StateFlow<FocusState> = _focusState

    private var focusRequest: AudioFocusRequest? = null
    private var onFocusLostTransient: (() -> Unit)? = null
    private var onFocusRegained: (() -> Unit)? = null
    private var onFocusLost: (() -> Unit)? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                _focusState.value = FocusState.GAINED
                onFocusRegained?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                _focusState.value = FocusState.LOST_TRANSIENT
                onFocusLostTransient?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                _focusState.value = FocusState.LOST
                onFocusLost?.invoke()
            }
        }
    }

    fun requestFocus(
        onLostTransient: (() -> Unit)? = null,
        onRegained: (() -> Unit)? = null,
        onLost: (() -> Unit)? = null,
    ): Boolean {
        onFocusLostTransient = onLostTransient
        onFocusRegained = onRegained
        onFocusLost = onLost

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(true)
            .build()

        val result = audioManager.requestAudioFocus(focusRequest!!)
        val gained = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (gained) {
            _focusState.value = FocusState.GAINED
        }
        return gained
    }

    fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        _focusState.value = FocusState.NONE
        onFocusLostTransient = null
        onFocusRegained = null
        onFocusLost = null
    }

    fun setSpeakerphoneOn(on: Boolean) {
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = on
    }
}
