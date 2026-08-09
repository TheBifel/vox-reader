# Portable speaker-analysis JSON

## Purpose

VoxReader uses one portable, versioned JSON document as the exchange format for whole-book
speaker attribution. A paid cloud model, local desktop tool, future overnight phone worker, or
person can produce the same data model.

Importing a valid result creates an immutable analysis revision in Room and makes it active for
that book. The reader does not depend on the producer.

## User workflow

1. Open the EPUB in VoxReader.
2. Produce a sidecar from the exact book outside the app, normally with the repository's
   `voxreader-label-book` skill, or obtain a compatible sidecar from another analysis tool.
3. Preserve the immutable book and phrase identity fields while assigning speakers and optional
   emotions.
4. Choose **Import character map** in the reader drawer and select the JSON.
5. VoxReader validates the complete document before activating it.

Known cached speakers immediately receive stable per-character highlight colors. Narration remains
uncolored; unresolved `unknown` phrases receive a stable diagnostic color so gaps in the external
analysis are visible on the page. The active playback highlight is drawn more strongly.

The book-labeling tool can create the JSON directly from the exact EPUB file. If its
book hash and immutable phrase data validate, VoxReader adopts that JSON phrase manifest for the
book. This handles legitimate desktop/Android sentence-boundary differences without guessing or
discarding assignments.

VoxReader currently has no sidecar export action. A sidecar prepared directly from EPUB is
provisional until it passes the app's import validator; that validator is the authoritative
compatibility check.

An imported JSON is authoritative. Every row, including `narrator` and `unknown`, is considered a
completed decision. VoxReader does not run live attribution; it renders cached speaker colors and
reuses cached voice profiles. Without an imported map, playback uses the Narrator profile.

## Document shape

The file is a UTF-8 JSON object with versioned metadata and a `phrases` array:

```json
{
  "format": "voxreader-speaker-analysis",
  "schema_version": 1,
  "book_id": "f4eb5e...",
  "provider_id": "claude-haiku-4-5-20251001",
  "created_at": 1785448943709,
  "manifest_version": 1,
  "segmentation_version": 2,
  "attribution_policy_version": 1,
  "provider_schema_version": 1,
  "phrases": [
    {
      "phrase_id": "7c10...",
      "phrase_index": 158,
      "start": 8863,
      "end": 8881,
      "text_hash": "fd09...",
      "text": "\"What about Gold?\"",
      "speaker_id": "lindon",
      "speaker_name": "Lindon",
      "voice_group": "MALE",
      "voice_slot": 1,
      "pitch": 0.94,
      "speech_rate": 1.0,
      "confidence": 0.99,
      "evidence": "CONTEXTUAL",
      "source": "PREANALYSIS_CACHE",
      "emotion": {
        "type": "EXCITEMENT",
        "intensity": 0.7,
        "confidence": 0.91
      }
    }
  ]
}
```

Strings use standard JSON escaping. Integer and decimal fields are JSON numbers, not strings.

## Metadata

| Key | Meaning |
|---|---|
| `format` | Constant `voxreader-speaker-analysis`. |
| `schema_version` | File-format version. |
| `book_id` | SHA-256 identity of the imported EPUB. |
| `provider_id` | Producer/model identifier shown in the app. |
| `created_at` | Unix epoch milliseconds. |
| `manifest_version` | Phrase-identity algorithm version. |
| `segmentation_version` | Sentence/dialogue segmentation version. |
| `attribution_policy_version` | Meaning of speaker attribution fields. |
| `provider_schema_version` | Contract version expected from providers. |

VoxReader rejects unsupported or mismatched versions.

## Phrase fields

| Field | Editable | Meaning |
|---|---:|---|
| `phrase_id` | No | Stable SHA-256 phrase identity. |
| `phrase_index` | No | Phrase order in the current segmentation. |
| `start` | No | UTF-16 start offset in extracted book text. |
| `end` | No | Exclusive UTF-16 end offset. |
| `text_hash` | No | Hash of normalized phrase text. |
| `text` | No | Original phrase text used for analysis. |
| `speaker_id` | Yes | Stable identity such as `lindon`, `yerin`, or `narrator`. |
| `speaker_name` | Yes | Display name shown in the reader legend. |
| `voice_group` | Yes | `NARRATOR`, `MALE`, `FEMALE`, or `NEUTRAL`. |
| `voice_slot` | Yes | Deterministic variation within a voice group. |
| `pitch` | Yes | TTS pitch in `0.5..1.6`. |
| `speech_rate` | Yes | TTS rate in `0.5..1.5`. |
| `confidence` | Yes | Attribution confidence in `0..1`. |
| `evidence` | Yes | Why the assignment was made. |
| `source` | Yes | Where the assignment came from. |
| `emotion` | Yes | Optional object containing `type`, `intensity`, and `confidence`. |

Use `unknown`, `Unknown`, `NEUTRAL`, and confidence `0` for unresolved phrases. Unknown objects
remain authoritative cached decisions and are displayed with a stable diagnostic color distinct
from uncolored narration.

Emotion `intensity` is in `0..1`, and emotion `confidence` must be in `0.85..1`. The accepted
emotion types are `JOY`, `SADNESS`, `ANGER`, `FEAR`, `SURPRISE`, `DISGUST`, `TENSION`,
`EXCITEMENT`, `CURIOSITY`, `CONFIDENCE`, `AFFECTION`, `SHAME`, `GRIEF`, `AMUSEMENT`,
`FRUSTRATION`, `RELIEF`, and `DETERMINATION`.

Voice post-processing is optional and disabled by default. When enabled, every accepted emotion
uses an editable profile containing speed, pitch, loudness, warm/bright tone, and the pause after
the phrase. Each value is interpolated from neutral using `intensity`; emotion confidence below
`0.85` is ignored defensively. Procedural Android voices apply the controls supported by the
platform engine. Piper and Kitten voices additionally process generated PCM for tone, loudness,
and duration-preserving pitch changes before it enters the playback cache.

The current labeler may include an optional per-character `character_profile` in the top-level
`characters` registry. VoxReader accepts this reviewer-only context, but it never overrides
phrase-level attribution or the reader's manual voice selection.

## Identity and attribution rules

- Keep one `speaker_id` and voice definition per character throughout the book.
- Resolve aliases and titles to the same stable identity only when supported by context.
- Assign narrative tags such as `Lindon said` to `narrator`, not Lindon.
- Assign only the quoted or otherwise spoken span to the character.
- Use `narrator` for narration and `unknown` when context is insufficient.

Allowed evidence values are `USER_LOCKED`, `EXPLICIT_TAG`, `QUOTE_CONTINUATION`,
`TURN_PATTERN`, `CONTEXTUAL`, and `UNKNOWN`.

Allowed source values are `USER_LOCKED`, `PREANALYSIS_CACHE`, `DETERMINISTIC`, `TINY_BERT`,
and `UNKNOWN`. Outsourced results normally use `PREANALYSIS_CACHE`.

## Import validation

Before activation VoxReader checks:

- exact book ID and contract versions;
- unique phrase IDs and indexes;
- contiguous phrase order, valid non-overlapping offsets, and complete extracted-book coverage;
- phrase IDs and text hashes recomputed from the immutable row data;
- embedded text match;
- valid enum and numeric ranges; and
- one consistent voice definition for each speaker ID.

Only after validation succeeds does VoxReader atomically activate the revision. A failed import
leaves the previous active revision untouched.
