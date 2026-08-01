# VoxReader

VoxReader is an offline-first Android EPUB reader that reads books aloud while assigning stable
voice variations to recurring characters. The proof of concept is built with Kotlin, Jetpack
Compose, MVVM, DataStore, portable speaker-analysis JSON files, Android's local text-to-speech
service, and optional downloaded on-device Piper voice packs.

See [Product and solution overview](docs/PRODUCT_AND_SOLUTION.md) for the product goal, user flow,
architecture, processing pipeline, current limitations, and implementation roadmap.

See [Whole-book pre-analysis implementation plan](docs/BOOK_PREANALYSIS_IMPLEMENTATION_PLAN.md)
for the future overnight on-phone and opt-in paid cloud cache paths.

See [FBReader feature opportunities](docs/FBREADER_FEATURE_OPPORTUNITIES.md) for a sourced,
YAGNI-filtered list of reader UI and logic ideas worth considering next.

## Open in Android Studio

1. Open the `VoxReader` folder as a project.
2. Allow Gradle sync to complete.
3. Select an Android device running API 26 or later.
4. Run the `app` configuration.
5. In Android's Files app, tap an EPUB and select VoxReader.

The app intentionally has no file picker in this POC. A new book enters the library only through
Android's **Open with** flow.

## Reader controls

- Tap the centre of a page to reveal the reading controls and hamburger menu.
- The table of contents is collapsed by default; expand it only when you want to jump chapters.
- **Characters and voices** opens a focused dialog for colour legend and per-character voice tuning.
- **Voice engine** opens the local AI model library. Each model shows a concise use-oriented
  description, its installed size, download status/progress, and explicit **Play**, **Download**,
  or **Remove** controls.
- While a book is playing, VoxReader uses Android media controls and a foreground playback service.
  It holds a partial wake lock only during active playback so local AI phrase generation remains
  responsive while the screen is locked.

## Repository test data

`test-data/` intentionally contains only the two user-supplied EPUB fixtures and their matching
speaker-analysis JSON files. Do not add book text, generated screenshots, downloaded models, or
private analysis work directories to version control.
