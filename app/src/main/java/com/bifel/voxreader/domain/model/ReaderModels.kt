package com.bifel.voxreader.domain.model

data class BookDocument(
    val id: String,
    val title: String,
    val localPath: String,
    val text: String,
)

data class StoredBook(
    val id: String,
    val title: String,
    val localPath: String,
    val readingPosition: Int = 0,
    val pageEndPosition: Int = 0,
    val characters: List<CharacterProfile> = emptyList(),
    val voiceOverrides: List<CharacterProfile> = emptyList(),
    val characterAttributionVersion: Int = 0,
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
)

enum class VoiceGroup {
    NARRATOR,
    MALE,
    FEMALE,
    NEUTRAL,
}

data class CharacterProfile(
    val id: String,
    val displayName: String,
    val aliases: Set<String> = setOf(displayName),
    val voiceGroup: VoiceGroup = VoiceGroup.NEUTRAL,
    val voiceSlot: Int = 0,
    val pitch: Float = 1f,
    val speechRate: Float = 1f,
)

data class SpeechPhrase(
    val index: Int,
    val start: Int,
    val end: Int,
    val text: String,
    val speakerId: String = NARRATOR_ID,
)

data class CharacterAnalysis(
    val phrases: List<SpeechPhrase>,
    val characters: List<CharacterProfile>,
    val analyzedPhraseRange: IntRange? = null,
    val source: CharacterAnalysisSource = CharacterAnalysisSource.RULES,
)

enum class CharacterAnalysisSource {
    RULES,
    TINY_BERT,
}

const val NARRATOR_ID = "narrator"
const val UNKNOWN_SPEAKER_ID = "unknown"
const val CURRENT_CHARACTER_ATTRIBUTION_VERSION = 7

val NarratorProfile = CharacterProfile(
    id = NARRATOR_ID,
    displayName = "Narrator",
    voiceGroup = VoiceGroup.NARRATOR,
    voiceSlot = 0,
    pitch = 1f,
    speechRate = 0.96f,
)
