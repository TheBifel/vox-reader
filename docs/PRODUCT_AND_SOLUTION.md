# VoxReader: product and high-level solution

The whole-book speaker-analysis exchange format is documented in
[`SPEAKER_ANALYSIS_JSON.md`](SPEAKER_ANALYSIS_JSON.md). It is the shared contract for manual,
cloud/outsourced, desktop, and future overnight analysis. Once imported, its phrase manifest and
speaker decisions are authoritative: the reader displays cached character colors and does not run
live attribution for that manifest.

## What the app does

VoxReader is an offline-first Android EPUB reader designed around synchronized read-aloud
playback. The reader displays a book as fixed pages, speaks the text currently being read, and
highlights the active text as speech progresses. Dialogue is associated with recurring fictional
characters, and each character receives a stable local voice variation.

The proof of concept deliberately avoids voice cloning, cloud services, accounts, and server-side
inference. It uses deterministic dialogue rules plus a bundled, task-specific TinyBERT classifier
on the phone, then uses voices already available in Android's local text-to-speech engine.
Characters are differentiated through deterministic voice selection, pitch, and speaking-rate
profiles. Imported phrase emotions can temporarily modify that profile while a phrase is spoken.

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
There is intentionally no in-app library browser or file picker in this POC. The attribution model
is bundled with the application and needs no separate installation.

### Reading

The EPUB text is laid out into viewport-sized pages. Pagination is calculated from the actual
Compose text measurements, screen dimensions, and reader typography. The stored position is a
character offset rather than a fragile page number, allowing the page to be reconstructed after a
screen-size or orientation change.

The tap zones follow conventional left-to-right navigation:

- tap the left third to move to the previous page;
- tap the right third to move to the next page;
- tap the middle third to reveal or hide the reader header;
- use the header's hamburger button to open reader settings.

A thin progress bar at the bottom represents the current character offset as a proportion of the
book's total extracted text.

### Appearance and speech controls

The drawer contains:

- a dark/light reader theme switch; and
- a command to enable or hide text-to-speech controls;
- the active offline attribution engine and last-analysis status; and
- an editable Narrator and character list with voice group, variation, pitch, and base-rate
  controls.

The drawer closes by tapping its shaded outside area, swiping it away, or pressing Android Back.

The dark theme uses a near-black background and warm white text. The light theme reverses that
relationship with a warm paper background and dark text.

When speech controls are enabled, previous-sentence, play/pause, and next-sentence controls appear
above the reading progress bar. A speed control above them adjusts global playback from `0.5x` to
`2.0x` and is remembered across launches. Pressing play starts from the first speakable sentence
visible on the current page. During initial speech preparation, the play button becomes a loader.
This loader represents character-data preparation and Android TTS initialization.

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
    G --> H["User enables speech and presses play"]
    H --> I["Segment sentences and select a small scene"]
    I --> J["Deterministic rules resolve explicit and continuing dialogue"]
    J --> K{"Speaker still ambiguous?"}
    K -->|Yes| L["TinyBERT scores nearby character candidates"]
    K -->|No| M["Use rule attribution"]
    L -->|Confident| N["Use highest-scoring candidate"]
    L -->|Low confidence or error| M
    N --> O["Reuse persistent character profile"]
    M --> O
    O --> P["Choose offline Android voice and saved profile"]
    P --> Q["Apply global speed and optional phrase emotion"]
    Q --> S["Speak one sentence"]
    S --> R["Highlight TTS range and preselect next sentence"]
    R --> Q
```

The app generates speech one sentence at a time. It does not prerecord a chapter or complete book.
Android produces the current sentence while the application already knows which sentence follows,
giving immediate playback and deterministic previous/next controls.

## Architecture

VoxReader is a single-activity Compose application using MVVM and explicit dependency injection.
The source is divided by responsibility:

```text
com.bifel.voxreader
├── domain
│   ├── model          Pure application models
│   ├── repository     Storage and book-source contracts
│   ├── analysis       CharacterAnalyzer contract
│   ├── speech         SpeechEngine contract and events
│   ├── text           Sentence segmentation
│   └── usecase        Import and restore workflows
├── data
│   ├── epub           Local file import and EPUB parser
│   ├── preferences    DataStore repository
│   ├── analysis       Dialogue rules, TinyBERT candidate scorer, and tokenizer
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
- preparing character analysis;
- controlling sentence-level playback;
- mapping TTS timing events to global book offsets; and
- exposing immutable `ReaderUiState`.

Compose owns layout-specific state that cannot exist independently of the UI, notably the measured
end of a page. The UI reports each calculated page range to the ViewModel, which persists the
corresponding character offset.

Data implementations do not leak into the ViewModel. It depends on interfaces such as
`BookRepository`, `LibraryRepository`, `CharacterAnalyzer`, and `SpeechEngine`.

### SOLID design choices

- **Single responsibility:** EPUB parsing, persistence, dialogue analysis, speech synthesis,
  pagination, and UI orchestration are separate classes.
- **Open/closed:** the hybrid TinyBERT analyzer and deterministic fallback both implement
  `CharacterAnalyzer`; another candidate scorer can implement `SpeakerScorer`, and a
  Kokoro/sherpa-onnx engine can implement `SpeechEngine`.
- **Liskov substitution:** callers use the domain contracts and do not depend on Android TTS or
  DataStore implementation details.
- **Interface segregation:** storage, settings, character analysis, and speech have small,
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

## On-device character attribution

The hybrid analyzer starts with an inexpensive deterministic pass. It segments sentences,
recognizes quotation marks, finds patterns such as `Alice said` and `asked Bob`, preserves a
speaker through an open quotation or paragraph continuation, tracks recent dialogue participants,
and assigns non-dialogue text to the narrator. Explicit dialogue tags are trusted without neural
inference.

Only ambiguous dialogue reaches the classifier. VoxReader builds a short scene, marks the target
dialogue, and supplies up to six dynamic candidates taken from nearby and saved characters.
TinyBERT scores `(scene, candidate)` pairs as `speaker` or `not speaker`. This pairwise formulation
does not place book-specific names in fixed model labels and therefore supports unseen books.
VoxReader accepts the winner only when both the probability and the margin over the runner-up meet
configured thresholds. Low confidence, invalid output, or runtime failure returns immediately to
the deterministic result.

The bundled model is fine-tuned from
[`huawei-noah/TinyBERT_General_4L_312D`](https://huggingface.co/huawei-noah/TinyBERT_General_4L_312D)
using generated fiction scenes that contain no source-book text. It is exported to ONNX and
dynamically quantized to 8-bit weights at roughly 14 MB. ONNX Runtime executes all candidates in one CPU
batch, and a local WordPiece tokenizer produces the model's fixed 160-token input. The app warms
the classifier in the background; there is no model picker, generative prompt, or 374–475 MB model
file.

Gender/voice group is resolved at character-profile level from book evidence and is not predicted
independently for every utterance. Processed phrase ranges are remembered during playback.
Previously attributed sentences and saved character profiles are preserved as the next scene is
analyzed. Profiles are stored per book in DataStore and reused after relaunch. Attribution schema
version changes neutralize old uncertain profiles before they are grounded again.

## Speech generation

`AndroidSpeechEngine` wraps the platform `TextToSpeech` API. It:

- filters out voices that report requiring a network connection;
- prefers voices matching the device language, then English;
- assigns voice slots deterministically;
- applies saved pitch and speaking-rate settings;
- speaks one sentence per utterance;
- emits start, range, completion, stop, and error events; and
- uses range offsets to synchronize highlighting.

The final TTS pitch and rate are composed from the saved character profile, the global playback
speed, and the imported phrase emotion. For the current POC, anger is faster and slightly lower,
sadness is slower and lower with a longer pause, excitement is faster and slightly higher, and
fear is faster and higher with a shorter pause. Emotion intensity controls how far the delivery
moves away from neutral. User-edited voice settings are kept separately from the imported JSON,
so reimporting an updated analysis does not erase those choices.

Speech is generated in real time. The next sentence starts after the current completion callback.
Pausing stops the current synthesis; resuming restarts the selected sentence because Android's
standard TTS API does not expose sample-accurate pause/resume.

## Planned evolution

### POC hardening

- Validate more EPUB variants and malformed files.
- Add instrumentation tests for file intents and TTS callback handling.
- Add font size, margins, and line-spacing controls.
- Improve long-sentence and nested-quotation handling.

### On-device intelligence hardening

- Measure attribution accuracy on a representative EPUB set.
- Add a reviewed fiction-attribution dataset; synthetic validation alone is not sufficient.
- Calibrate the acceptance probability and winner margin on real labeled scenes.
- Benchmark TinyBERT thread counts and sequence length across representative Android phones.
- Expand candidate extraction for aliases, titles, nested quotations, and non-English books.
- Add model/version metadata to diagnostics and automated regression benchmarks.

### Consistent bundled neural voices

If device-dependent Android voices are not good enough, add a `SpeechEngine` backed by
sherpa-onnx and a lightweight Kokoro model. This would make voice quality and the voice catalogue
consistent across phones while preserving the same ViewModel, controls, and highlighting flow.

### Product features beyond the POC

- in-app library and file picker;
- chapter navigation and table of contents;
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
6. hear a bundled phone-sized classifier and deterministic rules keep recurring characters on
   distinguishable voice variations without a server or internet connection.

## Current validation

The POC has been built and exercised on a Samsung Galaxy S21 running Android 15. Validation
includes:

- opening a real-world EPUB 2 file through an Android `ACTION_VIEW` content URI;
- extracting and paginating the book text;
- moving to the next page using the left tap zone;
- force-stopping and relaunching the app at the saved book and character offset;
- showing the speech-preparation loader;
- producing speech with the device's offline Android TTS voice;
- highlighting the active spoken range; and
- pausing playback without import, speech, or runtime errors in logcat.

Desktop unit tests cover EPUB order/text extraction, sentence segmentation, and rule-based
character attribution. A device instrumentation test separately exercises EPUB parsing with
Android's platform XML implementation because it differs from the desktop JVM parser.
