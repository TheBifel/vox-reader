---
memory_type: decisions
authority: canonical
last_verified: 2026-08-10
update_when: a durable product or architecture choice is made or reversed
---

# Durable decisions

## Offline-first playback

Speech runs locally. Voice cloning is unnecessary; a small catalog of stable default voices with per-character pitch/rate customization is sufficient. Neural weights are downloaded on demand rather than bundled.

## Imported attribution instead of live inference

The attempted live lightweight-model path produced latency and unreliable speaker changes. VoxReader therefore consumes pre-recognized JSON and performs no live speaker inference. Attribution may be generated manually, on desktop, overnight on-device, or by an explicitly authorized cloud tool, but all producers share one validated sidecar contract.

## JSON is the interchange format

TSV was retired. JSON carries immutable phrase identity, character profiles, optional traits, and optional emotions. The app imports but does not export it. App validation is authoritative.

## Reserved speaker identities

`narrator` and `unknown` are hard-coded system IDs. They are not discovered character names. All book characters are dynamic. Without a map, only Narrator exists in playback and voice settings.

## Canonical text and position

The canonical book text is not rewritten for paragraph spacing or display cleanup. Rendering builds mappings between displayed text and canonical UTF-16 offsets. Position persistence uses offsets, not page numbers.

## Reader performance

Avoid parsing/layout work for an entire book on every open. Render and paginate around the viewport. Speech generation runs ahead: neural PCM retains a bounded window of upcoming and recent phrases. A spinner communicates genuine preparation or buffer starvation.

## Three interchangeable engines

Procedural Android TTS, Piper, and KittenTTS Micro implement a common speech contract. Voice mode is a reader control; engine selection and model management live in settings. Engine-specific UI may adapt after selection, but reading/navigation logic must not.

## Emotion is optional and engine-independent

Sidecar emotion is optional. A disabled-by-default post-processing layer maps controlled emotions to configurable delivery profiles. The same setting applies to procedural and neural engines, with best-effort differences documented rather than hidden.

## Device policy

Use the emulator during implementation. Install on the real Samsung only after an explicit user request.
