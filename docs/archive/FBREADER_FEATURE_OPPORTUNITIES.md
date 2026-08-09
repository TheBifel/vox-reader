# FBReader feature opportunities for VoxReader

> Historical research snapshot. Several opportunities listed here have since shipped or changed.
> Use [product memory](../memory/PRODUCT.md) and [current state](../memory/STATE.md) for present
> behavior; retain this document only as background research.

## Purpose and evidence boundary

This document identifies reader ideas that VoxReader could adapt from FBReader without expanding
the proof of concept into a general-purpose ebook platform. The recommendations are filtered
through YAGNI: a feature is valuable here only if it materially improves reading or character-aware
speech.

There are two different evidence sets:

- **Current, verified product behaviour** comes from FBReader's official website, FAQ, release
  notes, and current platform feature tables.
- **Older, verified implementation behaviour** comes from the public FBReaderJ repository at
  commit [`e83aec9`](https://github.com/geometer/FBReaderJ/tree/e83aec9f94084aa59d39e33876bdb6fdc275c95e).
  FBReader states that Android source became closed after version 2.7; therefore, the old code is
  useful as architectural evidence, but it does not prove how the current Android app is
  implemented.

Anything labelled **inference** is a proposed VoxReader design rather than a claim about FBReader.

The old source is GPL-licensed. This document recommends independently implementing behaviours and
ideas using Android and Compose APIs. Do not copy source code into VoxReader unless VoxReader's
licensing is deliberately made compatible and the obligations are reviewed.

## Executive recommendation

VoxReader has already adopted the most important performance ideas visible in old FBReaderJ:
position-based progress, estimated page counts, and a three-page working set consisting of the
previous, current, and next pages. Keep those foundations.

The smallest useful next set is:

1. a table-of-contents sheet with the current chapter selected;
2. reliable background speech with notification controls;
3. a sleep timer;
4. pronunciation substitutions plus a manual language override; and
5. in-book search with a small back/forward position history.

Bookmarks, richer typography, text selection, footnotes, libraries, cloud synchronisation, and
additional formats should wait until real use demonstrates a need.

## What VoxReader already adapted

### Bounded page working set

**Verified older behaviour.** `ZLTextView` owns previous, current, and next page objects, swaps them
when scrolling finishes, and prepares a neighbouring page only when needed. See
[`ZLTextView.java`](https://github.com/geometer/FBReaderJ/blob/e83aec9f94084aa59d39e33876bdb6fdc275c95e/fbreader/app/src/main/java/org/geometerplus/zlibrary/text/view/ZLTextView.java#L50-L54)
and its page-swap logic in the same file.

**VoxReader status.** Adopted. Compose measures the visible page and keeps adjacent pages as a
small UI-owned cache. Do not add a whole-book page index.

### Position-based progress and estimated pages

**Verified older behaviour.** FBReaderJ maps scrollbar progress to cumulative text size and
estimates page counts from text area, character width, line height, and total text size rather than
laying out the entire book. See the scrollbar and page-estimation methods in
[`ZLTextView.java`](https://github.com/geometer/FBReaderJ/blob/e83aec9f94084aa59d39e33876bdb6fdc275c95e/fbreader/app/src/main/java/org/geometerplus/zlibrary/text/view/ZLTextView.java#L586-L654).

**VoxReader status.** Adopted. Keep the character offset as the durable locator and treat page
numbers as viewport-dependent estimates.

### Chapter markers on progress

**Verified older behaviour.** FBReaderJ can draw table-of-contents marks in its footer and limits
how many marks are rendered. See
[`FBView.java`](https://github.com/geometer/FBReaderJ/blob/e83aec9f94084aa59d39e33876bdb6fdc275c95e/fbreader/app/src/main/java/org/geometerplus/fbreader/fbreader/FBView.java#L455-L496)
and
[`FooterOptions.java`](https://github.com/geometer/FBReaderJ/blob/e83aec9f94084aa59d39e33876bdb6fdc275c95e/fbreader/app/src/main/java/org/geometerplus/fbreader/fbreader/options/FooterOptions.java#L29-L42).

**VoxReader status.** Adopted. If books with hundreds of very short sections make markers noisy,
cap or coalesce markers at display time; do not change stored chapter offsets.

## Recommended opportunities

### 1. Table-of-contents sheet with current-chapter context

**Priority:** P0

**Complexity/risk:** Low to medium; malformed or flat EPUB navigation is the main risk.

**What FBReader does.** The old Android app exposes a hierarchical table of contents, selects the
entry containing the current reading position, and jumps to an entry's text reference. This is
verified in
[`TOCActivity.java`](https://github.com/geometer/FBReaderJ/blob/e83aec9f94084aa59d39e33876bdb6fdc275c95e/fbreader/app/src/main/java/org/geometerplus/android/fbreader/TOCActivity.java#L41-L135).
Current FBReader platform documentation also lists table-of-contents support as a reader feature.

**Value for VoxReader.** Chapter ticks show structure but cannot identify or directly choose a
chapter. A labelled TOC is the highest-value navigation addition for long books.

**Implementation approach.** Preserve title, depth, and global text offset while parsing EPUB
navigation/spine data. Add a pure `TableOfContents` domain model. Expose the current entry from
`ReaderViewModel` using binary search over ordered offsets. Render it as a Compose modal bottom
sheet launched from the reader menu. A selection calls the existing offset seek path, stops or
repositions speech consistently, and closes the sheet.

**Recommendation.** Build next. Start with a flat list if the parser currently has only chapter
titles and offsets; add nesting only when real EPUBs require it.

### 2. Background speech with system media controls

**Priority:** P0

**Complexity/risk:** High; lifecycle, audio focus, headset events, and OEM battery management must
be handled correctly.

**What FBReader does.** The current Android reading-aloud guide states that playback continues with
the screen off or another app open, and that it can be controlled from the notification panel.
This is verified current behaviour in
[Automatic Reading Aloud on Android](https://fbreader.org/en/android/reading-aloud).

**Value for VoxReader.** Character-aware speech is the product's defining feature. Requiring the
reader screen to remain foreground makes it much less useful as an audiobook substitute.

**Implementation approach.** Move ownership of the active speech session from the activity-bound
UI into a foreground playback service backed by `MediaSession`. Keep phrase queue construction in
an application/domain coordinator and expose immutable playback state as `StateFlow`. The
ViewModel sends intents such as play, pause, seek sentence, and stop; Compose observes state but
does not own playback lifetime. Handle audio focus, becoming-noisy/headset disconnect, notification
actions, and explicit stop. Persist only the resumable book/phrase position, not transient queue
objects.

**Recommendation.** Build before expanding speaker analysis. It improves every book and every
voice source.

### 3. Compact/expanded speech panel

**Priority:** P1

**Complexity/risk:** Low; avoid duplicating controls in the drawer and panel.

**What FBReader does.** The current Android guide describes a tall first-run panel containing
speech settings and a compact mode that leaves more space for the page. This is verified current
behaviour in
[Automatic Reading Aloud on Android](https://fbreader.org/en/android/reading-aloud).

**Value for VoxReader.** Voice speed and playback controls are useful while listening, but permanent
controls reduce page height and force repagination. A compact player protects reading space.

**Implementation approach.** Keep one `SpeechControls` composable with collapsed and expanded
states. Collapsed mode shows previous, play/pause, next, and speed; expanded mode adds sleep timer
and session-level voice/language controls. Store only the user's preferred collapsed state in
DataStore. Character voice editing remains in the character list.

**Recommendation.** Build together with background playback, not as a separate control system.

### 4. Sleep timer

**Priority:** P1

**Complexity/risk:** Low to medium; timer semantics across process death need to be explicit.

**What FBReader does.** The current reading-aloud panel includes a timer. This is verified in the
official Android reading-aloud guide; the public page does not specify the timer's internal design.

**Value for VoxReader.** Listening in bed is a natural use case. A timer prevents hours of unwanted
playback and lost reading position.

**Implementation approach.** Put an absolute expiry timestamp in the playback service's session
state. Offer a minimal preset list such as 15, 30, 45, and 60 minutes plus “end of chapter.” Pause
cleanly at a phrase boundary when practical; use the timestamp as a hard upper bound. Surface the
remaining time in the compact player and notification.

**Recommendation.** Build a small preset implementation; do not create alarms, schedules, or a
general timer framework.

### 5. Pronunciation substitutions

**Priority:** P1

**Complexity/risk:** Medium; replacements must not corrupt display-to-speech offset mapping.

**What FBReader does.** Current FBReader 3.8 can load a replacements file to adjust TTS
pronunciation. This is verified in the official
[reading-aloud guide](https://fbreader.org/en/android/reading-aloud) and current Android release
notes mention continued improvements to substitution patterns.

**Value for VoxReader.** Fantasy names are exactly where generic offline TTS performs poorly.
Correcting recurring names may improve perceived quality more than adding another pitch variant.

**Implementation approach.** Define ordered, literal replacements at book and global scope. Apply
them only to the string sent to TTS, never to `BookDocument.text` or cached phrase identities.
Maintain a mapping from synthesized-string ranges back to original phrase ranges; until that map
exists, limit substitutions to whole-token replacements and highlight the whole active phrase.
Store rules in Room because the data is list-shaped and book-scoped; expose add/edit/delete in a
small settings sheet.

**Recommendation.** Build literal whole-token replacements first. Regex, phoneme alphabets, and
import/export are YAGNI until requested.

### 6. Language override and offline-voice diagnostics

**Priority:** P1

**Complexity/risk:** Medium because Android TTS engines expose inconsistent metadata.

**What FBReader does.** Current FBReader lets the user select the language manually if detection
fails and advises choosing a non-network voice to avoid interrupted speech. These are verified in
the official
[reading-aloud guide](https://fbreader.org/en/android/reading-aloud) and
[TTS FAQ](https://fbreader.org/en/faq/tts).

**Value for VoxReader.** A wrong language or network-only engine can look like a broken speech
pipeline. Clear diagnostics reduce false analysis and buffering bug reports.

**Implementation approach.** Resolve language in this order: per-book override, EPUB metadata,
device default. Query the selected engine's voices and display language, locale, and whether a
voice reports network requirements. Validate narrator and character selections when the engine or
locale changes, then use deterministic fallback profiles. Do not silently download voices.

**Recommendation.** Add a book-level language override and a visible “offline/network” voice label.
Automatic language detection beyond EPUB metadata is not yet justified.

### 7. In-book search with next/previous result

**Priority:** P1

**Complexity/risk:** Medium; very large books require cancellable background work.

**What FBReader does.** The old text view searches its model and supports next and previous marks;
see
[`ZLTextView.java`](https://github.com/geometer/FBReaderJ/blob/e83aec9f94084aa59d39e33876bdb6fdc275c95e/fbreader/app/src/main/java/org/geometerplus/zlibrary/text/view/ZLTextView.java#L170-L227).
Current desktop documentation also lists search and next/previous match navigation.

**Value for VoxReader.** Search makes it practical to return to a scene, character mention, or
quote and is especially useful while checking imported speaker attribution.

**Implementation approach.** Search the normalized `BookDocument.text` on `Dispatchers.Default`,
returning lightweight offset ranges in batches. Keep query/results in the ViewModel. Seeking a
result uses the existing offset path and adds the old position to navigation history. Compose
draws search highlighting only for results overlapping the visible page, using the same bounded
range lookup as speaker coloring.

**Recommendation.** Build after TOC. Start with case-insensitive literal search; whole-word,
regex, and fuzzy search are YAGNI.

### 8. Back/forward history for non-linear jumps

**Priority:** P1

**Complexity/risk:** Low.

**What FBReader does.** Current desktop documentation describes persistent undo/redo navigation for
jumps and footnote mode. The old public source separately stores precise reading positions and
jump endpoints, but it does not establish that the current Android implementation uses the same
stack design. Treat the stack implementation below as an inference.

**Value for VoxReader.** TOC and search are frustrating without an easy return to the page the user
left.

**Implementation approach.** Add a bounded `NavigationHistory` of character offsets. Push only
non-linear actions: TOC selection, search result, bookmark, hyperlink, or progress-slider commit.
Do not push normal page turns or TTS auto-advance. Clear the forward stack after a new jump. Keep
the first implementation in memory; persist it only if users need history across launches.

**Recommendation.** Build with TOC/search. A maximum of roughly 20 entries is enough.

### 9. Bookmarks and user highlights

**Priority:** P2

**Complexity/risk:** Medium to high because user highlighting and speaker highlighting must remain
visually distinct.

**What FBReader does.** FBReader treats a highlight as a bookmark, offers multiple styles, and
allows navigation and deletion. This is verified current behaviour in the official
[Bookmarks FAQ](https://fbreader.org/en/faq/bookmarks). The old source maintains ordered
highlightings and renders their backgrounds/outlines in
[`ZLTextView.java`](https://github.com/geometer/FBReaderJ/blob/e83aec9f94084aa59d39e33876bdb6fdc275c95e/fbreader/app/src/main/java/org/geometerplus/zlibrary/text/view/ZLTextView.java#L277-L307).

**Value for VoxReader.** Useful for study and for flagging speaker-analysis errors, but not required
for the listening POC.

**Implementation approach.** Store book ID, start/end offsets, selected text hash, note, color
style, and timestamps in Room. Render only overlapping ranges. Use an underline or margin marker
for user highlights so character background colors remain readable. Re-anchor by nearby text hash
if a parser-version change moves offsets.

**Recommendation.** Defer. If analysis correction becomes important, first build a dedicated
“report wrong speaker” action instead of a general annotation system.

### 10. Text selection, copy, and dictionary/translation action

**Priority:** P2

**Complexity/risk:** High in the current Compose pagination approach.

**What FBReader does.** The old source has draggable selection cursors and configurable long-press
word actions; see
[`FBView.java`](https://github.com/geometer/FBReaderJ/blob/e83aec9f94084aa59d39e33876bdb6fdc275c95e/fbreader/app/src/main/java/org/geometerplus/fbreader/fbreader/FBView.java#L207-L242).
Current FBReader Premium advertises Google Translate/DeepL integration.

**Value for VoxReader.** Helpful for language learners, but separate from character speech.

**Implementation approach.** Use Compose text selection only after verifying that it can coexist
with page tap zones and annotated speaker spans. Offer Android copy/share/process-text intents
before adding any network translation provider. Pause TTS only if the user starts a selection.

**Recommendation.** Defer. Copy is the only minimal first action; do not build accounts or a
translation backend.

### 11. Reading appearance controls

**Priority:** P2

**Complexity/risk:** Medium because every geometry change invalidates current and adjacent pages.

**What FBReader does.** Current FBReader supports custom fonts and manual brightness by swiping the
left edge, according to the official
[Preferences FAQ](https://fbreader.org/en/faq/preferences). Its current desktop reader also
documents customisable headers/footers, margins, columns, and automatic dark theme.

**Value for VoxReader.** Font size and line spacing are meaningful accessibility features. Extensive
font, wallpaper, margin, and gesture customisation is not central to the POC.

**Implementation approach.** Add only font size and line spacing initially. Store a small
`ReaderAppearance` value in DataStore. On change, preserve the current text offset, invalidate the
three-page layout cache, and rebuild the visible page. Continue following system brightness;
manual brightness and custom font folders can wait.

**Recommendation.** Defer until core playback is reliable. Do not reproduce FBReader's full
appearance matrix.

### 12. EPUB hyperlinks and footnotes with return navigation

**Priority:** P2

**Complexity/risk:** High; the current plain-text extraction discards link structure.

**What FBReader does.** In old FBReaderJ, a tap checks hyperlinks before applying tap-zone page
actions and can switch to a dedicated footnote view; see
[`FBView.java`](https://github.com/geometer/FBReaderJ/blob/e83aec9f94084aa59d39e33876bdb6fdc275c95e/fbreader/app/src/main/java/org/geometerplus/fbreader/fbreader/FBView.java#L74-L118)
and hyperlink handling in
[`FBReaderApp.java`](https://github.com/geometer/FBReaderJ/blob/e83aec9f94084aa59d39e33876bdb6fdc275c95e/fbreader/app/src/main/java/org/geometerplus/fbreader/fbreader/FBReaderApp.java#L238-L265).

**Value for VoxReader.** Footnotes matter in nonfiction and some novels. They are not required for
the current fiction/TTS experiment.

**Implementation approach.** First extend parsing to preserve link spans and resolved EPUB targets
without changing normalized text offsets. Internal links seek to a target and push history;
footnotes open a modal sheet and return without losing the spoken phrase. External links require
an explicit launch confirmation. Link hit-testing must take precedence over page tap zones.

**Recommendation.** Defer until a real target book exposes the need.

### 13. Hyphenation

**Priority:** P3

**Complexity/risk:** High relative to its POC value; it affects layout, selection, and highlighting.

**What FBReader does.** The public source includes language-specific TeX-style hyphenation, and the
current Android 3.8.23 notes report improved English hyphenation. See the official
[3.8.23 release note](https://fbreader.org/en/news/fbreader-3823-for-android).

**Value for VoxReader.** Better typography and slightly denser pages, especially on narrow screens.
It does not improve speech and may introduce visible-vs-spoken token complications.

**Implementation approach.** If eventually needed, use a maintained Android/Unicode-compatible
hyphenation component during layout only. Soft hyphens must never enter the canonical book text,
phrase IDs, or TTS input.

**Recommendation.** Do not build now.

### 14. Configurable tap zones, swipe animation, hardware-key mapping, and brightness gestures

**Priority:** P3

**Complexity/risk:** Medium as a group; gesture conflicts and settings sprawl are likely.

**What FBReader does.** The old source maps single/double taps through configurable zones, supports
flick scrolling, lets the left edge adjust brightness, and the current FAQ documents hardware-key
mapping. See
[`FBView.java`](https://github.com/geometer/FBReaderJ/blob/e83aec9f94084aa59d39e33876bdb6fdc275c95e/fbreader/app/src/main/java/org/geometerplus/fbreader/fbreader/FBView.java#L61-L203)
and the official [Preferences FAQ](https://fbreader.org/en/faq/preferences).

**Value for VoxReader.** Some users prefer swipes or volume-button page turns, but the current three
tap zones already satisfy the core phone workflow.

**Implementation approach.** If requested, represent actions as a small enum and map gestures or
keys to intents handled by the ViewModel. Keep link/selection hit testing ahead of tap-zone
fallback. Do not implement arbitrary XML keymaps or a visual zone editor.

**Recommendation.** Skip unless device testing reveals a strong accessibility or e-ink need.

## Features to deliberately leave out

These are real FBReader capabilities, but they do not belong in the current VoxReader scope:

- **Library/catalogue management, shelves, author/series browsing, and cover grids.** VoxReader's
  intentional POC entry point is opening an EPUB from Android. Add only a short “recent books” list
  if repeated file-manager use becomes painful.
- **Cloud book, position, and bookmark synchronisation.** FBReader's book network synchronises
  collections, positions, and bookmarks, as documented in the
  [FBReader book network FAQ](https://books.fbreader.org/faq.html). VoxReader is offline-first and
  has no account model; speaker-analysis JSON already supplies the needed portable artifact.
- **OPDS catalogues and book purchasing.** These add authentication, downloads, metadata, and
  content-distribution responsibilities unrelated to character speech.
- **PDF, DjVu, comics, FB2, MOBI, and DRM.** Current FBReader supports many formats, as listed on its
  [Android page](https://fbreader.org/android). VoxReader's phrase-offset and EPUB-analysis contract
  should mature before another document model is introduced.
- **Two-column/tablet layout and elaborate page animations.** They multiply pagination and visual
  testing without improving the phone-first POC.
- **Settings import/export.** DataStore is sufficient until the setting surface becomes materially
  larger.

## Suggested delivery order

```text
Now
  TOC sheet + current chapter
  Background playback + notification controls

Next
  Compact/expanded player + sleep timer
  Pronunciation substitutions + language/voice diagnostics
  Search + bounded navigation history

Only after real demand
  Bookmarks/highlights
  Basic typography controls
  Selection/copy
  Footnotes/hyperlinks

Do not schedule for the POC
  General library/cloud/OPDS
  More document formats/DRM
  Full gesture/theme/font customisation
```

This sequence keeps the product centred on reliable, character-aware listening while borrowing the
reader behaviours that have the clearest user value.

## Sources reviewed

- [FBReader for Android](https://fbreader.org/android) — current formats, editions, and explicit
  statement that current source is closed and the old 2.6-era source is public.
- [Automatic Reading Aloud on Android](https://fbreader.org/en/android/reading-aloud) — current
  playback panel, background reading, tuning, substitutions, timer, and notification controls.
- [Reading aloud FAQ](https://fbreader.org/en/faq/tts) — system TTS engines, offline-voice advice,
  background limitations, and current platform scope.
- [Bookmarks FAQ](https://fbreader.org/en/faq/bookmarks) — highlight/bookmark model and styles.
- [Preferences FAQ](https://fbreader.org/en/faq/preferences) — brightness gesture, custom fonts,
  and hardware-key mapping.
- [FBReader for Windows](https://fbreader.org/en/windows) — current cross-platform reader feature
  inventory, including TOC, search, navigation history, columns, and appearance. Items documented
  only there are not claimed as current Android behaviour.
- [FBReader book network FAQ](https://books.fbreader.org/faq.html) — synchronised data and OPDS
  access.
- [FBReader Android 3.8.23 release note](https://fbreader.org/en/news/fbreader-3823-for-android) —
  complex-EPUB opening performance and hyphenation improvements.
- [Public FBReaderJ source at `e83aec9`](https://github.com/geometer/FBReaderJ/tree/e83aec9f94084aa59d39e33876bdb6fdc275c95e) —
  old page cache, estimated progress, selection, search, TOC, hyperlinks, and input handling.
