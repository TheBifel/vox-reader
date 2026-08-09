---
memory_type: architecture
authority: canonical
last_verified: 2026-08-10
update_when: module boundaries, storage, data flow, or invariants change
---

# Architecture memory

VoxReader is a single Android application module using Kotlin, Jetpack Compose, MVVM, manual dependency injection, DataStore, Room, and local speech engines. Minimum SDK is 26; compile/target SDK is 36; Java toolchain level is 21.

## Package map

- `application/reader`: framework-free playback, session/analysis, and navigation coordinators.
- `presentation/reader`: route-level Compose UI, feature-level reader/chrome/settings/voice files, grouped actions, nested UI state, and the state-adapter ViewModel.
- `domain/model`, `domain/repository`, `domain/speech`: stable models and contracts used across implementations.
- `data/epub`, `data/fb2`: EPUB/FB2 parsing, document loading, and bounded EPUB asset reads.
- `data/preanalysis`: speaker-sidecar validation, import, and Room-backed active revisions.
- `data/preferences`: DataStore-backed reader, library, engine, voice, and post-processing settings.
- `data/speech`: Android TTS, Piper, KittenTTS Micro, neural buffering, PCM post-processing, foreground playback, and model catalog/downloads.
- `data/database`: Room entities, DAOs, and schema.
- `VoxReaderApplication` / app container: manual composition root.

## Reader data flow

1. Android supplies a persisted content URI through **Open with** or the in-app picker.
2. The format loader produces `BookDocument.text`, chapters, links, and images.
3. The saved reading position is a UTF-16 character offset into `BookDocument.text`.
4. Horizontal pagination and vertical layout operate on the visible region instead of laying out the whole book up front.
5. Rendering may collapse excess paragraph separators or apply visual spacing, but it must preserve a mapping to canonical offsets for highlights, links, history, and persistence.
6. `ReaderSessionCoordinator` owns open/latest/analysis resolution and persists per-book voice overrides through `LibraryRepository`.
7. `ReaderNavigationController` owns bounded non-linear history; Compose reports viewport changes without owning durable history state.

`ReaderUiState` is divided into session, settings, playback, voice-catalog, and chrome sections. `ReaderViewModel` projects repository/controller flows into those sections. Compose emits feature-level `ReaderActions`; Android-only file pickers and external intents stay in `MainActivity`.

## Speaker-analysis flow

An imported sidecar is untrusted input. Import validates format and policy versions, book identity/hash, immutable phrase identity, text/hash/offsets, ranges, enums, and profile consistency before atomically activating a Room revision. The imported revision supplies phrase speakers, optional emotions, character profiles, and stable highlight/voice identities. DataStore voice overrides are layered over imported profiles. `narrator` and `unknown` are fixed system IDs; all other IDs are book-specific.

The app imports JSON but does not export a phrase manifest. Direct EPUB preparation by the repository skill is provisional until the generated sidecar passes app validation, because desktop and Android segmentation can differ.

## Speech flow

The reader talks only to the `SpeechEngine` contract:

- Procedural: Android's installed offline TTS voices.
- AI Simple: downloaded Piper ONNX voices through sherpa-onnx.
- AI Advanced: downloaded KittenTTS Micro voices through sherpa-onnx.

`ReaderPlaybackController` selects the active engine through `SpeechEngineRegistry`, prepares several upcoming phrases, retains recent phrases, rejects stale engine events after mode changes, and translates foreground-media commands. Neural engines use a bounded PCM pipeline/cache (approximately five ahead and three behind) so character changes do not insert silence. Foreground media playback and a partial wake lock keep generation responsive with the screen locked. If the buffer misses, UI loading state is shown.

Emotion post-processing is independent of engine selection. The master switch and per-emotion profiles are persisted in DataStore. Procedural delivery maps supported settings to native rate/pitch/volume/pause controls. Neural delivery applies pitch, tone, loudness, and timing to PCM. Unsupported procedural nuances such as warm/bright tone remain best-effort.

## Invariants

- Canonical offsets are UTF-16 offsets into the unchanged `BookDocument.text`.
- Page numbers are viewport-dependent estimates; the character offset is the durable position.
- Avoid whole-book visual layout and synchronous whole-book work on open or Voice-mode activation.
- A new book resets the active speaker map and per-book voice assignments.
- Missing speaker map means narrator-only playback and settings.
- Model files are downloaded on demand into app-private storage.
- Room owns imported analysis revisions; DataStore owns preferences and lightweight library/voice state.
- DataStore is the canonical owner of per-book voice overrides; imported analysis profiles are projected through those overrides when a session is resolved.
- Reader code must remain independent of concrete speech engines.
- Composables do not open EPUB archives; `BookAssetRepository` performs bounded asset reads off the UI thread.
