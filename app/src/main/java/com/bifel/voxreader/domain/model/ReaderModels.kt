package com.bifel.voxreader.domain.model

data class BookDocument(
    val id: String,
    val title: String,
    val localPath: String,
    val text: String,
    val chapterStarts: List<Int> = listOf(0),
    val links: List<BookLink> = emptyList(),
    val headings: List<BookTextRange> = emptyList(),
    val images: List<BookImage> = emptyList(),
)

data class BookTextRange(val start: Int, val end: Int)

data class BookLink(
    val start: Int,
    val end: Int,
    val targetOffset: Int? = null,
    val externalUri: String? = null,
    val isFootnote: Boolean = false,
)

data class BookImage(
    val offset: Int,
    val archivePath: String,
    val description: String = "Image",
)

data class StoredBook(
    val id: String,
    val title: String,
    val localPath: String,
    val readingPosition: Int = 0,
    val pageEndPosition: Int = 0,
    val voiceOverrides: List<CharacterProfile> = emptyList(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
)

data class LibraryState(
    val books: List<StoredBook> = emptyList(),
    val latestBookId: String? = null,
) {
    val latestBook: StoredBook?
        get() = books.firstOrNull { it.id == latestBookId }
            ?: books.maxByOrNull { it.lastOpenedAt }
}

data class ReaderSettings(
    val darkTheme: Boolean = true,
    val speechControlsEnabled: Boolean = false,
    val playbackSpeed: Float = 1f,
    val fontSizeSp: Float = 20f,
    val lineHeightSp: Float = 31f,
    val horizontalMarginDp: Float = 24f,
    val topPaddingDp: Float = 22f,
    val bottomPaddingDp: Float = 22f,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SERIF,
    val textAlignment: ReaderTextAlignment = ReaderTextAlignment.LEFT,
    val paragraphSpacingSp: Float = 0f,
    val firstLineIndentSp: Float = 0f,
    val orientation: ReaderOrientation = ReaderOrientation.SYSTEM,
    val keepScreenOn: Boolean = false,
    val imageSize: ReaderImageSize = ReaderImageSize.FIT_WIDTH,
    val imageAlignment: ReaderImageAlignment = ReaderImageAlignment.CENTER,
    val customReaderColors: Boolean = false,
    val readerBackgroundColor: Int = 0xFF090807.toInt(),
    val readerTextColor: Int = 0xFFF4E9D6.toInt(),
    val readerLinkColor: Int = 0xFF78A9FF.toInt(),
    val readerHeadingColor: Int = 0xFFD8A35D.toInt(),
    val readerHighlightColor: Int = 0x66D79A47,
    val sleepCheckMinutes: Int = 5,
    val sleepGraceSeconds: Int = 30,
    val readingMode: ReadingMode = ReadingMode.PAGINATED,
    val speechEngineMode: SpeechEngineMode = SpeechEngineMode.PROCEDURAL,
)

enum class ReaderFontFamily { SERIF, SANS_SERIF, MONOSPACE }
enum class ReaderTextAlignment { LEFT, CENTER, JUSTIFIED }
enum class ReaderOrientation { SYSTEM, PORTRAIT, LANDSCAPE }
enum class ReaderImageSize { ORIGINAL, SMALL, MEDIUM, FIT_WIDTH }
enum class ReaderImageAlignment { LEFT, CENTER, RIGHT }

enum class ReadingMode {
    PAGINATED,
    VERTICAL_SCROLL,
}

enum class SpeechEngineMode {
    PROCEDURAL,
    LOCAL_GENERATIVE,
}

enum class VoiceGroup {
    NARRATOR,
    MALE,
    FEMALE,
    NEUTRAL,
}

/** Structured, optional cues from book analysis; never a claim about a person's real identity. */
data class CharacterVoiceTraits(
    val ageBand: CharacterAgeBand = CharacterAgeBand.UNKNOWN,
    val genderPresentation: GenderPresentation = GenderPresentation.UNKNOWN,
    val vocalRegister: VocalRegister = VocalRegister.UNKNOWN,
)

enum class CharacterAgeBand { CHILD, YOUNG, ADULT, MATURE, ELDER, UNKNOWN }
enum class GenderPresentation { FEMININE, MASCULINE, ANDROGYNOUS, UNKNOWN }
enum class VocalRegister { LOW, MEDIUM, HIGH, UNKNOWN }

data class CharacterProfile(
    val id: String,
    val displayName: String,
    val aliases: Set<String> = setOf(displayName),
    val voiceGroup: VoiceGroup = VoiceGroup.NEUTRAL,
    val voiceSlot: Int = 0,
    val pitch: Float = 1f,
    val speechRate: Float = 1f,
    val proceduralVoiceId: String? = null,
    val localVoicePackId: String? = null,
    val traits: CharacterVoiceTraits? = null,
)

data class SpeechPhrase(
    val index: Int,
    val start: Int,
    val end: Int,
    val text: String,
    val speakerId: String = NARRATOR_ID,
)

const val NARRATOR_ID = "narrator"
const val UNKNOWN_SPEAKER_ID = "unknown"

val NarratorProfile = CharacterProfile(
    id = NARRATOR_ID,
    displayName = "Narrator",
    voiceGroup = VoiceGroup.NARRATOR,
    voiceSlot = 0,
    pitch = 1f,
    speechRate = 0.96f,
)

val UnknownProfile = CharacterProfile(
    id = UNKNOWN_SPEAKER_ID,
    displayName = "Unknown",
    voiceGroup = VoiceGroup.NEUTRAL,
    voiceSlot = 0,
    pitch = 1f,
    speechRate = 1f,
)
