package ai.openclaw.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import ai.openclaw.android.service.VoiceChatForegroundService
import ai.openclaw.android.ui.ConnectionSetupScreen
import ai.openclaw.android.ui.SettingsScreen
import ai.openclaw.android.ui.VoiceChatScreen
import ai.openclaw.android.ui.theme.OpenClawTheme

class MainActivity : ComponentActivity() {

    private val viewModel: VoiceChatViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (audioGranted) {
            // Permission granted, user can now start voice chat
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestPermissionsIfNeeded()

        setContent {
            OpenClawTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDiscovery()
    }

    override fun onPause() {
        super.onPause()
        // If voice chat is active, start foreground service to keep it alive
        val voiceState = viewModel.voiceState.value
        if (voiceState != ai.openclaw.android.voice.VoiceChatManager.State.IDLE) {
            startVoiceForegroundService()
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startVoiceForegroundService() {
        val intent = Intent(this, VoiceChatForegroundService::class.java)
        startForegroundService(intent)
    }
}

enum class Screen {
    VOICE_CHAT,
    SETTINGS,
    CONNECTION,
}

@Composable
fun AppNavigation(viewModel: VoiceChatViewModel) {
    var currentScreen by remember { mutableStateOf(Screen.VOICE_CHAT) }

    when (currentScreen) {
        Screen.VOICE_CHAT -> VoiceChatScreen(
            viewModel = viewModel,
            onNavigateToSettings = { currentScreen = Screen.SETTINGS },
            onNavigateToConnection = { currentScreen = Screen.CONNECTION },
        )
        Screen.SETTINGS -> SettingsScreen(
            viewModel = viewModel,
            onNavigateBack = { currentScreen = Screen.VOICE_CHAT },
        )
        Screen.CONNECTION -> ConnectionSetupScreen(
            viewModel = viewModel,
            onNavigateBack = { currentScreen = Screen.VOICE_CHAT },
        )
    }
}
