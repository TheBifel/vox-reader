---
memory_type: state
authority: canonical
last_verified: 2026-08-10
update_when: a checkpoint is committed, validation changes, blockers appear, or next work changes
---

# Current state

## Checkpoint

- Branch: `main`
- Remote: `origin` (`TheBifel/vox-reader`)
- Latest product checkpoint at this review: `42294f0 feat: add configurable speech post-processing`
- Previous stable advanced-speech checkpoint: `5a183e1 feat: stabilize buffered advanced TTS`
- Unit tests passed immediately before the memory cleanup. Full test/lint/assemble validation must be rerun after cleanup.

## Working product

EPUB/FB2 reading, restored position, horizontal/vertical navigation, TOC, progress, links/history, appearance controls, sleep timer, speaker-map import, character colors/settings, narrator-only fallback, foreground media playback, three local speech modes, download-on-demand voices, neural lookahead buffering, and optional emotion post-processing are implemented.

AI Advanced/Kitten with buffering performed well on the Samsung Galaxy S21. The current post-processing feature is disabled by default and supports 17 emotion profiles. Existing local test maps may contain no emotion annotations, so synthetic/unit fixtures are required to exercise that path.

## Known limitations

- `ReaderScreen` and `ReaderViewModel` remain large integration points; see [TECH_DEBT.md](TECH_DEBT.md).
- App import is the authoritative sidecar check; direct desktop EPUB segmentation can still differ.
- Android TTS cannot reproduce every neural post-processing dimension exactly.
- EPUB styling, complex images/tables, full footnote UX, library management, search, bookmarks, annotations, DRM, and whole-book analysis are incomplete or out of scope.
- The APK is relatively large because the tracked sherpa AAR contains multiple ABIs.

## Next-work guidance

Do not start a broad architecture rewrite. When the next feature would add responsibility to `ReaderViewModel` or `ReaderScreen`, first extract that feature's coherent coordinator/UI section. Deferred local overnight and opt-in cloud analysis remain documented in [../plans/WHOLE_BOOK_ANALYSIS.md](../plans/WHOLE_BOOK_ANALYSIS.md), not implemented.

## Local-only fixtures

The ignored `test-data/` directory contains two user-supplied book/map pairs used for emulator testing. They are intentionally absent from git and must not be copied into tracked tests or docs.
