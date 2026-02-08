package ai.openclaw.android.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class SpeechRecognitionEngine(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    enum class State {
        IDLE,
        LISTENING,
        PROCESSING,
        ERROR,
    }

    sealed class Result {
        data class Partial(val text: String) : Result()
        data class Final(val text: String) : Result()
        data class Error(val code: Int, val message: String) : Result()
    }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private val _results = MutableSharedFlow<Result>(extraBufferCapacity = 16)
    val results: SharedFlow<Result> = _results

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText

    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldRestart = false
    private var restartJob: Job? = null
    private var consecutiveErrors = 0
    private var silenceTimeoutMs = 700L
    private var lastSpeechTimeMs = 0L

    // Echo suppression: ignore results that closely match recent TTS output
    private var recentTtsOutput: String? = null
    private var ttsOutputTimestamp = 0L

    fun setSilenceTimeout(ms: Long) {
        silenceTimeoutMs = ms
    }

    fun setRecentTtsOutput(text: String?) {
        recentTtsOutput = text
        ttsOutputTimestamp = System.currentTimeMillis()
    }

    fun startListening() {
        if (isListening) return
        shouldRestart = true
        consecutiveErrors = 0
        createAndStartRecognizer()
    }

    fun stopListening() {
        shouldRestart = false
        isListening = false
        restartJob?.cancel()
        destroyRecognizer()
        _state.value = State.IDLE
        _partialText.value = ""
    }

    private fun createAndStartRecognizer() {
        destroyRecognizer()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            scope.launch {
                _results.emit(Result.Error(-1, "Speech recognition not available"))
            }
            _state.value = State.ERROR
            return
        }

        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = State.LISTENING
                isListening = true
                consecutiveErrors = 0
                lastSpeechTimeMs = System.currentTimeMillis()
            }

            override fun onBeginningOfSpeech() {
                lastSpeechTimeMs = System.currentTimeMillis()
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.value = State.PROCESSING
            }

            override fun onError(error: Int) {
                isListening = false
                val shouldIgnore = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

                if (shouldIgnore && shouldRestart) {
                    scheduleRestart(300)
                    return
                }

                consecutiveErrors++
                if (shouldRestart && consecutiveErrors < 5) {
                    val delay = (300L * consecutiveErrors).coerceAtMost(3000L)
                    scheduleRestart(delay)
                } else {
                    _state.value = State.ERROR
                    scope.launch {
                        _results.emit(Result.Error(error, errorMessage(error)))
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""

                if (text.isNotBlank() && !isEchoOfTts(text)) {
                    _partialText.value = text
                    scope.launch {
                        _results.emit(Result.Final(text))
                    }
                }

                _partialText.value = ""
                if (shouldRestart) {
                    scheduleRestart(100)
                } else {
                    _state.value = State.IDLE
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: return
                if (text.isNotBlank()) {
                    lastSpeechTimeMs = System.currentTimeMillis()
                    _partialText.value = text
                    scope.launch {
                        _results.emit(Result.Partial(text))
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceTimeoutMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceTimeoutMs)
        }

        try {
            sr.startListening(intent)
            _state.value = State.LISTENING
        } catch (e: Exception) {
            _state.value = State.ERROR
            scope.launch {
                _results.emit(Result.Error(-1, "Failed to start: ${e.message}"))
            }
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(delayMs)
            if (shouldRestart) {
                createAndStartRecognizer()
            }
        }
    }

    private fun isEchoOfTts(text: String): Boolean {
        val tts = recentTtsOutput ?: return false
        // If TTS was played within the last 3 seconds, check for similarity
        if (System.currentTimeMillis() - ttsOutputTimestamp > 3000) return false
        val normalizedText = text.lowercase().trim()
        val normalizedTts = tts.lowercase().trim()
        // Simple similarity check: if the spoken text is largely contained in recent TTS
        return normalizedTts.contains(normalizedText) ||
            normalizedText.length < 4 // Very short results during TTS are likely echo
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
        isListening = false
    }

    fun destroy() {
        stopListening()
        destroyRecognizer()
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Unknown error ($code)"
    }
}
