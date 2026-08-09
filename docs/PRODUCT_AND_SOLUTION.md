# VoxReader: product and high-level solution

The whole-book speaker-analysis exchange format is documented in
[`SPEAKER_ANALYSIS_JSON.md`](SPEAKER_ANALYSIS_JSON.md). It is the shared contract for manual,
cloud/outsourced, desktop, and future overnight analysis. Once imported, its phrase manifest and
speaker decisions are authoritative. The reader does not perform live speaker attribution.

## What the app does

VoxReader is an offline-first Android EPUB reader designed around synchronized read-aloud
playback. The reader displays a book as fixed pages, speaks the text currently being read, and
highlights the active text as speech progresses. Dialogue is associated with recurring fictional
characters, and each character receives a stable local voice variation.

The proof of concept deliberately avoids voice cloning, accounts, and inference inside the reader.
Speaker decisions arrive through a portable whole-book JSON file produced manually, on a desktop,
or by an optional external service. Playback then stays offline and uses voices already available
in Android's local text-to-speech engine. Characters are differentiated through deterministic
voice selection, pitch, and speaking-rate profiles. Imported phrase emotions can temporarily
modify that profile while a phrase is spoken.

The long-term goal is a private audiobook-like reading experience that:

- works without an internet connection;
- begins playback quickly instead of rendering an entire audiobook first;
- remembers the last book and reading position;
- keeps a character's assigned voice stable across the book;
- remains usable on an ordinary Android phone; and
- allows the analysis and speech implementations to improve without replacing the reader.

## Core user experience

### Importing and reopening books

A new book is opened from Android's Files app or another file manager by tapping an `.epub` file
and choosing VoxReader. The app copies the source file into its private application storage, parses
the reading order, and remembers the imported book in DataStore.

Launching VoxReader normally reopens the most recently used book at the saved page position.
There is intentionally no in-app library browser or file picker in this POC. Without an imported
speaker-analysis JSON, all phrases use the Narrator profile.

### Reading

The EPUB text is laid out into viewport-sized pages. Only the visible page is measured, with the
previous and next pages prepared as adjacent cache entries. VoxReader never lays out the entire
book during opening or when speech controls change the viewport. The stored position is a
character offset rather than a fragile page number, allowing the page to be reconstructed after a
screen-size or orientation change.

The tap zones follow conventional left-to-right navigation:

- tap the left third to move to the previous page;
- tap the right third to move to the next page;
- tap the middle third to reveal or hide the reader header;
- use the header's hamburger button to open reader settings.

The bottom progress control maps the saved character offset to an estimated page number using the
measured size of the current viewport. This is intentionally an estimate, like established EPUB
readers, and avoids a blocking whole-book pagination pass. It shows the current percentage on the
left and current/estimated-total pages on the right. Dragging seeks through text offsets, while
short perpendicular markers map EPUB spine/chapter offsets onto the same scale. The control keeps
a 16 dp margin from both screen edges and updates when the available page size changes.

Imported speaker colors are also viewport-local: binary search selects only phrases overlapping
the visible page. Narration remains uncolored. An unresolved cached `unknown` phrase receives its
own stable color so incomplete external analysis is visible and debuggable instead of looking like
narration.

### Appearance and speech controls

The drawer contains:

- a dark/light reader theme switch; and
- a command to enable or hide text-to-speech controls;
- the active speaker-map status; and
- an editable Narrator and character list with voice group, variation, pitch, and base-rate
  controls.

The drawer closes by tapping its shaded outside area, swiping it away, or pressing Android Back.

The dark theme uses a near-black background and warm white text. The light theme reverses that
relationship with a warm paper background and dark text.

When speech controls are enabled, previous-sentence, play/pause, and next-sentence controls appear
above the reading progress bar. A speed control above them adjusts global playback from `0.5x` to
`2.0x` and is remembered across launches. Pressing play starts from the first speakable sentence
visible on the current page. During initial speech preparation, the play button becomes a loader.
This loader represents speech queue preparation and Android TTS initialization. Once playback
starts, buffered sentence transitions retain the normal pause button; the loader returns only if
the rolling buffer genuinely runs dry.

While speaking, Android's range callbacks update the highlighted text. When the spoken range moves
outside the current page, the reader automatically lays out and displays the page containing that
range. If an installed TTS engine does not provide word/range timings, the whole active sentence is
highlighted as a fallback. Quote glyphs remain visually attached to their cached character's
highlight, but punctuation-only fragments and decorative separators are skipped instead of being
sent to TTS. Quote glyphs are removed from the synthesized text without changing offsets, which
prevents some Android voices from announcing them as “quote.”

## High-level processing flow

```mermaid
flowchart LR
    A["Tap EPUB in Files"] --> B["Android ACTION_VIEW intent"]
    B --> C["Copy EPUB to private storage"]
    C --> D["Parse container, package, manifest, and spine"]
    D --> E["Extract normalized chapter text"]
    E --> F["Restore character offset from DataStore"]
    F --> G["Measure and display current page"]
    G --> H{"Speaker JSON cached?"}
    H -->|Yes| I["Load cached speakers, profiles, and emotions"]
    H -->|No| J["Use Narrator profile"]
    I --> P["Choose offline Android voice and saved profile"]
    J --> P
    P --> Q["Apply global speed and optional phrase emotion"]
    Q --> S["Queue current sentence plus five look-ahead sentences"]
    S --> R["Speak and highlight the current TTS range"]
    R --> T["Refill the rolling three-sentence queue"]
    T --> R
```

The app generates speech in real time and does not prerecord a chapter or complete book. The
ViewModel maintains a rolling queue of the current phrase plus five look-ahead phrases. Each
request snapshots its character voice, pitch, rate, and emotion pause. Procedural Android TTS uses
the platform queue. Both neural engines use one shared PCM pipeline: a generation worker renders
phrases in order while a separate playback worker consumes already prepared audio. An eight-entry
LRU cache normally retains the five phrases ahead and roughly three recently heard phrases, making
short backward navigation instant. If generation genuinely falls behind playback, the standard
preparation spinner is shown until the next PCM buffer is ready.

## Architecture

VoxReader is a single-activity Compose application using MVVM and explicit dependency injection.
The source is divided by responsibility:

```text
com.bifel.voxreader
├── domain
│   ├── model          Pure application models
│   ├── repository     Storage and book-source contracts
│   ├── preanalysis    Portable analysis models and contracts
│   ├── speech         SpeechEngine contract and events
│   ├── text           Sentence segmentation
│   └── usecase        Import and restore workflows
├── data
│   ├── epub           Local file import and EPUB parser
│   ├── preferences    DataStore repository
│   ├── preanalysis    JSON validation and Room cache
│   └── speech         Android TextToSpeech adapter
├── presentation
│   ├── reader         Reader ViewModel, UI state, pagination, and screen
│   └── theme          Warm dark/light Compose themes
├── AppContainer       Application-scoped dependency composition
├── VoxReaderApplication
└── MainActivity       Intent entry point and Compose host
```

### MVVM responsibilities

`ReaderViewModel` owns durable screen state and coordinates use cases:

- loading or importing a book;
- restoring and saving reading position;
- importing and resolving cached speaker analysis;
- controlling sentence-level playback;
- mapping TTS timing events to global book offsets; and
- exposing immutable `ReaderUiState`.

Compose owns layout-specific state that cannot exist independently of the UI, notably the measured
current page and adjacent-page cache. The UI reports each displayed page range to the ViewModel,
which persists the corresponding character offset. Phrase and page-boundary searches use binary
lookup over their ordered manifests rather than whole-book scans.

Data implementations do not leak into the ViewModel. It depends on interfaces such as
`BookRepository`, `LibraryRepository`, and `SpeechEngine`.

### SOLID design choices

- **Single responsibility:** EPUB parsing, persistence, analysis-file handling, speech synthesis,
  pagination, and UI orchestration are separate classes.
- **Open/closed:** another offline synthesis implementation can implement `SpeechEngine` without
  changing reader state or controls.
- **Liskov substitution:** callers use the domain contracts and do not depend on Android TTS or
  DataStore implementation details.
- **Interface segregation:** storage, settings, cached analysis, and speech have small,
  purpose-specific contracts.
- **Dependency inversion:** the ViewModel and use cases depend on domain interfaces; `AppContainer`
  selects concrete Android implementations at the application boundary.

Manual dependency injection is intentional for this small POC. It avoids annotation processing and
keeps object ownership visible. Hilt can be introduced later if the object graph becomes complex.

## EPUB handling

The parser uses the standard EPUB structure:

1. read `META-INF/container.xml`;
2. locate the OPF package document;
3. read metadata, manifest, and spine;
4. resolve XHTML documents in spine order;
5. remove scripts, styles, navigation, and SVG content;
6. extract headings, paragraphs, list items, and preformatted text; and
7. join chapters into one normalized text stream.

Images, CSS layout, footnotes, tables, embedded fonts, media overlays, and DRM are outside the POC.
The imported source EPUB remains in private storage so it can be reparsed on later launches.

## Persistence

Jetpack Preferences DataStore is the durable source for:

- theme choice;
- whether speech controls are enabled;
- imported-book metadata;
- the latest book ID;
- page start/end character offsets; and
- global playback speed; and
- per-book Narrator/character voice overrides.

Book text is not duplicated inside DataStore. EPUB files live under the app's private `files/books`
directory and are named by a SHA-256 digest. Reopening the same source therefore reuses the stored
copy and its existing progress/character profiles.

## Speaker-analysis cache

VoxReader accepts one versioned JSON contract for manual, desktop, cloud, and future overnight
analysis. Import validates the book hash, phrase offsets, text hashes, schema versions, profiles,
and emotions before a new Room revision becomes active. Invalid or incomplete files never replace
the currently active revision.

The reader treats every imported row, including `narrator` and `unknown`, as authoritative. It
does not guess speakers during playback. When no valid cache exists, sentence segmentation still
supports navigation and TTS, but every phrase uses the Narrator profile. Voice edits are stored
separately in DataStore and overlaid on imported profiles, so reimporting analysis does not erase
the user's settings.

## Speech generation

VoxReader exposes three interchangeable implementations of the `SpeechEngine` contract:

- **Procedural** wraps Android's platform `TextToSpeech` service;
- **AI Simple** runs downloaded Piper voices locally through sherpa-onnx; and
- **AI Advanced** runs the compact eight-speaker KittenTTS Micro model locally through
  sherpa-onnx.

`AndroidSpeechEngine`:

- filters out voices that report requiring a network connection;
- selects one installed English locale pack with both male and female choices, avoiding expensive
  cross-locale pipeline switches;
- assigns voice slots deterministically;
- applies saved pitch and speaking-rate settings;
- queues three independently configured sentence utterances with `QUEUE_ADD`;
- emits start, range, completion, stop, and error events; and
- uses range offsets to synchronize highlighting.

The final delivery is composed from the saved character profile, global playback speed, and the
optional imported phrase emotion. A reader-wide post-processing switch gates editable profiles
for all supported emotions. Each profile controls speed, pitch, loudness, warm/bright tone, and
the trailing pause; intensity controls how far the result moves from neutral. Android TTS applies
its native controls, while neural engines also transform generated PCM before caching. User-edited
voice and emotion settings are kept separately from imported JSON, so reimporting analysis does
not erase those choices.

Speech is generated in real time. Look-ahead requests are submitted while the current sentence is
playing, so playback advances to queued Android TTS work or already generated neural PCM instead of
starting synthesis at the transition. Piper and Kitten share cancellation, AudioTrack playback,
look-ahead generation, and in-memory backward caching rather than implementing separate queue
semantics. Neutral delivery adds no artificial inter-sentence delay; an explicit sadness pause is
applied after the phrase. Pausing clears the rolling queue and stops the current playback; resuming
rebuilds it from the selected sentence.

## Planned evolution

### POC hardening

- Validate more EPUB variants and malformed files.
- Add instrumentation tests for file intents and TTS callback handling.
- Add font size, margins, and line-spacing controls.
- Improve long-sentence and nested-quotation handling.

### Consistent local neural voices

Piper and KittenTTS Micro provide device-independent offline voices through the same speech
contract as Android TTS. Model files are downloaded on demand into app-private storage rather than
inflating the base APK. Future neural models can reuse the shared buffered PCM pipeline.

### Product features beyond the POC

- in-app library and file picker;
- chapter names and direct table-of-contents navigation;
- bookmarks, notes, search, and selectable text;
- configurable tap direction and gestures;
- background playback and media notification controls;
- pronunciation overrides;
- sentence-ahead audio buffering and disk cache; and
- optional overnight on-phone or paid cloud whole-book analysis cached for fully offline playback
  afterward. See `BOOK_PREANALYSIS_IMPLEMENTATION_PLAN.md`.

## POC success criteria

The concept is validated when a user can:

1. tap an EPUB in Android Files and open it in VoxReader;
2. read it as stable pages and return to the same location after restarting;
3. enable speech and hear playback begin from the visible page with a short preparation state;
4. see spoken text highlighted and pages advance automatically;
5. navigate speech by sentence; and
6. import a portable speaker map and hear recurring characters use distinguishable voice
   variations without a server or internet connection during playback.

## Current validation

The POC has been built and exercised on a Samsung Galaxy S21 running Android 15. Validation
includes:

- opening a real-world EPUB 2 file through an Android `ACTION_VIEW` content URI;
- extracting and paginating the book text;
- moving to the next page using the right tap zone;
- force-stopping and relaunching the app at the saved book and character offset;
- showing the speech-preparation loader;
- producing speech with the device's offline Android TTS voice;
- keeping normal queued sentence transitions within a few milliseconds after voice warm-up;
- highlighting the active spoken range; and
- pausing playback without import, speech, or runtime errors in logcat.

Desktop unit tests cover EPUB order/text extraction, sentence segmentation, portable JSON
validation, playback buffering, emotion delivery, and reader navigation helpers.
