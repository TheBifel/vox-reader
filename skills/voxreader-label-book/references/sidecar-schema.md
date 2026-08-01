# VoxReader speaker-analysis JSON v1

The UTF-8 file is one JSON object:

```json
{
  "format": "voxreader-speaker-analysis",
  "schema_version": 1,
  "book_id": "<SHA-256 EPUB id>",
  "provider_id": "<producer>",
  "created_at": 1785448943709,
  "manifest_version": 1,
  "segmentation_version": 2,
  "attribution_policy_version": 1,
  "provider_schema_version": 1,
  "characters": [
    {
      "speaker_id": "lindon",
      "speaker_name": "Lindon",
      "aliases": ["Wei Shi Lindon"],
      "voice_group": "MALE",
      "voice_slot": 1,
      "pitch": 0.96,
      "speech_rate": 1.0,
      "traits": {
        "age_band": "YOUNG",
        "gender_presentation": "MASCULINE",
        "vocal_register": "MEDIUM"
      }
    }
  ],
  "phrases": [
    {
      "phrase_id": "<exact phrase id>",
      "phrase_index": 0,
      "start": 0,
      "end": 12,
      "text_hash": "<normalized text hash>",
      "text": "<original phrase>",
      "speaker_id": "narrator",
      "speaker_name": "Narrator",
      "voice_group": "NARRATOR",
      "voice_slot": 0,
      "pitch": 1.0,
      "speech_rate": 0.96,
      "confidence": 1.0,
      "evidence": "CONTEXTUAL",
      "source": "PREANALYSIS_CACHE",
      "emotion": {
        "type": "TENSION",
        "intensity": 0.7,
        "confidence": 0.9
      }
    }
  ]
}
```

JSON escaping handles tabs, line breaks, quotes, and backslashes. Numeric fields must be JSON numbers, not quoted strings.

## Immutable fields

The producer must copy these fields unchanged from the app-exported template:

- `phrase_id`
- `phrase_index`
- `start`
- `end`
- `text_hash`
- `text`

The app validates them against the currently opened EPUB. A textually similar book edition is not interchangeable.

## Attribution fields

- `speaker_id`: stable ID, maximum 128 characters; `narrator` and `unknown` are reserved system IDs.
- `speaker_name`: nonempty display name, maximum 160 characters.
- `voice_group`: `NARRATOR`, `MALE`, `FEMALE`, or `NEUTRAL`.
- `voice_slot`: integer selecting a default voice variation.
- `pitch`: `0.5..1.6`.
- `speech_rate`: `0.5..1.5`.
- `confidence`: `0..1`.
- `evidence`: `USER_LOCKED`, `EXPLICIT_TAG`, `QUOTE_CONTINUATION`, `TURN_PATTERN`, `CONTEXTUAL`, or `UNKNOWN`.
- `source`: generated model results use `PREANALYSIS_CACHE`; unresolved rows use `UNKNOWN`.
- `emotion`: optional object containing controlled `type`, `intensity`, and emotion-specific
  `confidence`. Omit it unless confidence is at least `0.85`; never emit a neutral value.

All rows for one non-unknown speaker ID must repeat an identical name and voice profile.
Emotion is phrase-specific and may vary between phrases from the same speaker.

## Reserved system speakers

These are hard-coded identities in both the generator contract and VoxReader. They are not dynamic
book characters and must not be renamed, aliased, or assigned traits:

| `speaker_id` | Fixed name | Voice group | Slot | Pitch | Rate | Attribution fields |
| --- | --- | --- | ---: | ---: | ---: | --- |
| `narrator` | `Narrator` | `NARRATOR` | 0 | 1.0 | 0.96 | Normal evidence/source rules apply. |
| `unknown` | `Unknown` | `NEUTRAL` | 0 | 1.0 | 1.0 | `confidence: 0.0`, `evidence: UNKNOWN`, `source: UNKNOWN`. |

All other `speaker_id` values are book-derived and dynamic. The importer rejects a sidecar that
attempts to redefine either system profile.

## Optional character registry

`characters` is an optional top-level array. It contains one canonical profile per known speaker and
is used as a stable source of voice-selection cues; it does not replace phrase-level attribution.
If a listed speaker also appears in `phrases`, its identity and voice profile must exactly match the
phrase rows. `aliases` may be empty. `traits` is optional, but when present it contains all three
controlled fields:

- `age_band`: `CHILD`, `YOUNG`, `ADULT`, `MATURE`, `ELDER`, or `UNKNOWN`
- `gender_presentation`: `FEMININE`, `MASCULINE`, `ANDROGYNOUS`, or `UNKNOWN`
- `vocal_register`: `LOW`, `MEDIUM`, `HIGH`, or `UNKNOWN`

These are evidence-backed voice-selection cues, not free-text descriptions, diagnoses, or claims
about a real person. Use `UNKNOWN` for missing evidence. The app preserves them for future
automatic voice recommendations while leaving the reader's manual voice choice in control.

`character_profile` is also optional. When present, it contains a compact, evidence-backed
description used by reviewers as a weak tie-breaker, never as a replacement for textual evidence:

```json
"character_profile": {
  "summary": "A restrained young observer who avoids direct conflict.",
  "speech_style": {
    "register": "FORMAL",
    "verbosity": "TERSE",
    "typical_tones": ["CALM", "CAUTIOUS"],
    "markers": ["short replies", "formal forms of address"]
  },
  "evidence_phrase_ids": ["<phrase id>"]
}
```

Allowed `register` values are `FORMAL`, `NEUTRAL`, `INFORMAL`, `MIXED`, and `UNKNOWN`; allowed
`verbosity` values are `TERSE`, `BALANCED`, `VERBOSE`, `MIXED`, and `UNKNOWN`. `typical_tones`
use the controlled emotion values plus `CALM`, `CAUTIOUS`, `SARCASTIC`, `DRY`, and `UNKNOWN`. Include at
least one supporting phrase ID and at most eight short `markers`. Do not infer a profile from a
name, gender, or a single unsupported line.

## Completeness

A final sidecar should include every template phrase exactly once. Unknown phrase objects are valid and allow partial attribution without corrupting phrase alignment.
