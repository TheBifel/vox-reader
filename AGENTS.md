# VoxReader agent guide

This file is the authoritative entry point for coding agents working in this repository.

## Read order

1. Read [docs/memory/README.md](docs/memory/README.md).
2. Read the memory documents selected by its routing table.
3. For speaker-sidecar work, also read [docs/reference/SPEAKER_ANALYSIS_JSON.md](docs/reference/SPEAKER_ANALYSIS_JSON.md).
4. For book-labeling work, follow [skills/voxreader-label-book/SKILL.md](skills/voxreader-label-book/SKILL.md).

## Project rules

- Preserve the offline-first product boundary. Book text and speech generation stay on-device unless a future feature explicitly obtains user consent for cloud processing.
- `narrator` and `unknown` are reserved speaker IDs. Book characters use dynamic IDs from the imported map.
- Treat UTF-16 offsets in `BookDocument.text` as canonical. Never normalize or mutate canonical text to fix rendering.
- Keep pagination and highlighting viewport-local. Do not reintroduce whole-book layout work on book open or voice-mode activation.
- Keep speech engines behind `SpeechEngine`; reader UI must not depend on Piper, Kitten, or Android TTS implementation details.
- Opening a different book resets the active speaker map and per-book voice assignments.
- Model binaries and copyrighted books are local artifacts. Never add them to git.
- Install on a physical Samsung device only when the user explicitly asks. Develop and verify on an emulator by default.

## Architecture guardrail

Keep `ReaderViewModel` as the UI-state adapter for the application-layer reader controllers. Playback belongs in `ReaderPlaybackController`, book/analysis/voice-override workflow in `ReaderSessionCoordinator`, and history in `ReaderNavigationController`. Add speech implementations through `SpeechEngineRegistry` and model catalogs through `VoiceCatalogRepository`; do not branch reader behavior on concrete Piper, Kitten, or Android TTS classes.

`ReaderScreen` is the route-level Compose owner. Keep feature UI in the existing reader content, chrome, appearance, settings, character, and voice files, and extend the grouped `ReaderActions` API instead of adding a new flat callback list. Archive/file I/O belongs behind repositories, never in composables.

## Verification

Run from the repository root:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon
git diff --check
```

Update the matching file in `docs/memory/` whenever a durable behavior, architectural invariant, workflow, known limitation, or project state changes.
