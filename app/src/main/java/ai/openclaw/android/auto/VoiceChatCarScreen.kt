package ai.openclaw.android.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ai.openclaw.android.NodeApp
import ai.openclaw.android.voice.VoiceChatManager
import kotlinx.coroutines.*

/**
 * Android Auto voice chat screen.
 *
 * Provides a minimal, glanceable UI per Android Auto driver distraction guidelines:
 * - Status text showing current voice state
 * - Large action button to start/stop/pause voice chat
 * - Recent conversation entries (last 3)
 */
class VoiceChatCarScreen(carContext: CarContext) : Screen(carContext) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var voiceChatManager: VoiceChatManager? = null
    private var currentState = VoiceChatManager.State.IDLE
    private var lastConversation = emptyList<VoiceChatManager.ConversationEntry>()
    private var statusText = "Tap to talk with OpenClaw"

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                voiceChatManager = NodeApp.instance?.runtime?.voiceChatManager
                startStateCollection()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
    }

    private fun startStateCollection() {
        val manager = voiceChatManager ?: return

        scope.launch {
            manager.state.collect { state ->
                val changed = currentState != state
                currentState = state
                statusText = when (state) {
                    VoiceChatManager.State.IDLE -> "Tap to talk with OpenClaw"
                    VoiceChatManager.State.LISTENING -> "Listening..."
                    VoiceChatManager.State.PROCESSING -> "Thinking..."
                    VoiceChatManager.State.SPEAKING -> "Speaking..."
                    VoiceChatManager.State.PAUSED -> "Paused"
                }
                if (changed) invalidate()
            }
        }

        scope.launch {
            manager.conversation.collect { entries ->
                val changed = lastConversation.size != entries.size
                lastConversation = entries.takeLast(3)
                if (changed) invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()

        // Add recent conversation rows (last 3 entries)
        for (entry in lastConversation) {
            val prefix = if (entry.role == "user") "You" else "OpenClaw"
            val text = entry.text.take(80) + if (entry.text.length > 80) "..." else ""
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("$prefix: $text")
                    .build()
            )
        }

        if (lastConversation.isEmpty()) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("OpenClaw Voice Chat")
                    .addText("Start a voice conversation with your AI assistant")
                    .build()
            )
        }

        // Main action button
        val actionTitle: String
        val action: Action

        when (currentState) {
            VoiceChatManager.State.IDLE -> {
                actionTitle = "Start Voice Chat"
                action = Action.Builder()
                    .setTitle(actionTitle)
                    .setOnClickListener { voiceChatManager?.start() }
                    .build()
            }
            VoiceChatManager.State.PAUSED -> {
                actionTitle = "Resume"
                action = Action.Builder()
                    .setTitle(actionTitle)
                    .setOnClickListener { voiceChatManager?.resume() }
                    .build()
            }
            else -> {
                actionTitle = "Stop"
                action = Action.Builder()
                    .setTitle(actionTitle)
                    .setOnClickListener { voiceChatManager?.stop() }
                    .build()
            }
        }

        paneBuilder.addAction(action)

        // Add pause action when active
        if (currentState != VoiceChatManager.State.IDLE && currentState != VoiceChatManager.State.PAUSED) {
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("Pause")
                    .setOnClickListener { voiceChatManager?.pause() }
                    .build()
            )
        }

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("OpenClaw Voice")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
