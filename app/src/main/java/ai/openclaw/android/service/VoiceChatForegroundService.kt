package ai.openclaw.android.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ai.openclaw.android.MainActivity
import ai.openclaw.android.NodeApp
import ai.openclaw.android.R
import ai.openclaw.android.voice.VoiceChatManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Foreground service that keeps the voice chat session alive when the app
 * is in the background or the screen is off. Shows a persistent notification
 * with Pause/Resume/End actions.
 */
class VoiceChatForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateObserverJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                NodeApp.instance?.runtime?.voiceChatManager?.pause()
            }
            ACTION_RESUME -> {
                NodeApp.instance?.runtime?.voiceChatManager?.resume()
            }
            ACTION_STOP -> {
                NodeApp.instance?.runtime?.voiceChatManager?.stop()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, createNotification(VoiceChatManager.State.LISTENING))
        observeState()

        return START_STICKY
    }

    private fun observeState() {
        stateObserverJob?.cancel()
        stateObserverJob = scope.launch {
            val manager = NodeApp.instance?.runtime?.voiceChatManager ?: return@launch
            manager.state.collectLatest { state ->
                if (state == VoiceChatManager.State.IDLE) {
                    stopSelf()
                } else {
                    val notification = createNotification(state)
                    val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                    nm.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun createNotification(state: VoiceChatManager.State): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, NodeApp.CHANNEL_VOICE)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        when (state) {
            VoiceChatManager.State.PAUSED -> {
                builder.setContentTitle(getString(R.string.notification_title_paused))
                builder.addAction(
                    0, getString(R.string.action_resume),
                    createActionIntent(ACTION_RESUME),
                )
            }
            else -> {
                builder.setContentTitle(getString(R.string.notification_title_active))
                builder.setContentText(
                    when (state) {
                        VoiceChatManager.State.LISTENING -> getString(R.string.status_listening)
                        VoiceChatManager.State.PROCESSING -> getString(R.string.status_processing)
                        VoiceChatManager.State.SPEAKING -> getString(R.string.status_speaking)
                        else -> ""
                    }
                )
                builder.addAction(
                    0, getString(R.string.action_pause),
                    createActionIntent(ACTION_PAUSE),
                )
            }
        }

        builder.addAction(
            0, getString(R.string.action_end),
            createActionIntent(ACTION_STOP),
        )

        return builder.build()
    }

    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(this, VoiceChatForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        stateObserverJob?.cancel()
        scope.cancel()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE = "ai.openclaw.android.voice.PAUSE"
        const val ACTION_RESUME = "ai.openclaw.android.voice.RESUME"
        const val ACTION_STOP = "ai.openclaw.android.voice.STOP"
    }
}
