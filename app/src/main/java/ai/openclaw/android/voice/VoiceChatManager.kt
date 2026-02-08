package ai.openclaw.android.voice

import ai.openclaw.android.chat.ChatController
import ai.openclaw.android.chat.ChatMessage
import ai.openclaw.android.chat.ChatMessageContent
import ai.openclaw.android.gateway.GatewayEvent
import ai.openclaw.android.gateway.GatewayProtocol
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Full-duplex voice chat orchestrator.
 *
 * Manages the continuous loop: Listen → Send → Stream → Speak → Listen
 * Supports barge-in (interrupt TTS when user speaks), pause/resume, and
 * sentence-level streaming TTS for low-latency responses.
 */
class VoiceChatManager(
    private val chatController: ChatController,
    private val speechEngine: SpeechRecognitionEngine,
    private val ttsEngine: TtsEngine,
    private val audioFocus: AudioFocusManager,
    private val audioRouting: AudioRoutingManager,
    private val scope: CoroutineScope,
) {
    enum class State {
        IDLE,
        LISTENING,
        PROCESSING,
        SPEAKING,
        PAUSED,
    }

    data class ConversationEntry(
        val role: String,
        val text: String,
        val timestampMs: Long = System.currentTimeMillis(),
    )

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private val _conversation = MutableStateFlow<List<ConversationEntry>>(emptyList())
    val conversation: StateFlow<List<ConversationEntry>> = _conversation

    private val _partialUserText = MutableStateFlow("")
    val partialUserText: StateFlow<String> = _partialUserText

    private val _streamingAssistantText = MutableStateFlow("")
    val streamingAssistantText: StateFlow<String> = _streamingAssistantText

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var speechCollectionJob: Job? = null
    private var streamingTtsJob: Job? = null
    private var lastSentenceBoundary = 0
    private var bargeInEnabled = true
    private var bargeInMinChars = 3

    fun start() {
        if (_state.value != State.IDLE) return

        val focusGained = audioFocus.requestFocus(
            onLostTransient = { pause() },
            onRegained = { resume() },
            onLost = { stop() },
        )

        if (!focusGained) {
            _errorMessage.value = "Could not acquire audio focus"
            return
        }

        audioRouting.start()
        ttsEngine.initialize()

        setupTtsCallbacks()
        startSpeechCollection()

        _state.value = State.LISTENING
        speechEngine.startListening()
    }

    fun stop() {
        _state.value = State.IDLE
        speechCollectionJob?.cancel()
        streamingTtsJob?.cancel()
        speechEngine.stopListening()
        ttsEngine.stop()
        audioFocus.abandonFocus()
        audioRouting.stop()
        _partialUserText.value = ""
        _streamingAssistantText.value = ""
        _errorMessage.value = null
    }

    fun pause() {
        if (_state.value == State.IDLE) return
        val previousState = _state.value
        _state.value = State.PAUSED
        speechEngine.stopListening()
        if (previousState == State.SPEAKING) {
            ttsEngine.pause()
        }
    }

    fun resume() {
        if (_state.value != State.PAUSED) return

        audioFocus.requestFocus(
            onLostTransient = { pause() },
            onRegained = { resume() },
            onLost = { stop() },
        )

        if (ttsEngine.state.value == TtsEngine.State.PAUSED) {
            _state.value = State.SPEAKING
            ttsEngine.resume()
        } else {
            _state.value = State.LISTENING
            speechEngine.startListening()
        }
    }

    fun togglePause() {
        if (_state.value == State.PAUSED) resume() else pause()
    }

    private fun setupTtsCallbacks() {
        ttsEngine.onSentenceStart = { sentence ->
            speechEngine.setRecentTtsOutput(sentence)
        }
        ttsEngine.onComplete = {
            // TTS finished speaking, go back to listening
            if (_state.value == State.SPEAKING) {
                _state.value = State.LISTENING
                _streamingAssistantText.value = ""
                lastSentenceBoundary = 0
                speechEngine.startListening()
            }
        }
        ttsEngine.onInterrupted = {
            // Barge-in happened, already in LISTENING
        }
    }

    private fun startSpeechCollection() {
        speechCollectionJob?.cancel()
        speechCollectionJob = scope.launch {
            speechEngine.results.collect { result ->
                when (result) {
                    is SpeechRecognitionEngine.Result.Partial -> {
                        _partialUserText.value = result.text
                        // Check for barge-in during TTS
                        if (_state.value == State.SPEAKING && bargeInEnabled) {
                            if (result.text.length >= bargeInMinChars) {
                                handleBargeIn()
                            }
                        }
                    }
                    is SpeechRecognitionEngine.Result.Final -> {
                        if (result.text.isNotBlank()) {
                            handleFinalSpeech(result.text)
                        }
                    }
                    is SpeechRecognitionEngine.Result.Error -> {
                        if (_state.value == State.LISTENING) {
                            _errorMessage.value = result.message
                            // Auto-clear error after 3 seconds
                            scope.launch {
                                delay(3000)
                                _errorMessage.value = null
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleBargeIn() {
        ttsEngine.interrupt()
        _streamingAssistantText.value = ""
        lastSentenceBoundary = 0
        _state.value = State.LISTENING
    }

    private fun handleFinalSpeech(text: String) {
        _partialUserText.value = ""
        _conversation.value = _conversation.value + ConversationEntry("user", text)

        _state.value = State.PROCESSING
        _streamingAssistantText.value = ""
        lastSentenceBoundary = 0

        // Start collecting streaming response for TTS
        startStreamingTtsCollection()

        scope.launch {
            try {
                chatController.sendMessage(text)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to send: ${e.message}"
                _state.value = State.LISTENING
                speechEngine.startListening()
            }
        }
    }

    private fun startStreamingTtsCollection() {
        streamingTtsJob?.cancel()
        streamingTtsJob = scope.launch {
            // Monitor the chat controller's streaming text and feed sentences to TTS
            var lastLength = 0
            var responseDone = false

            // Watch for streaming text changes
            val textJob = launch {
                chatController.streamingText.collect { fullText ->
                    if (fullText.length > lastLength) {
                        val newText = fullText.substring(lastLength)
                        lastLength = fullText.length
                        _streamingAssistantText.value = fullText

                        // Extract complete sentences for TTS
                        val sentences = extractNewSentences(fullText)
                        for (sentence in sentences) {
                            if (_state.value == State.PROCESSING) {
                                _state.value = State.SPEAKING
                                speechEngine.stopListening()
                                // Re-enable listening for barge-in after a short delay
                                launch {
                                    delay(500)
                                    if (_state.value == State.SPEAKING && bargeInEnabled) {
                                        speechEngine.startListening()
                                    }
                                }
                            }
                            ttsEngine.speakStreaming(sentence)
                        }
                    }
                }
            }

            // Watch for chat completion
            val doneJob = launch {
                chatController.isProcessing.collect { processing ->
                    if (!processing && lastLength > 0 && !responseDone) {
                        responseDone = true
                        // Send any remaining text to TTS
                        val fullText = chatController.streamingText.value
                        val remaining = extractRemainingText(fullText)
                        if (remaining.isNotBlank()) {
                            if (_state.value == State.PROCESSING) {
                                _state.value = State.SPEAKING
                            }
                            ttsEngine.speakStreaming(remaining)
                        }

                        // Add completed response to conversation
                        if (fullText.isNotBlank()) {
                            _conversation.value = _conversation.value +
                                ConversationEntry("assistant", fullText)
                        }

                        textJob.cancel()
                    }
                }
            }
        }
    }

    private fun extractNewSentences(fullText: String): List<String> {
        val sentences = mutableListOf<String>()
        var pos = lastSentenceBoundary

        while (pos < fullText.length) {
            val char = fullText[pos]
            if (char in ".!?\n" && pos > lastSentenceBoundary) {
                // Check it's not an abbreviation (simple heuristic)
                val nextChar = fullText.getOrNull(pos + 1)
                if (nextChar == null || nextChar == ' ' || nextChar == '\n') {
                    val sentence = fullText.substring(lastSentenceBoundary, pos + 1).trim()
                    if (sentence.isNotBlank() && sentence.length > 2) {
                        sentences.add(sentence)
                    }
                    lastSentenceBoundary = pos + 1
                }
            }
            pos++
        }
        return sentences
    }

    private fun extractRemainingText(fullText: String): String {
        return if (lastSentenceBoundary < fullText.length) {
            val remaining = fullText.substring(lastSentenceBoundary).trim()
            lastSentenceBoundary = fullText.length
            remaining
        } else ""
    }

    fun handleGatewayEvent(event: GatewayEvent) {
        // Handle agent streaming events for voice
        if (event.name == GatewayProtocol.EVENT_AGENT) {
            val stream = event.payload["stream"]?.jsonPrimitive?.content
            if (stream == GatewayProtocol.STREAM_ERROR) {
                val message = event.payload["data"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                _errorMessage.value = message ?: "Agent error"
                if (_state.value == State.PROCESSING) {
                    _state.value = State.LISTENING
                    speechEngine.startListening()
                }
            }
        }
    }

    fun clearConversation() {
        _conversation.value = emptyList()
    }
}
