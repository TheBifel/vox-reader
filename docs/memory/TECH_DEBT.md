---
memory_type: technical_debt
authority: canonical
last_verified: 2026-08-10
update_when: debt is introduced, removed, reprioritized, or its trigger changes
---

# Technical debt and refactor triggers

## Priority 1: integration coverage

Unit tests cover core parsing/mapping/post-processing behavior, but end-to-end coverage is limited for URI opening, vertical auto-follow, background playback, model downloads, and sidecar import against real books. Prefer small deterministic tests plus emulator smoke tests; never commit copyrighted fixtures.

## Priority 2: neural packaging and lifecycle

The sherpa-onnx AAR makes the APK large because it includes multiple ABIs. Consider ABI splits or a narrower runtime only when distribution size becomes a real requirement. Continue testing cancellation, rapid engine switching, screen lock, model deletion, and low-memory recovery when changing neural lifecycle code.

## Priority 3: parser breadth

EPUB/FB2 support intentionally handles common content, links, chapters, and images rather than full publication CSS/DRM/table semantics. Expand only for an observed book failure and keep canonical offset mapping intact.

## Deferred product work

Whole-book overnight analysis and opt-in paid cloud analysis are designed but not implemented. Library management, search, bookmarks, annotations, and richer footnotes remain candidates, not commitments.
