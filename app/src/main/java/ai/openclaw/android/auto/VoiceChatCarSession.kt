package ai.openclaw.android.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class VoiceChatCarSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return VoiceChatCarScreen(carContext)
    }
}
