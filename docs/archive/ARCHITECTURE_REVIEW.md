# VoxReader architecture review

> Historical snapshot (2026-08-02). This review describes an earlier codebase; several findings
> and line references are now obsolete. Use [current architecture memory](../memory/ARCHITECTURE.md)
> and [technical debt](../memory/TECH_DEBT.md) for active guidance.

**Scope.** Static review of the repository as it existed on 2026-08-02, including the current working tree (which already contains uncommitted application changes). No application or build files were modified. `gradlew.bat testDebugUnitTest` completed successfully during this review.

## Executive assessment

VoxReader has a good small-app foundation: it is a single Android module, separates `domain`, `data`, and `presentation` packages, keeps the EPUB parser and pure text/speech helpers independently testable, and validates imported speaker-analysis input carefully. The primary risk is not an excessive number of modules or abstractions; it is *centralisation*: two classes own too many unrelated reasons to change.

`ReaderScreen.kt` is 1,817 lines and `ReaderViewModel.kt` is 879 lines. Together they directly coordinate reading layout, navigation history, playback state, two speech engines, downloaded model management, analysis import, character settings, persistence, and Android foreground-service commands. This makes normal feature work cross-cutting, difficult to test, and prone to playback/lifecycle regressions.

The recommended direction is deliberately modest: retain the single Gradle module and existing package layout, introduce a few explicit application-facing ports/use cases only where they remove Android/data dependencies from presentation, and split by coherent feature responsibility. Do not introduce a multi-module clean-architecture framework, generic result/event framework, DI framework, or new persistence layer merely for stylistic purity.

## Conceptual reference: Ponytail

[Ponytail](https://github.com/dietrichgebert/ponytail) is relevant as a decision discipline, not as a dependency or architecture to copy. Its documented ladder is: do not build it if it is unnecessary; otherwise reuse existing code, standard-library/platform facilities, installed dependencies, and only then write the smallest necessary code. It explicitly retains validation and safety work. See its [README, “How it works”](https://github.com/dietrichgebert/ponytail#how-it-works).

Applied here, that means:

- Preserve the useful existing seams (`SpeechEngine`, repository interfaces, pure segmentation/paging helpers) instead of rebuilding the app around a new framework.
- Delete or defer unused exported paths before adding new providers or UI flows.
- Extract only genuine ownership boundaries. Splitting the screen into dozens of wrapper composables or turning every setter into a use case would add ceremony without reducing coupling.
- Keep the sidecar validation and file-size limits: these are trust-boundary safeguards, not “extra” code.

## Current architecture and ownership

```text
MainActivity
  -> AppContainer (manual composition root)
  -> ReaderViewModel
       -> use cases / LibraryRepository / SettingsRepository
       -> SpeechEngine x2, PiperVoicePackManager, PortableAnalysisService
       -> MediaPlaybackService (static Android API)

data: EPUB + DataStore + Room + Android TTS + Sherpa/Piper + foreground service
domain: reader models, repository interfaces, segmentation, speech contracts,
        analysis manifest/sidecar validation
presentation: one reader UI file, UI state, paging/navigation helpers, view model
```

`AppContainer` is an appropriate small manual composition root ([`AppContainer.kt:16-30`](../../app/src/main/java/com/bifel/voxreader/AppContainer.kt)); a DI framework would be YAGNI at this scale. The useful dependency direction is sometimes broken, however: presentation imports concrete data classes/services ([`ReaderViewModel.kt:5,26,31`](../../app/src/main/java/com/bifel/voxreader/presentation/reader/ReaderViewModel.kt) and [`ReaderUiState.kt:9`](../../app/src/main/java/com/bifel/voxreader/presentation/reader/ReaderUiState.kt)).

## Findings (priority order)

### P0 — split the reader coordinator before adding more reader features

**Evidence.** `ReaderViewModel` has nine constructor dependencies ([`ReaderViewModel.kt:42-52`](../../app/src/main/java/com/bifel/voxreader/presentation/reader/ReaderViewModel.kt)); subscribes to settings, both engines, voice packs, and service commands in its initializer ([`ReaderViewModel.kt:67-115`](../../app/src/main/java/com/bifel/voxreader/presentation/reader/ReaderViewModel.kt)); directly manages history, phrase buffering, playback sessions, UI state, persistence, imports, and previews. `ReaderScreen` has 1,817 lines and contains reader layout, scrolling/pagination, drawer/settings, progress navigation, playback controls, engine/model dialogs, and a character editor (for example [`ReaderScreen.kt:683-1071`](../../app/src/main/java/com/bifel/voxreader/presentation/reader/ReaderScreen.kt) and [`ReaderScreen.kt:1453-1742`](../../app/src/main/java/com/bifel/voxreader/presentation/reader/ReaderScreen.kt)).

**Why it matters.** This violates SRP and makes `ReaderViewModel` the accidental owner of both product workflow and platform delivery mechanics. It has no focused unit tests; most current tests target pure utility classes. Any new concern expands a class that is already hard to reason about.

**Refactor.** Keep one screen-level view model initially, but extract three collaborators with narrow responsibilities:

1. `ReaderPlaybackController`: phrase queue/buffer, active engine selection, events, playback session cancellation, and navigation to the next phrase.
2. `ReaderAnalysisCoordinator`: import/cache/load analysis and apply voice overrides.
3. `ReaderNavigationController` (or a small pure state reducer): page offsets and back/forward history.

The view model should translate UI actions into calls and compose their state into `ReaderUiState`; it should not know implementation details of a foreground service or voice-pack download. Split the Compose file by screen region (reader content, navigation, settings, voice management) only after the contracts are stable.

### P0 — restore a presentation/application boundary around Android and Piper implementations

**Evidence.** The view model calls `MediaPlaybackService.stop()` and collects its process-global `commands` flow ([`ReaderViewModel.kt:105-114,164-169`](../../app/src/main/java/com/bifel/voxreader/presentation/reader/ReaderViewModel.kt)). It accepts `PiperVoicePackManager` and `PortableAnalysisService` concrete data-layer classes ([`ReaderViewModel.kt:50-51`](../../app/src/main/java/com/bifel/voxreader/presentation/reader/ReaderViewModel.kt)); the UI state exposes `data.speech.PiperVoicePack` ([`ReaderUiState.kt:33-34`](../../app/src/main/java/com/bifel/voxreader/presentation/reader/ReaderUiState.kt)).

**Why it matters.** View-model tests require Android-adjacent implementations or substantial mocking, and storage/download/service changes necessarily affect presentation types. The static shared-flow service bridge also has implicit process/lifecycle semantics and no clear owner.

**Refactor.** Define small application/domain-facing contracts, implemented in `data`: e.g. `PlaybackNotificationController`, `VoicePackRepository`, and `AnalysisRepository`/`AnalysisImporter`. Use presentation-facing `VoicePackUiModel`, not the data entity. This is dependency inversion with a concrete payoff; do not introduce interfaces for pure helpers that are not swapped or tested.

### P1 — separate policy/state from transport in the voice-pack feature

**Evidence.** `PiperVoicePackManager` owns a 46-entry product catalog, remote URL construction, HTTP transport, progress/UI states, filesystem layout, installation marker, deletion, and user-facing descriptions ([`PiperVoicePackManager.kt:22-128`](../../app/src/main/java/com/bifel/voxreader/data/speech/PiperVoicePackManager.kt), [`PiperVoicePackManager.kt:132-205`](../../app/src/main/java/com/bifel/voxreader/data/speech/PiperVoicePackManager.kt)). `SherpaPiperSpeechEngine` then depends on that manager’s model path/selection.

**Why it matters.** It has multiple reasons to change (catalog curation, download robustness, storage policy, UI wording, model/runtime compatibility), and tests for any one concern require the others. It also makes a large catalog a code change.

**Refactor.** Retain the current local implementation, but split immutable `VoicePackCatalog` from a `VoicePackStore` (files/install state) and `VoicePackDownloader` (HTTP). Keep the public facade if it avoids churn. Use a `VoicePack` domain/application model and map download-specific state at the edge. Test installs/deletions with a temporary filesystem and downloads behind a tiny stream/client abstraction.

### P1 — distinguish active product scope from staged/future analysis machinery

**Evidence.** The README describes manual portable JSON as the POC and calls overnight/cloud processing future work ([`README.md`](../../README.md)). The runtime supports import and cache lookup, but no UI invokes `PortableAnalysisService.export`; repository search finds its `export` method only at its definition. Room nevertheless models runs with `PREPARING`, `READY`, `PARTIAL`, `FAILED`, cancellation, provider/model IDs, four version fields, staging, activation and migrations ([`PreAnalysisEntities.kt:14-98`](../../app/src/main/java/com/bifel/voxreader/data/database/entity/PreAnalysisEntities.kt), [`RoomPreAnalysisStore.kt:43-160`](../../app/src/main/java/com/bifel/voxreader/data/preanalysis/RoomPreAnalysisStore.kt)).

**Assessment.** The imported sidecar schema and its strict validation are justified: externally supplied input needs stable identity, bounds checks, and clear errors. The internal multi-run/provider lifecycle is partly anticipatory while only one import producer is active.

**Refactor decision.** Choose and document one of two paths before expanding it:

- **POC path (recommended until a second producer ships):** remove/avoid exposing unused export and defer cancellation/provider-run features; keep an active imported-analysis snapshot plus manifest/attributions/characters.
- **Multi-provider path:** keep the revision model, but add a real provider port, an explicit run-state UI, cancellation, and integration tests for replacing an active revision.

Do not retain a half-implemented workflow solely for a possible cloud feature.

### P1 — make persistence ownership explicit; avoid two authoritative stores for voice profiles

**Evidence.** `updateCharacterVoice` optimistically mutates state and writes the same profile to both `LibraryRepository` and `PortableAnalysisService` ([`ReaderViewModel.kt:237-253`](../../app/src/main/java/com/bifel/voxreader/presentation/reader/ReaderViewModel.kt)). `StoredBook.voiceOverrides` is JSON in DataStore, while analysis characters live in Room. A failed second write can leave the two copies divergent; neither operation is coordinated transactionally.

**Refactor.** Declare one source of truth for user voice overrides. The smallest change is to keep per-book user overrides in the library store and treat Room character entries as imported analysis only; merge them when projecting the reader model. Alternatively, persist all character/override data in Room and remove the DataStore duplication. Add a failure/reload test for the chosen invariant.

### P2 — remove or reduce thin abstractions that have no current caller value

**Evidence.** `LibraryRepository.library` is declared ([`ReaderRepositories.kt:17-25`](../../app/src/main/java/com/bifel/voxreader/domain/repository/ReaderRepositories.kt)) but has no production reference. `PortableAnalysisService.export` likewise has no production caller. `ImportBookUseCase` and `LoadLatestBookUseCase` are each thin two-operation wrappers ([`ReaderUseCases.kt:8-29`](../../app/src/main/java/com/bifel/voxreader/domain/usecase/ReaderUseCases.kt)).

**Refactor.** Delete unused public API after confirming no planned consumer, or add the intended consumer in the same change. Keep the two use cases only if the team considers their orchestration boundary valuable; otherwise fold each into a clearly named reader-library application service. Do not create additional one-method use-case classes by default.

### P2 — improve test coverage where behavior is currently stateful or platform-coupled

**Evidence.** Existing unit tests are a good start for parser, segmenter, sidecar JSON, speech text/delivery/buffer, page navigation, and phrase lookup. There are no unit tests for `ReaderViewModel`, `ReaderDataStoreRepository`, `RoomPreAnalysisStore`, `PortableAnalysisService`, `PiperVoicePackManager`, either speech engine, or `MediaPlaybackService` (test source inventory under [`app/src/test`](../../app/src/test)).

**Refactor.** First add tests alongside the P0 extraction rather than trying to mock the current 879-line view model. Cover: stale speech event rejection/session cancellation; page-history invariants; analysis import then override persistence; invalid/missing voice pack behavior; and replacing an active analysis revision. Add one instrumented test for URI EPUB open and media-service notification lifecycle when practical.

## What should remain as-is for now

- **Manual `AppContainer`.** It is readable and appropriate for one module. ([`AppContainer.kt:16-30`](../../app/src/main/java/com/bifel/voxreader/AppContainer.kt))
- **Small pure helper objects.** `SentenceSegmenter`, `PhraseLookup`, `PageNavigation`, `PageCalculator`, `SpeechText`, `EmotionDelivery`, and `PhrasePlaybackBuffer` are cohesive, have focused tests, and are lower-risk than a generic “utility” layer.
- **Strict sidecar validation.** The size cap, schema/version checks, immutable phrase-identity checks, range/coverage validation, and enum validation in [`AnalysisSidecarJson.kt:69-234`](../../app/src/main/java/com/bifel/voxreader/domain/preanalysis/sidecar/AnalysisSidecarJson.kt) are proportional to an external-file trust boundary.
- **File-import copy and SHA-256 identity.** [`LocalBookRepository.kt:18-55`](../../app/src/main/java/com/bifel/voxreader/data/epub/LocalBookRepository.kt) sensibly takes ownership of document availability offline and cleans its temporary import file on failure.
- **No file-picker feature.** The README’s Android “Open with” decision is a valid POC YAGNI choice; do not add library browsing until the product actually needs it.

## Prioritized delivery plan

1. **Stabilise behavior (P0).** Characterise existing playback and navigation with tests; introduce a `PlaybackNotificationController` and replace direct `MediaPlaybackService` access. Extract `ReaderPlaybackController` without changing user-visible behavior.
2. **Clarify state ownership (P0/P1).** Choose the canonical store for user voice overrides; remove the duplicate write path or make the projection/reconciliation explicit. Add reload/failure tests.
3. **Uncouple feature edges (P1).** Replace data-layer types in `ReaderUiState` and concrete manager/service dependencies in the view model with narrow application contracts. Keep manual composition in `AppContainer`.
4. **Right-size analysis (P1).** Decide whether the app is currently import-only or genuinely multi-provider. Prune dead export/run features for the former, or complete provider/run lifecycle and tests for the latter.
5. **Split UI by responsibility (P2).** Once callbacks become feature-level actions instead of broad view-model methods, split `ReaderScreen` into reader, chrome/navigation, settings, and voice-management files. Preserve private functions until reuse is demonstrated.
6. **Modularise only on evidence (later).** Consider Gradle modules only if build time, ownership, or independent feature delivery requires them. The current codebase does not justify `:domain`, `:data`, and `:feature-reader` modules yet.

## Acceptance criteria for the refactor

- `ReaderViewModel` no longer imports a concrete service or data implementation and has a small, feature-oriented dependency set.
- Playback/session rules and navigation history are testable without Android framework objects.
- One documented store owns user voice overrides; imports cannot leave two stores silently divergent.
- The app has either an active, tested multi-provider analysis workflow or a smaller import-only model with no unused public export/run lifecycle.
- `ReaderScreen` is divided along feature boundaries without a generic UI abstraction layer.
- `gradlew.bat testDebugUnitTest` remains green (it passed at review time).
