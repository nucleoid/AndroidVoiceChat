package ai.openclaw.android.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.*
import java.io.IOException
import java.util.LinkedList
import java.util.Locale
import java.util.UUID

class TtsEngine(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    enum class State {
        IDLE,
        SPEAKING,
        PAUSED,
    }

    data class TtsConfig(
        val elevenLabsApiKey: String? = null,
        val elevenLabsVoiceId: String = "21m00Tcm4TlvDq8ikWAM",
        val elevenLabsModel: String = "eleven_turbo_v2",
        val useElevenLabs: Boolean = false,
        val systemTtsSpeed: Float = 1.0f,
        val systemTtsPitch: Float = 1.0f,
    )

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private val _currentlySpeaking = MutableStateFlow("")
    val currentlySpeaking: StateFlow<String> = _currentlySpeaking

    var config = TtsConfig()

    private var systemTts: TextToSpeech? = null
    private var systemTtsReady = false
    private var audioTrack: AudioTrack? = null
    private val httpClient = OkHttpClient()
    private val sentenceQueue = LinkedList<String>()
    private var speakJob: Job? = null
    private var isInterrupted = false

    var onSentenceStart: ((String) -> Unit)? = null
    var onComplete: (() -> Unit)? = null
    var onInterrupted: (() -> Unit)? = null

    fun initialize() {
        systemTts = TextToSpeech(context) { status ->
            systemTtsReady = status == TextToSpeech.SUCCESS
            systemTts?.language = Locale.US
        }
    }

    fun speak(text: String) {
        val sentences = splitIntoSentences(text)
        sentenceQueue.addAll(sentences)
        if (_state.value != State.SPEAKING) {
            processQueue()
        }
    }

    fun speakStreaming(textChunk: String) {
        sentenceQueue.add(textChunk)
        if (_state.value != State.SPEAKING) {
            processQueue()
        }
    }

    fun interrupt() {
        isInterrupted = true
        speakJob?.cancel()
        sentenceQueue.clear()
        stopAudioTrack()
        systemTts?.stop()
        _state.value = State.IDLE
        _currentlySpeaking.value = ""
        onInterrupted?.invoke()
    }

    fun pause() {
        if (_state.value == State.SPEAKING) {
            _state.value = State.PAUSED
            audioTrack?.pause()
            systemTts?.stop()
        }
    }

    fun resume() {
        if (_state.value == State.PAUSED) {
            audioTrack?.play()
            _state.value = State.SPEAKING
            processQueue()
        }
    }

    fun stop() {
        interrupt()
        sentenceQueue.clear()
    }

    fun destroy() {
        stop()
        systemTts?.shutdown()
        systemTts = null
        audioTrack?.release()
        audioTrack = null
    }

    private fun processQueue() {
        if (sentenceQueue.isEmpty()) {
            _state.value = State.IDLE
            _currentlySpeaking.value = ""
            onComplete?.invoke()
            return
        }

        isInterrupted = false
        _state.value = State.SPEAKING

        speakJob = scope.launch(Dispatchers.IO) {
            while (sentenceQueue.isNotEmpty() && !isInterrupted) {
                val sentence = sentenceQueue.poll() ?: break
                withContext(Dispatchers.Main) {
                    _currentlySpeaking.value = sentence
                    onSentenceStart?.invoke(sentence)
                }

                if (config.useElevenLabs && config.elevenLabsApiKey != null) {
                    speakWithElevenLabs(sentence)
                } else {
                    speakWithSystemTts(sentence)
                }
            }

            if (!isInterrupted) {
                withContext(Dispatchers.Main) {
                    _state.value = State.IDLE
                    _currentlySpeaking.value = ""
                    onComplete?.invoke()
                }
            }
        }
    }

    private suspend fun speakWithElevenLabs(text: String) {
        val apiKey = config.elevenLabsApiKey ?: return speakWithSystemTts(text)

        val body = buildJsonObject {
            put("text", text)
            put("model_id", config.elevenLabsModel)
            putJsonObject("voice_settings") {
                put("stability", 0.5)
                put("similarity_boost", 0.75)
            }
        }

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/${config.elevenLabsVoiceId}/stream")
            .addHeader("xi-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "audio/mpeg")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                speakWithSystemTts(text)
                return
            }

            val audioData = response.body?.bytes()
            response.close()

            if (audioData != null && !isInterrupted) {
                playPcmOrMp3(audioData)
            }
        } catch (e: IOException) {
            if (!isInterrupted) {
                speakWithSystemTts(text)
            }
        }
    }

    private fun playPcmOrMp3(data: ByteArray) {
        // For simplicity, use MediaPlayer for MP3 data via a temp file approach
        // In production, use a streaming MediaDataSource
        try {
            val tempFile = java.io.File.createTempFile("tts_", ".mp3", context.cacheDir)
            tempFile.writeBytes(data)

            val mediaPlayer = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                prepare()
            }

            if (!isInterrupted) {
                val completable = CompletableDeferred<Unit>()
                mediaPlayer.setOnCompletionListener {
                    it.release()
                    tempFile.delete()
                    completable.complete(Unit)
                }
                mediaPlayer.setOnErrorListener { mp, _, _ ->
                    mp.release()
                    tempFile.delete()
                    completable.complete(Unit)
                    true
                }
                mediaPlayer.start()
                runBlocking { completable.await() }
            } else {
                mediaPlayer.release()
                tempFile.delete()
            }
        } catch (_: Exception) {}
    }

    private suspend fun speakWithSystemTts(text: String) {
        if (!systemTtsReady || systemTts == null) return

        val utteranceId = UUID.randomUUID().toString()
        val completable = CompletableDeferred<Unit>()

        withContext(Dispatchers.Main) {
            systemTts?.setSpeechRate(config.systemTtsSpeed)
            systemTts?.setPitch(config.systemTtsPitch)

            systemTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId) completable.complete(Unit)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId) completable.complete(Unit)
                }
                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId) completable.complete(Unit)
                }
            })

            val params = android.os.Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            systemTts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }

        try {
            withTimeout(30_000) { completable.await() }
        } catch (_: TimeoutCancellationException) {}
    }

    private fun stopAudioTrack() {
        try {
            audioTrack?.stop()
            audioTrack?.flush()
        } catch (_: Exception) {}
    }

    companion object {
        fun splitIntoSentences(text: String): List<String> {
            if (text.isBlank()) return emptyList()
            // Split on sentence boundaries while preserving punctuation
            val sentences = mutableListOf<String>()
            val current = StringBuilder()

            for (char in text) {
                current.append(char)
                if (char in ".!?" && current.length > 1) {
                    val sentence = current.toString().trim()
                    if (sentence.isNotBlank()) {
                        sentences.add(sentence)
                    }
                    current.clear()
                }
            }

            // Add remaining text
            val remaining = current.toString().trim()
            if (remaining.isNotBlank()) {
                sentences.add(remaining)
            }

            return sentences
        }
    }
}
