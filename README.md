# VoxReader

VoxReader is an offline-first Android EPUB reader that reads books aloud while assigning stable
voice variations to recurring characters. The proof of concept is built with Kotlin, Jetpack
Compose, MVVM, DataStore, and Android's local text-to-speech service.

See [Product and solution overview](docs/PRODUCT_AND_SOLUTION.md) for the product goal, user flow,
architecture, processing pipeline, current limitations, and implementation roadmap.

## Open in Android Studio

1. Open the `VoxReader` folder as a project.
2. Allow Gradle sync to complete.
3. Select an Android device running API 26 or later.
4. Run the `app` configuration.
5. In Android's Files app, tap an EPUB and select VoxReader.

The app intentionally has no file picker in this POC. A new book enters the library only through
Android's **Open with** flow.

