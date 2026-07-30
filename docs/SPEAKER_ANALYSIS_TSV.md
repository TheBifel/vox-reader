# Portable speaker-analysis TSV

## Purpose

VoxReader uses one portable, versioned TSV file as the exchange format for whole-book speaker
attribution. The source of the analysis does not matter to the reader:

- a paid cloud model can fill the file;
- a local desktop tool can fill it;
- the future overnight phone worker can fill it;
- a person can edit it manually.

All four paths produce the same data model. Importing the result creates an immutable analysis
revision in the phone's Room database and makes it the active revision for that book.

## User workflow

1. Open the EPUB in VoxReader.
2. Open the reader drawer and choose **Export speaker analysis TSV**.
3. Send the exported file to an analysis tool, or edit it manually.
4. Keep the identity and text columns unchanged. Fill the speaker and voice columns.
5. In VoxReader choose **Import speaker analysis TSV** and select the result.
6. VoxReader validates the whole file before activating it.

Known cached speakers immediately appear as different text highlight colors. The same stable
speaker ID always receives the same color throughout the book. Narration and unknown phrases have
no speaker color. During playback, the currently spoken text uses the stronger playback highlight
on top of the speaker color.

## File shape

The file is UTF-8 text with:

1. versioned `# key=value` metadata;
2. one tab-separated header;
3. one row per speech phrase.

Example:

```tsv
# voxreader_speaker_analysis_tsv=1
# book_id=f4eb5e...
# provider_id=claude-haiku-4-5-20251001
# created_at=1785448943709
# manifest_version=1
# segmentation_version=2
# attribution_policy_version=1
# provider_schema_version=1
phrase_id	phrase_index	start	end	text_hash	text	speaker_id	speaker_name	voice_group	voice_slot	pitch	speech_rate	confidence	evidence	source
7c10...	158	8863	8881	fd09...	“What about Gold?”	lindon	Lindon	MALE	1	0.94	1.0	0.99	CONTEXTUAL	PREANALYSIS_CACHE
7dc3...	159	8882	8899	ba62...	he asked eagerly.	narrator	Narrator	NARRATOR	0	1.0	0.96	1.0	EXPLICIT_TAG	PREANALYSIS_CACHE
```

Tabs, line breaks, carriage returns, and backslashes inside values are escaped as `\t`, `\n`,
`\r`, and `\\`.

## Metadata

| Key | Meaning |
|---|---|
| `voxreader_speaker_analysis_tsv` | File-format version. |
| `book_id` | SHA-256 identity of the imported EPUB. Prevents applying analysis to another book. |
| `provider_id` | Human-readable producer/model identifier shown in the app. |
| `created_at` | Unix epoch milliseconds. |
| `manifest_version` | Phrase-identity algorithm version. |
| `segmentation_version` | Sentence/dialogue segmentation version. |
| `attribution_policy_version` | Meaning of speaker attribution fields. |
| `provider_schema_version` | Contract version expected from analysis providers. |

VoxReader rejects unsupported or mismatched versions instead of silently shifting assignments.

## Row columns

| Column | Editable | Meaning |
|---|---:|---|
| `phrase_id` | No | Stable SHA-256 phrase identity. |
| `phrase_index` | No | Phrase order in the current segmentation. |
| `start` | No | UTF-16 start offset in the extracted book text. |
| `end` | No | Exclusive UTF-16 end offset. |
| `text_hash` | No | Hash of normalized phrase text. |
| `text` | No | Original phrase text, included for analysis and debugging. |
| `speaker_id` | Yes | Stable identity such as `lindon`, `yerin`, or `narrator`. |
| `speaker_name` | Yes | Display name used in the reader legend. |
| `voice_group` | Yes | `NARRATOR`, `MALE`, `FEMALE`, or `NEUTRAL`. |
| `voice_slot` | Yes | Deterministic voice variation within a group. |
| `pitch` | Yes | TTS pitch in the accepted range `0.5..1.6`. |
| `speech_rate` | Yes | TTS rate in the accepted range `0.5..1.5`. |
| `confidence` | Yes | Attribution confidence in `0..1`. |
| `evidence` | Yes | Why the assignment was made. |
| `source` | Yes | Where the assignment came from. |

Use `unknown / Unknown / NEUTRAL / confidence 0` for unresolved phrases. Unknown rows remain in
the portable file but are not activated as cached assignments, so a partial file is supported.

## Speaker identity rules

- One character must keep one `speaker_id` throughout the book.
- Aliases such as “Lindon,” “Wei Shi Lindon,” and pronouns resolve to the same ID.
- The same `speaker_id` must have one consistent display name and voice definition in a file.
- Narrative tags such as `Lindon said` are assigned to `narrator`, not to Lindon.
- Only the quoted or otherwise spoken span receives the character's ID.
- Use `narrator` for narration and `unknown` when context is insufficient.

## Allowed evidence values

- `USER_LOCKED`
- `EXPLICIT_TAG`
- `QUOTE_CONTINUATION`
- `TURN_PATTERN`
- `CONTEXTUAL`
- `UNKNOWN`

## Allowed source values

- `USER_LOCKED`
- `PREANALYSIS_CACHE`
- `DETERMINISTIC`
- `TINY_BERT`
- `UNKNOWN`

An outsourced or manually prepared result should normally use `PREANALYSIS_CACHE`.

## Import validation and activation

Before writing an active revision, VoxReader checks:

- exact book ID;
- all contract versions;
- unique phrase IDs and phrase indexes;
- exact index, offset, and text-hash match against the current EPUB;
- optional embedded text match;
- valid enum and numeric ranges;
- consistent voice definition for every repeated speaker ID.

Only after validation succeeds does VoxReader stage and atomically activate the new revision. A
failed import leaves the previous active revision untouched.

## Runtime behavior

At book load, VoxReader rebuilds the current stable phrase manifest and looks up active cached
assignments by phrase ID. Cached assignments:

- replace the in-memory phrase speaker;
- load their character voice profiles;
- bypass live TinyBERT/rule analysis;
- drive deterministic speaker colors;
- drive TTS voice selection.

Unresolved phrases can still use the existing live fallback until the whole-book providers replace
it. The future cloud and overnight implementations should both call this same import/staging path
rather than introducing provider-specific reader logic.
