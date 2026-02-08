package ai.openclaw.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class NodeApp : Application() {

    lateinit var runtime: NodeRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannels()

        runtime = NodeRuntime(this)
        runtime.initialize()
    }

    override fun onTerminate() {
        super.onTerminate()
        runtime.destroy()
    }

    private fun createNotificationChannels() {
        val voiceChannel = NotificationChannel(
            CHANNEL_VOICE,
            getString(R.string.notification_channel_voice),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_voice_desc)
            setShowBadge(false)
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(voiceChannel)
    }

    companion object {
        const val CHANNEL_VOICE = "voice_chat"
        var instance: NodeApp? = null
            private set
    }
}
