package com.bifel.voxreader.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.LibraryState
import com.bifel.voxreader.domain.model.ReaderSettings
import com.bifel.voxreader.domain.model.ReaderFontFamily
import com.bifel.voxreader.domain.model.ReaderTextAlignment
import com.bifel.voxreader.domain.model.ReaderOrientation
import com.bifel.voxreader.domain.model.ReaderImageSize
import com.bifel.voxreader.domain.model.ReaderImageAlignment
import com.bifel.voxreader.domain.model.ReadingMode
import com.bifel.voxreader.domain.model.StoredBook
import com.bifel.voxreader.domain.model.SpeechEngineMode
import com.bifel.voxreader.domain.model.EmotionProcessingProfile
import com.bifel.voxreader.domain.model.EmotionType
import com.bifel.voxreader.domain.model.SpeechPostProcessingSettings
import com.bifel.voxreader.domain.model.defaultEmotionProcessingProfiles
import com.bifel.voxreader.domain.model.VoiceGroup
import com.bifel.voxreader.domain.repository.LibraryRepository
import com.bifel.voxreader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reader_state",
)

class ReaderDataStoreRepository(
    context: Context,
) : LibraryRepository, SettingsRepository {

    private val dataStore = context.readerDataStore

    private val safePreferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    private val library: Flow<LibraryState> = safePreferences.map(::decodeLibrary)

    override val settings: Flow<ReaderSettings> = safePreferences.map { preferences ->
        ReaderSettings(
            darkTheme = preferences[Keys.DarkTheme] ?: true,
            speechControlsEnabled = preferences[Keys.SpeechControls] ?: false,
            playbackSpeed = (preferences[Keys.PlaybackSpeed] ?: 1f).coerceIn(0.5f, 2f),
            fontSizeSp = (preferences[Keys.FontSizeSp] ?: 20f).coerceIn(14f, 32f),
            lineHeightSp = (preferences[Keys.LineHeightSp] ?: 31f).coerceIn(18f, 48f),
            horizontalMarginDp = (preferences[Keys.HorizontalMarginDp] ?: 24f).coerceIn(12f, 56f),
            topPaddingDp = (preferences[Keys.TopPaddingDp] ?: 22f).coerceIn(0f, 80f),
            bottomPaddingDp = (preferences[Keys.BottomPaddingDp] ?: 22f).coerceIn(0f, 80f),
            fontFamily = enumValue(preferences[Keys.FontFamily], ReaderFontFamily.SERIF),
            textAlignment = enumValue(preferences[Keys.TextAlignment], ReaderTextAlignment.LEFT),
            paragraphSpacingSp = (preferences[Keys.ParagraphSpacingSp] ?: 0f).coerceIn(0f, 32f),
            firstLineIndentSp = (preferences[Keys.FirstLineIndentSp] ?: 0f).coerceIn(0f, 64f),
            orientation = enumValue(preferences[Keys.Orientation], ReaderOrientation.SYSTEM),
            keepScreenOn = preferences[Keys.KeepScreenOn] ?: false,
            imageSize = enumValue(preferences[Keys.ImageSize], ReaderImageSize.FIT_WIDTH),
            imageAlignment = enumValue(preferences[Keys.ImageAlignment], ReaderImageAlignment.CENTER),
            customReaderColors = preferences[Keys.CustomReaderColors] ?: false,
            readerBackgroundColor = preferences[Keys.ReaderBackgroundColor] ?: 0xFF090807.toInt(),
            readerTextColor = preferences[Keys.ReaderTextColor] ?: 0xFFF4E9D6.toInt(),
            readerLinkColor = preferences[Keys.ReaderLinkColor] ?: 0xFF78A9FF.toInt(),
            readerHeadingColor = preferences[Keys.ReaderHeadingColor] ?: 0xFFD8A35D.toInt(),
            readerHighlightColor = preferences[Keys.ReaderHighlightColor] ?: 0x66D79A47,
            sleepCheckMinutes = (preferences[Keys.SleepCheckMinutes] ?: 5).coerceIn(1, 60),
            sleepGraceSeconds = (preferences[Keys.SleepGraceSeconds] ?: 30).coerceIn(10, 120),
            readingMode = runCatching {
                ReadingMode.valueOf(preferences[Keys.ReadingMode] ?: ReadingMode.PAGINATED.name)
            }.getOrDefault(ReadingMode.PAGINATED),
            speechEngineMode = runCatching {
                SpeechEngineMode.valueOf(preferences[Keys.SpeechEngineMode] ?: SpeechEngineMode.PROCEDURAL.name)
            }.getOrDefault(SpeechEngineMode.PROCEDURAL),
            speechPostProcessing = decodeSpeechPostProcessing(preferences),
        )
    }

    override suspend fun currentLibrary(): LibraryState = library.first()

    override suspend fun saveImportedBook(book: BookDocument) {
        dataStore.edit { preferences ->
            val library = decodeLibrary(preferences)
            val existing = library.books.firstOrNull { it.id == book.id }
            val now = System.currentTimeMillis()
            val stored = existing?.copy(
                title = book.title,
                localPath = book.localPath,
                readingPosition = 0,
                pageEndPosition = 0,
                voiceOverrides = emptyList(),
                lastOpenedAt = now,
            ) ?: StoredBook(
                id = book.id,
                title = book.title,
                localPath = book.localPath,
                lastOpenedAt = now,
            )
            preferences[Keys.Books] = encodeBooks(
                library.books.filterNot { it.id == book.id } + stored,
            )
            preferences[Keys.LatestBookId] = book.id
        }
    }

    override suspend fun markOpened(bookId: String) {
        updateBook(bookId) { it.copy(lastOpenedAt = System.currentTimeMillis()) }
        dataStore.edit { it[Keys.LatestBookId] = bookId }
    }

    override suspend fun updateReadingPosition(
        bookId: String,
        pageStart: Int,
        pageEnd: Int,
    ) {
        updateBook(bookId) {
            it.copy(
                readingPosition = pageStart.coerceAtLeast(0),
                pageEndPosition = pageEnd.coerceAtLeast(pageStart),
                lastOpenedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun updateVoiceOverride(
        bookId: String,
        profile: CharacterProfile,
    ) {
        updateBook(bookId) { book ->
            book.copy(
                voiceOverrides = book.voiceOverrides
                    .filterNot { it.id == profile.id } + profile,
            )
        }
    }

    override suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { it[Keys.DarkTheme] = enabled }
    }

    override suspend fun setSpeechControlsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SpeechControls] = enabled }
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        dataStore.edit { it[Keys.PlaybackSpeed] = speed.coerceIn(0.5f, 2f) }
    }

    override suspend fun setReadingAppearance(fontSizeSp: Float, lineHeightSp: Float, horizontalMarginDp: Float, topPaddingDp: Float, bottomPaddingDp: Float) {
        dataStore.edit {
            it[Keys.FontSizeSp] = fontSizeSp.coerceIn(14f, 32f)
            it[Keys.LineHeightSp] = lineHeightSp.coerceIn(18f, 48f)
            it[Keys.HorizontalMarginDp] = horizontalMarginDp.coerceIn(12f, 56f)
            it[Keys.TopPaddingDp] = topPaddingDp.coerceIn(0f, 80f)
            it[Keys.BottomPaddingDp] = bottomPaddingDp.coerceIn(0f, 80f)
        }
    }

    override suspend fun setReadingMode(mode: ReadingMode) {
        dataStore.edit { it[Keys.ReadingMode] = mode.name }
    }

    override suspend fun setTypography(fontFamily: ReaderFontFamily, alignment: ReaderTextAlignment, paragraphSpacingSp: Float, firstLineIndentSp: Float) {
        dataStore.edit {
            it[Keys.FontFamily] = fontFamily.name
            it[Keys.TextAlignment] = alignment.name
            it[Keys.ParagraphSpacingSp] = paragraphSpacingSp.coerceIn(0f, 32f)
            it[Keys.FirstLineIndentSp] = firstLineIndentSp.coerceIn(0f, 64f)
        }
    }

    override suspend fun setDisplayBehavior(orientation: ReaderOrientation, keepScreenOn: Boolean) {
        dataStore.edit {
            it[Keys.Orientation] = orientation.name
            it[Keys.KeepScreenOn] = keepScreenOn
        }
    }

    override suspend fun setImageAppearance(size: ReaderImageSize, alignment: ReaderImageAlignment) {
        dataStore.edit {
            it[Keys.ImageSize] = size.name
            it[Keys.ImageAlignment] = alignment.name
        }
    }

    override suspend fun setReaderColors(enabled: Boolean, background: Int, text: Int, link: Int, heading: Int, highlight: Int) {
        dataStore.edit {
            it[Keys.CustomReaderColors] = enabled
            it[Keys.ReaderBackgroundColor] = background
            it[Keys.ReaderTextColor] = text
            it[Keys.ReaderLinkColor] = link
            it[Keys.ReaderHeadingColor] = heading
            it[Keys.ReaderHighlightColor] = highlight
        }
    }

    override suspend fun setSleepTimerSettings(checkMinutes: Int, graceSeconds: Int) {
        dataStore.edit {
            it[Keys.SleepCheckMinutes] = checkMinutes.coerceIn(1, 60)
            it[Keys.SleepGraceSeconds] = graceSeconds.coerceIn(10, 120)
        }
    }

    override suspend fun setSpeechEngineMode(mode: SpeechEngineMode) {
        dataStore.edit { it[Keys.SpeechEngineMode] = mode.name }
    }

    override suspend fun setSpeechPostProcessing(settings: SpeechPostProcessingSettings) {
        val normalized = settings.profiles.mapValues { it.value.normalized() }
        dataStore.edit {
            it[Keys.SpeechPostProcessingEnabled] = settings.enabled
            it[Keys.SpeechPostProcessingProfiles] = encodeSpeechPostProcessing(normalized)
        }
    }

    private fun decodeSpeechPostProcessing(preferences: Preferences): SpeechPostProcessingSettings {
        val defaults = defaultEmotionProcessingProfiles()
        val stored = runCatching {
            val json = JSONObject(preferences[Keys.SpeechPostProcessingProfiles] ?: "{}")
            buildMap {
                EmotionType.entries.forEach { type ->
                    val value = json.optJSONObject(type.name) ?: return@forEach
                    put(
                        type,
                        EmotionProcessingProfile(
                            speed = value.optDouble("speed", defaults.getValue(type).speed.toDouble()).toFloat(),
                            pitch = value.optDouble("pitch", defaults.getValue(type).pitch.toDouble()).toFloat(),
                            loudness = value.optDouble("loudness", defaults.getValue(type).loudness.toDouble()).toFloat(),
                            tone = value.optDouble("tone", defaults.getValue(type).tone.toDouble()).toFloat(),
                            pauseAfterMillis = value.optLong("pauseAfterMillis", defaults.getValue(type).pauseAfterMillis),
                        ).normalized(),
                    )
                }
            }
        }.getOrDefault(emptyMap())
        return SpeechPostProcessingSettings(
            enabled = preferences[Keys.SpeechPostProcessingEnabled] ?: false,
            profiles = defaults + stored,
        )
    }

    private fun encodeSpeechPostProcessing(
        profiles: Map<EmotionType, EmotionProcessingProfile>,
    ): String = JSONObject().apply {
        EmotionType.entries.forEach { type ->
            val profile = profiles[type] ?: return@forEach
            put(
                type.name,
                JSONObject().apply {
                    put("speed", profile.speed.toDouble())
                    put("pitch", profile.pitch.toDouble())
                    put("loudness", profile.loudness.toDouble())
                    put("tone", profile.tone.toDouble())
                    put("pauseAfterMillis", profile.pauseAfterMillis)
                },
            )
        }
    }.toString()

    private suspend fun updateBook(
        bookId: String,
        transform: (StoredBook) -> StoredBook,
    ) {
        dataStore.edit { preferences ->
            val library = decodeLibrary(preferences)
            preferences[Keys.Books] = encodeBooks(
                library.books.map { if (it.id == bookId) transform(it) else it },
            )
        }
    }

    private fun decodeLibrary(preferences: Preferences): LibraryState {
        val books = runCatching {
            val array = JSONArray(preferences[Keys.Books] ?: "[]")
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toStoredBook())
                }
            }
        }.getOrDefault(emptyList())

        return LibraryState(
            books = books,
            latestBookId = preferences[Keys.LatestBookId],
        )
    }

    private fun encodeBooks(books: List<StoredBook>): String =
        JSONArray().apply {
            books.forEach { put(it.toJson()) }
        }.toString()

    private fun StoredBook.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("localPath", localPath)
        put("readingPosition", readingPosition)
        put("pageEndPosition", pageEndPosition)
        put("lastOpenedAt", lastOpenedAt)
        put(
            "voiceOverrides",
            JSONArray().apply {
                voiceOverrides.forEach { put(it.toJson()) }
            },
        )
    }

    private fun CharacterProfile.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("displayName", displayName)
        put("aliases", JSONArray(aliases.toList()))
        put("voiceGroup", voiceGroup.name)
        put("voiceSlot", voiceSlot)
        put("pitch", pitch.toDouble())
        put("speechRate", speechRate.toDouble())
        put("proceduralVoiceId", proceduralVoiceId)
        put("localVoicePackId", localVoicePackId)
        put("advancedVoiceId", advancedVoiceId)
    }

    private fun JSONObject.toStoredBook(): StoredBook {
        val overrideArray = optJSONArray("voiceOverrides") ?: JSONArray()
        val voiceOverrides = buildList {
            for (index in 0 until overrideArray.length()) {
                add(overrideArray.getJSONObject(index).toCharacterProfile())
            }
        }
        return StoredBook(
            id = getString("id"),
            title = optString("title", "Untitled book"),
            localPath = getString("localPath"),
            readingPosition = optInt("readingPosition", 0),
            pageEndPosition = optInt("pageEndPosition", 0),
            voiceOverrides = voiceOverrides,
            lastOpenedAt = optLong("lastOpenedAt", 0L),
        )
    }

    private fun JSONObject.toCharacterProfile(): CharacterProfile {
        val aliasArray = optJSONArray("aliases") ?: JSONArray()
        val aliases = buildSet {
            for (index in 0 until aliasArray.length()) {
                add(aliasArray.getString(index))
            }
        }
        val displayName = optString("displayName", "Character")
        return CharacterProfile(
            id = getString("id"),
            displayName = displayName,
            aliases = aliases.ifEmpty { setOf(displayName) },
            voiceGroup = runCatching {
                VoiceGroup.valueOf(optString("voiceGroup", VoiceGroup.NEUTRAL.name))
            }.getOrDefault(VoiceGroup.NEUTRAL),
            voiceSlot = optInt("voiceSlot", 0),
            pitch = optDouble("pitch", 1.0).toFloat(),
            speechRate = optDouble("speechRate", 1.0).toFloat(),
            proceduralVoiceId = optString("proceduralVoiceId").takeIf(String::isNotBlank),
            localVoicePackId = optString("localVoicePackId").takeIf(String::isNotBlank),
            advancedVoiceId = optString("advancedVoiceId").takeIf(String::isNotBlank),
        )
    }

    private object Keys {
        val DarkTheme = booleanPreferencesKey("dark_theme")
        val SpeechControls = booleanPreferencesKey("speech_controls")
        val PlaybackSpeed = floatPreferencesKey("playback_speed")
        val FontSizeSp = floatPreferencesKey("font_size_sp")
        val LineHeightSp = floatPreferencesKey("line_height_sp")
        val HorizontalMarginDp = floatPreferencesKey("horizontal_margin_dp")
        val TopPaddingDp = floatPreferencesKey("top_padding_dp")
        val BottomPaddingDp = floatPreferencesKey("bottom_padding_dp")
        val FontFamily = stringPreferencesKey("font_family")
        val TextAlignment = stringPreferencesKey("text_alignment")
        val ParagraphSpacingSp = floatPreferencesKey("paragraph_spacing_sp")
        val FirstLineIndentSp = floatPreferencesKey("first_line_indent_sp")
        val Orientation = stringPreferencesKey("reader_orientation")
        val KeepScreenOn = booleanPreferencesKey("keep_screen_on")
        val ImageSize = stringPreferencesKey("image_size")
        val ImageAlignment = stringPreferencesKey("image_alignment")
        val CustomReaderColors = booleanPreferencesKey("custom_reader_colors")
        val ReaderBackgroundColor = intPreferencesKey("reader_background_color")
        val ReaderTextColor = intPreferencesKey("reader_text_color")
        val ReaderLinkColor = intPreferencesKey("reader_link_color")
        val ReaderHeadingColor = intPreferencesKey("reader_heading_color")
        val ReaderHighlightColor = intPreferencesKey("reader_highlight_color")
        val SleepCheckMinutes = intPreferencesKey("sleep_check_minutes")
        val SleepGraceSeconds = intPreferencesKey("sleep_grace_seconds")
        val ReadingMode = stringPreferencesKey("reading_mode")
        val SpeechEngineMode = stringPreferencesKey("speech_engine_mode")
        val SpeechPostProcessingEnabled = booleanPreferencesKey("speech_post_processing_enabled")
        val SpeechPostProcessingProfiles = stringPreferencesKey("speech_post_processing_profiles")
        val LatestBookId = stringPreferencesKey("latest_book_id")
        val Books = stringPreferencesKey("books_json")
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

}
