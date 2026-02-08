# OpenClaw Voice Chat

Android app + Android Auto integration for voice conversations with your [OpenClaw](https://github.com/openclaw/openclaw) AI personal assistant. Think Gemini Live, but for OpenClaw.

## Features

- **Full-duplex voice chat** — speak and listen simultaneously, interrupt the AI mid-sentence (barge-in), continuous conversation loop
- **Streaming TTS** — sentences are spoken as they arrive from the LLM, not after the full response completes
- **Android Auto** — hands-free voice conversations while driving via Car App Library
- **OpenClaw gateway integration** — connects via the same WebSocket JSON-RPC protocol (v3) as the native OpenClaw apps, with Ed25519 device authentication and TOFU TLS pinning
- **mDNS discovery** — automatically finds OpenClaw gateways on your local network, or connect manually by URL
- **Configurable LLM backend** — defaults to OpenAI, also supports Anthropic Claude, Google Gemini, or a custom endpoint
- **ElevenLabs TTS** — optional high-quality voice output with streaming, falls back to Android system TTS
- **Background operation** — foreground service with notification controls keeps voice sessions alive with the screen off

## Architecture

```
┌──────────────────────────────────────────┐
│  UI (Jetpack Compose + Android Auto)     │
│  VoiceChatScreen · SettingsScreen        │
│  ConnectionSetupScreen · VoiceChatCar*   │
├──────────────────────────────────────────┤
│  Voice Engine                            │
│  VoiceChatManager (full-duplex)          │
│  SpeechRecognitionEngine · TtsEngine     │
│  AudioFocusManager · AudioRoutingManager │
├──────────────────────────────────────────┤
│  Chat Layer                              │
│  ChatController · ChatModels             │
├──────────────────────────────────────────┤
│  Gateway Layer                           │
│  GatewaySession (WebSocket JSON-RPC)     │
│  DeviceIdentityStore (Ed25519)           │
│  GatewayDiscovery (mDNS + manual)        │
│  GatewayTls (TOFU pinning)              │
└──────────────────────────────────────────┘
```

## Voice Flow

1. User taps **Start Talking** — STT begins continuous recognition
2. Partial transcription displayed in real-time
3. 700ms silence detected — transcript sent to OpenClaw via `chat.send`
4. Gateway streams assistant response via `agent` events
5. Sentence boundaries detected — TTS starts speaking immediately
6. If user speaks during TTS — **barge-in**: TTS stops, new input processed
7. TTS finishes — loop back to listening
8. User taps **Pause** / **End** to control the session

## Requirements

- Android 12+ (API 31)
- An [OpenClaw](https://github.com/openclaw/openclaw) gateway running on your network or accessible remotely

## Building

```bash
# Clone
git clone https://github.com/nucleoid/AndroidVoiceChat.git
cd AndroidVoiceChat

# Create local.properties with your Android SDK path
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Build debug APK
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Setup

1. Install the APK on your Android device
2. Grant microphone and notification permissions when prompted
3. Open **Connection** (Wi-Fi icon) and either:
   - Select a discovered gateway from the list, or
   - Enter your gateway URL manually (e.g. `ws://192.168.1.100:18789`)
4. If this is a new device, approve it in the OpenClaw CLI: `openclaw devices approve`
5. Open **Settings** (gear icon) to configure your LLM backend and API key
6. Tap **Start Talking** and have a conversation

## Android Auto

The app registers as an Android Auto service automatically. When connected to a car:

1. Open **OpenClaw Voice** from the Android Auto app launcher
2. Tap **Start Voice Chat**
3. The same voice engine runs with audio routed through the car's speakers and microphone

## Configuration

| Setting | Description | Default |
|---------|-------------|---------|
| LLM Backend | OpenAI, Anthropic, Gemini, or Custom | OpenAI |
| API Key | Key for the selected LLM provider | — |
| Model | LLM model ID | `gpt-4o` |
| ElevenLabs TTS | Toggle high-quality voice synthesis | Off |
| ElevenLabs Voice ID | Voice to use for TTS | Rachel |
| Speech Speed | System TTS playback rate | 1.0x |

## License

MIT
