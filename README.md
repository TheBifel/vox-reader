# VoxReader

VoxReader is an offline-first Android EPUB/FB2 reader with real-time, character-aware local speech. It uses Kotlin, Jetpack Compose, MVVM, DataStore, Room, Android TTS, Piper, and KittenTTS Micro.

## Run the app

1. Open this repository in Android Studio.
2. Sync Gradle with JDK 21 and Android SDK 36 installed.
3. Select an API 26+ emulator.
4. Run the `app` configuration.
5. Open an EPUB/FB2 through Android **Open with** or use **Open** in the app drawer.

For command-line verification:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon
```

## What is implemented

- Horizontal pagination and vertical scrolling with saved position, TOC, progress, history, links, appearance controls, and sleep timer.
- Foreground speech with system media controls and screen-lock support.
- Procedural Android TTS, AI Simple/Piper, and AI Advanced/KittenTTS Micro engines.
- Downloadable app-private voice models, character voice assignment, phrase lookahead, and bounded neural PCM buffering.
- Imported speaker-analysis JSON with character colors, narrator/unknown identities, optional emotions, and configurable engine-independent post-processing.

No speaker map is required: narrator-only playback remains available. VoxReader performs no live LLM attribution and does not upload book text.

## Project documentation

- Agents: [AGENTS.md](AGENTS.md)
- Durable project memory: [docs/memory/README.md](docs/memory/README.md)
- Complete documentation map: [docs/README.md](docs/README.md)
- Speaker-map format: [docs/reference/SPEAKER_ANALYSIS_JSON.md](docs/reference/SPEAKER_ANALYSIS_JSON.md)

Local books, speaker maps, downloaded models, screenshots, work packets, and secrets are intentionally excluded from version control.
