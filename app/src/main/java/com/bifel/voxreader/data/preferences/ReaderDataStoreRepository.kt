package com.bifel.voxreader.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.LibraryState
import com.bifel.voxreader.domain.model.ReaderSettings
import com.bifel.voxreader.domain.model.StoredBook
import com.bifel.voxreader.domain.model.SpeechEngineMode
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

    override val library: Flow<LibraryState> = safePreferences.map(::decodeLibrary)

    override val settings: Flow<ReaderSettings> = safePreferences.map { preferences ->
        ReaderSettings(
            darkTheme = preferences[Keys.DarkTheme] ?: true,
            speechControlsEnabled = preferences[Keys.SpeechControls] ?: false,
            playbackSpeed = (preferences[Keys.PlaybackSpeed] ?: 1f).coerceIn(0.5f, 2f),
            fontSizeSp = (preferences[Keys.FontSizeSp] ?: 20f).coerceIn(14f, 32f),
            lineHeightSp = (preferences[Keys.LineHeightSp] ?: 31f).coerceIn(18f, 48f),
            horizontalMarginDp = (preferences[Keys.HorizontalMarginDp] ?: 24f).coerceIn(12f, 56f),
            speechEngineMode = runCatching {
                SpeechEngineMode.valueOf(preferences[Keys.SpeechEngineMode] ?: SpeechEngineMode.PROCEDURAL.name)
            }.getOrDefault(SpeechEngineMode.PROCEDURAL),
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

    override suspend fun setReadingAppearance(fontSizeSp: Float, lineHeightSp: Float, horizontalMarginDp: Float) {
        dataStore.edit {
            it[Keys.FontSizeSp] = fontSizeSp.coerceIn(14f, 32f)
            it[Keys.LineHeightSp] = lineHeightSp.coerceIn(18f, 48f)
            it[Keys.HorizontalMarginDp] = horizontalMarginDp.coerceIn(12f, 56f)
        }
    }

    override suspend fun setSpeechEngineMode(mode: SpeechEngineMode) {
        dataStore.edit { it[Keys.SpeechEngineMode] = mode.name }
    }

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
        )
    }

    private object Keys {
        val DarkTheme = booleanPreferencesKey("dark_theme")
        val SpeechControls = booleanPreferencesKey("speech_controls")
        val PlaybackSpeed = floatPreferencesKey("playback_speed")
        val FontSizeSp = floatPreferencesKey("font_size_sp")
        val LineHeightSp = floatPreferencesKey("line_height_sp")
        val HorizontalMarginDp = floatPreferencesKey("horizontal_margin_dp")
        val SpeechEngineMode = stringPreferencesKey("speech_engine_mode")
        val LatestBookId = stringPreferencesKey("latest_book_id")
        val Books = stringPreferencesKey("books_json")
    }

}
