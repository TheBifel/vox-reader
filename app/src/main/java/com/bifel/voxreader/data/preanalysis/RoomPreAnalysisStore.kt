package com.bifel.voxreader.data.preanalysis

import androidx.room.withTransaction
import com.bifel.voxreader.data.database.VoxReaderDatabase
import com.bifel.voxreader.data.database.entity.AnalysisRunEntity
import com.bifel.voxreader.data.database.entity.CharacterEntity
import com.bifel.voxreader.data.database.entity.PhraseAttributionEntity
import com.bifel.voxreader.data.database.entity.PhraseManifestEntity
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.CharacterVoiceTraits
import com.bifel.voxreader.domain.model.CharacterAgeBand
import com.bifel.voxreader.domain.model.GenderPresentation
import com.bifel.voxreader.domain.model.VocalRegister
import com.bifel.voxreader.domain.model.EmotionType
import com.bifel.voxreader.domain.model.PhraseEmotion
import com.bifel.voxreader.domain.model.VoiceGroup
import com.bifel.voxreader.domain.preanalysis.model.ATTRIBUTION_POLICY_VERSION
import com.bifel.voxreader.domain.preanalysis.model.AnalysisRunId
import com.bifel.voxreader.domain.preanalysis.model.AnalysisRunStatus
import com.bifel.voxreader.domain.preanalysis.model.AttributionRevision
import com.bifel.voxreader.domain.preanalysis.model.AttributionSource
import com.bifel.voxreader.domain.preanalysis.model.EvidenceKind
import com.bifel.voxreader.domain.preanalysis.model.PHRASE_MANIFEST_VERSION
import com.bifel.voxreader.domain.preanalysis.model.PROVIDER_SCHEMA_VERSION
import com.bifel.voxreader.domain.preanalysis.model.PhraseId
import com.bifel.voxreader.domain.preanalysis.model.PhraseManifestItem
import com.bifel.voxreader.domain.preanalysis.model.SEGMENTATION_VERSION
import com.bifel.voxreader.domain.preanalysis.model.SpeakerAttribution
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomPreAnalysisStore(
    private val database: VoxReaderDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val runDao = database.analysisRunDao()
    private val manifestDao = database.phraseManifestDao()
    private val attributionDao = database.phraseAttributionDao()
    private val characterDao = database.characterDao()

    suspend fun clearBook(bookId: String) {
        database.withTransaction {
            runDao.deactivateBook(bookId, clock())
            manifestDao.deleteForBook(bookId)
        }
    }

    suspend fun createRun(
        bookId: String,
        providerId: String,
        totalPhrases: Int,
    ): AnalysisRunId {
        val id = AnalysisRunId(UUID.randomUUID().toString())
        val now = clock()
        runDao.upsert(
            AnalysisRunEntity(
                runId = id.value,
                bookId = bookId,
                providerId = providerId,
                providerModelId = providerId,
                status = AnalysisRunStatus.PREPARING.name,
                totalPhrases = totalPhrases,
                completedPhrases = 0,
                attributedPhrases = 0,
                active = false,
                cancelRequested = false,
                manifestVersion = PHRASE_MANIFEST_VERSION,
                segmentationVersion = SEGMENTATION_VERSION,
                attributionPolicyVersion = ATTRIBUTION_POLICY_VERSION,
                providerSchemaVersion = PROVIDER_SCHEMA_VERSION,
                createdAt = now,
                updatedAt = now,
                completedAt = null,
                safeErrorCode = null,
            ),
        )
        return id
    }

    fun observeActiveRevision(bookId: String): Flow<AttributionRevision?> =
        combine(
            runDao.observeActive(bookId),
            characterDao.observeActive(bookId),
        ) { run, characters ->
            run?.let {
                AttributionRevision(
                    runId = AnalysisRunId(it.runId),
                    bookId = it.bookId,
                    providerId = it.providerId,
                    active = it.active,
                    coverage = if (it.totalPhrases == 0) {
                        0f
                    } else {
                        it.attributedPhrases.toFloat() / it.totalPhrases
                    },
                    characters = characters.map { it.toDomain() },
                )
            }
        }

    suspend fun manifest(bookId: String): List<PhraseManifestItem> =
        manifestDao.forBook(bookId).map { it.toDomain() }

    suspend fun replaceManifest(bookId: String, manifest: List<PhraseManifestItem>) {
        database.withTransaction {
            manifestDao.deleteForBook(bookId)
            manifestDao.upsert(manifest.map { it.toEntity(bookId) })
        }
    }

    suspend fun activeAttributions(bookId: String): Map<PhraseId, SpeakerAttribution> =
        attributionDao.activeForBook(bookId).associate { entity ->
            val attribution = entity.toDomain()
            attribution.phraseId to attribution
        }

    suspend fun activateRevisionWithManifest(
        runId: AnalysisRunId,
        manifest: List<PhraseManifestItem>,
    ) {
        database.withTransaction {
            val run = requireNotNull(runDao.find(runId.value)) {
                "Unknown analysis run ${runId.value}"
            }
            val status = run.status.toRunStatus()
            require(status == AnalysisRunStatus.READY || status == AnalysisRunStatus.PARTIAL) {
                "Only a validated READY or PARTIAL revision can be activated."
            }
            manifestDao.deleteForBook(run.bookId)
            manifestDao.upsert(manifest.map { it.toEntity(run.bookId) })
            val now = clock()
            runDao.deactivateBook(run.bookId, now)
            runDao.activate(run.runId, now)
        }
    }

    suspend fun stageValidatedRevision(
        runId: AnalysisRunId,
        attributions: List<SpeakerAttribution>,
        characters: List<CharacterProfile>,
    ) {
        database.withTransaction {
            val run = requireNotNull(runDao.find(runId.value))
            attributionDao.upsert(
                attributions.map { it.toEntity(run.bookId, runId.value, clock()) },
            )
            characterDao.upsert(characters.map { it.toEntity(run.bookId, runId.value) })
            val now = clock()
            runDao.upsert(
                run.copy(
                    status = if (attributions.size == run.totalPhrases) {
                        AnalysisRunStatus.READY.name
                    } else {
                        AnalysisRunStatus.PARTIAL.name
                    },
                    completedPhrases = run.totalPhrases,
                    attributedPhrases = attributions.size,
                    completedAt = now,
                    updatedAt = now,
                    safeErrorCode = null,
                ),
            )
        }
    }

    private fun PhraseManifestItem.toEntity(bookId: String) =
        PhraseManifestEntity(
            bookId = bookId,
            phraseId = phraseId.value,
            phraseIndex = phraseIndex,
            start = start,
            end = end,
            normalizedTextHash = normalizedTextHash,
            manifestVersion = PHRASE_MANIFEST_VERSION,
            segmentationVersion = SEGMENTATION_VERSION,
        )

    private fun PhraseManifestEntity.toDomain() =
        PhraseManifestItem(
            phraseId = PhraseId(phraseId),
            phraseIndex = phraseIndex,
            start = start,
            end = end,
            normalizedTextHash = normalizedTextHash,
        )

    private fun SpeakerAttribution.toEntity(
        bookId: String,
        runId: String,
        createdAt: Long,
    ) = PhraseAttributionEntity(
        runId = runId,
        bookId = bookId,
        phraseId = phraseId.value,
        characterId = characterId,
        confidence = confidence,
        source = source.name,
        evidence = evidence.name,
        emotionType = emotion?.type?.name,
        emotionIntensity = emotion?.intensity,
        emotionConfidence = emotion?.confidence,
        createdAt = createdAt,
    )

    private fun PhraseAttributionEntity.toDomain() =
        SpeakerAttribution(
            phraseId = PhraseId(phraseId),
            characterId = characterId,
            confidence = confidence,
            source = enumValueOrDefault(source, AttributionSource.UNKNOWN),
            evidence = enumValueOrDefault(evidence, EvidenceKind.UNKNOWN),
            emotion = emotionType?.let { type ->
                val intensity = emotionIntensity ?: return@let null
                val confidence = emotionConfidence ?: return@let null
                val emotion = runCatching { EmotionType.valueOf(type) }.getOrNull()
                    ?: return@let null
                PhraseEmotion(
                    type = emotion,
                    intensity = intensity,
                    confidence = confidence,
                )
            },
        )

    private fun CharacterProfile.toEntity(bookId: String, runId: String) =
        CharacterEntity(
            bookId = bookId,
            runId = runId,
            characterId = id,
            displayName = displayName,
            aliases = aliases.joinToString(ALIAS_SEPARATOR),
            voiceGroup = voiceGroup.name,
            voiceSlot = voiceSlot,
            pitch = pitch,
            speechRate = speechRate,
            ageBand = traits?.ageBand?.name,
            genderPresentation = traits?.genderPresentation?.name,
            vocalRegister = traits?.vocalRegister?.name,
        )

    private fun CharacterEntity.toDomain() =
        CharacterProfile(
            id = characterId,
            displayName = displayName,
            aliases = aliases
                .split(ALIAS_SEPARATOR)
                .filter(String::isNotBlank)
                .toSet()
                .ifEmpty { setOf(displayName) },
            voiceGroup = enumValueOrDefault(voiceGroup, VoiceGroup.NEUTRAL),
            voiceSlot = voiceSlot,
            pitch = pitch,
            speechRate = speechRate,
            traits = ageBand?.let {
                CharacterVoiceTraits(
                    ageBand = enumValueOrDefault(it, CharacterAgeBand.UNKNOWN),
                    genderPresentation = enumValueOrDefault(genderPresentation.orEmpty(), GenderPresentation.UNKNOWN),
                    vocalRegister = enumValueOrDefault(vocalRegister.orEmpty(), VocalRegister.UNKNOWN),
                )
            },
        )

    private fun String.toRunStatus(): AnalysisRunStatus =
        enumValueOrDefault(this, AnalysisRunStatus.FAILED)

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        value: String,
        fallback: T,
    ): T = runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)

    private companion object {
        const val ALIAS_SEPARATOR = "\u001f"
    }
}
