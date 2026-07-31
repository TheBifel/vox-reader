package com.bifel.voxreader.data.preanalysis

import com.bifel.voxreader.domain.model.BookDocument
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NARRATOR_ID
import com.bifel.voxreader.domain.model.NarratorProfile
import com.bifel.voxreader.domain.model.PhraseEmotion
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.model.UNKNOWN_SPEAKER_ID
import com.bifel.voxreader.domain.preanalysis.PhraseManifestBuilder
import com.bifel.voxreader.domain.preanalysis.model.PhraseManifestItem
import com.bifel.voxreader.domain.preanalysis.model.PreAnalysisRequest
import com.bifel.voxreader.domain.preanalysis.sidecar.AnalysisSidecarBundle
import com.bifel.voxreader.domain.preanalysis.sidecar.AnalysisSidecarMetadata
import com.bifel.voxreader.domain.preanalysis.sidecar.AnalysisSidecarRow
import com.bifel.voxreader.domain.preanalysis.sidecar.AnalysisSidecarJson
import com.bifel.voxreader.domain.preanalysis.sidecar.unknownSidecarRow
import kotlinx.coroutines.flow.first

/**
 * The application boundary shared by manual files, desktop/cloud processing, and the future
 * overnight provider. Every producer ultimately imports the same validated JSON revision.
 */
class PortableAnalysisService(
    private val store: RoomPreAnalysisStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun export(
        book: BookDocument,
        phrases: List<SpeechPhrase>,
    ): String {
        val manifest = PhraseManifestBuilder.build(book.id, phrases)
        val revision = store.observeActiveRevision(book.id).first()
        val cached = store.activeAttributions(book.id)
        val characters = revision
            ?.characters
            .orEmpty()
            .plus(NarratorProfile)
            .associateBy(CharacterProfile::id)
        val textByIndex = phrases.associateBy(SpeechPhrase::index)

        val rows = manifest.map { item ->
            val text = textByIndex[item.phraseIndex]?.text.orEmpty()
            val attribution = cached[item.phraseId]
            val character = attribution?.let { characters[it.characterId] }
            if (attribution == null || character == null) {
                unknownSidecarRow(item, text)
            } else {
                AnalysisSidecarRow(
                    phraseId = item.phraseId,
                    phraseIndex = item.phraseIndex,
                    start = item.start,
                    end = item.end,
                    textHash = item.normalizedTextHash,
                    text = text,
                    speakerId = character.id,
                    speakerName = character.displayName,
                    voiceGroup = character.voiceGroup,
                    voiceSlot = character.voiceSlot,
                    pitch = character.pitch,
                    speechRate = character.speechRate,
                    confidence = attribution.confidence,
                    evidence = attribution.evidence,
                    source = attribution.source,
                    emotion = attribution.emotion,
                )
            }
        }
        return AnalysisSidecarJson.encode(
            AnalysisSidecarBundle(
                metadata = AnalysisSidecarMetadata(
                    bookId = book.id,
                    providerId = revision?.providerId ?: TEMPLATE_PROVIDER_ID,
                    createdAt = clock(),
                ),
                rows = rows,
            ),
        )
    }

    suspend fun import(
        book: BookDocument,
        content: String,
    ): ImportedAnalysis {
        val bundle = AnalysisSidecarJson.decode(content)
        val errors = bundle.validateAgainst(
            bookId = book.id,
            bookText = book.text,
        )
        require(errors.isEmpty()) {
            errors.take(MAX_REPORTED_ERRORS).joinToString(
                prefix = "The analysis file is not compatible with this book:\n",
                separator = "\n",
            )
        }
        val manifest = bundle.toManifest()

        val runId = store.createRun(
            PreAnalysisRequest(
                bookId = book.id,
                providerId = bundle.metadata.providerId,
                replaceExisting = true,
            ),
        )
        store.prepareRun(
            runId = runId,
            totalPhrases = manifest.size,
            providerModelId = bundle.metadata.providerId,
        )
        val result = bundle.toProviderResult(runId)
        store.stageValidatedRevision(
            runId = runId,
            attributions = result.attributions,
            characters = result.characters,
        )
        store.activateRevisionWithManifest(runId, manifest)

        return ImportedAnalysis(
            providerId = bundle.metadata.providerId,
            totalPhrases = manifest.size,
            attributedPhrases = result.attributions.size,
            characterCount = result.characters.count {
                it.id != NARRATOR_ID && it.id != UNKNOWN_SPEAKER_ID
            },
        )
    }

    suspend fun cached(
        book: BookDocument,
        phrases: List<SpeechPhrase>,
    ): CachedAnalysis = cached(book) { phrases }

    /**
     * Restores a persisted phrase manifest without re-segmenting the whole book. The fallback is
     * evaluated only for books that have no usable cached manifest.
     */
    suspend fun cached(
        book: BookDocument,
        fallbackPhrases: () -> List<SpeechPhrase>,
    ): CachedAnalysis {
        val revision = store.observeActiveRevision(book.id).first()
            ?: return CachedAnalysis.empty(fallbackPhrases())
        val storedManifest = store.manifest(book.id)
        val storedPhrases = storedManifest.toSpeechPhrases(book.text)
        val fallback = if (storedPhrases == null) fallbackPhrases() else null
        val manifest = if (storedPhrases != null) {
            storedManifest
        } else {
            PhraseManifestBuilder.build(book.id, requireNotNull(fallback))
        }
        val basePhrases = storedPhrases ?: requireNotNull(fallback)
        val attributions = store.activeAttributions(book.id)
        val attributionByIndex = manifest.mapNotNull { item ->
            attributions[item.phraseId]?.let { item.phraseIndex to it }
        }.toMap()
        val emotionsByPhraseIndex = attributionByIndex.mapNotNull { (index, attribution) ->
            attribution.emotion?.let { index to it }
        }.toMap()
        val resolvedPhrases = basePhrases.map { phrase ->
            phrase.copy(
                speakerId = attributionByIndex[phrase.index]?.characterId ?: phrase.speakerId,
            )
        }
        val characters = revision.characters
            .plus(NarratorProfile)
            .distinctBy(CharacterProfile::id)

        return CachedAnalysis(
            phrases = resolvedPhrases,
            characters = characters,
            cachedPhraseIndices = attributionByIndex.keys,
            emotionsByPhraseIndex = emotionsByPhraseIndex,
            providerId = revision.providerId,
            coverage = revision.coverage,
        )
    }

    suspend fun updateVoiceProfile(
        bookId: String,
        profile: CharacterProfile,
    ) {
        val revision = store.observeActiveRevision(bookId).first() ?: return
        store.upsertCharacters(
            bookId = bookId,
            runId = revision.runId,
            characters = listOf(profile),
        )
    }

    data class ImportedAnalysis(
        val providerId: String,
        val totalPhrases: Int,
        val attributedPhrases: Int,
        val characterCount: Int,
    )

    data class CachedAnalysis(
        val phrases: List<SpeechPhrase>,
        val characters: List<CharacterProfile>,
        val cachedPhraseIndices: Set<Int>,
        val emotionsByPhraseIndex: Map<Int, PhraseEmotion>,
        val providerId: String?,
        val coverage: Float,
    ) {
        companion object {
            fun empty(phrases: List<SpeechPhrase>) =
                CachedAnalysis(
                    phrases = phrases,
                    characters = emptyList(),
                    cachedPhraseIndices = emptySet(),
                    emotionsByPhraseIndex = emptyMap(),
                    providerId = null,
                    coverage = 0f,
                )
        }
    }

    private companion object {
        const val TEMPLATE_PROVIDER_ID = "unassigned-template"
        const val MAX_REPORTED_ERRORS = 8
    }
}

private fun List<PhraseManifestItem>.toSpeechPhrases(
    bookText: String,
): List<SpeechPhrase>? {
    if (isEmpty()) return null
    val phrases = ArrayList<SpeechPhrase>(size)
    var previousEnd = 0
    forEachIndexed { index, item ->
        if (item.phraseIndex != index ||
            item.start < previousEnd ||
            item.start < 0 ||
            item.end <= item.start ||
            item.end > bookText.length
        ) {
            return null
        }
        phrases += SpeechPhrase(
            index = item.phraseIndex,
            start = item.start,
            end = item.end,
            text = bookText.substring(item.start, item.end),
        )
        previousEnd = item.end
    }
    return phrases
}
