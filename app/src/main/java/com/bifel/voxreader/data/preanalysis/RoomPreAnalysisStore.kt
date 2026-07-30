package com.bifel.voxreader.data.preanalysis

import androidx.room.withTransaction
import com.bifel.voxreader.data.database.VoxReaderDatabase
import com.bifel.voxreader.data.database.entity.AnalysisRunEntity
import com.bifel.voxreader.data.database.entity.CharacterEntity
import com.bifel.voxreader.data.database.entity.PhraseAttributionEntity
import com.bifel.voxreader.data.database.entity.PhraseManifestEntity
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.VoiceGroup
import com.bifel.voxreader.domain.preanalysis.AttributionRepository
import com.bifel.voxreader.domain.preanalysis.CharacterRegistryRepository
import com.bifel.voxreader.domain.preanalysis.PreAnalysisRepository
import com.bifel.voxreader.domain.preanalysis.model.ATTRIBUTION_POLICY_VERSION
import com.bifel.voxreader.domain.preanalysis.model.AnalysisRun
import com.bifel.voxreader.domain.preanalysis.model.AnalysisRunId
import com.bifel.voxreader.domain.preanalysis.model.AnalysisRunStatus
import com.bifel.voxreader.domain.preanalysis.model.AttributionRevision
import com.bifel.voxreader.domain.preanalysis.model.AttributionSource
import com.bifel.voxreader.domain.preanalysis.model.EvidenceKind
import com.bifel.voxreader.domain.preanalysis.model.PHRASE_MANIFEST_VERSION
import com.bifel.voxreader.domain.preanalysis.model.PROVIDER_SCHEMA_VERSION
import com.bifel.voxreader.domain.preanalysis.model.PhraseId
import com.bifel.voxreader.domain.preanalysis.model.PhraseManifestItem
import com.bifel.voxreader.domain.preanalysis.model.PreAnalysisRequest
import com.bifel.voxreader.domain.preanalysis.model.SEGMENTATION_VERSION
import com.bifel.voxreader.domain.preanalysis.model.SpeakerAttribution
import com.bifel.voxreader.domain.preanalysis.model.canTransitionTo
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomPreAnalysisStore(
    private val database: VoxReaderDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : PreAnalysisRepository, AttributionRepository, CharacterRegistryRepository {

    private val runDao = database.analysisRunDao()
    private val manifestDao = database.phraseManifestDao()
    private val attributionDao = database.phraseAttributionDao()
    private val characterDao = database.characterDao()

    override fun observeLatestRun(bookId: String): Flow<AnalysisRun?> =
        runDao.observeLatest(bookId).map { it?.toDomain() }

    override suspend fun createRun(request: PreAnalysisRequest): AnalysisRunId {
        val id = AnalysisRunId(UUID.randomUUID().toString())
        val now = clock()
        runDao.upsert(
            AnalysisRunEntity(
                runId = id.value,
                bookId = request.bookId,
                providerId = request.providerId,
                providerModelId = null,
                status = AnalysisRunStatus.QUEUED.name,
                totalPhrases = 0,
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

    override suspend fun requestCancellation(runId: AnalysisRunId) {
        val current = runDao.find(runId.value) ?: return
        val status = current.status.toRunStatus()
        if (!status.canTransitionTo(AnalysisRunStatus.CANCELING)) return
        runDao.updateStatus(
            runId = runId.value,
            status = AnalysisRunStatus.CANCELING.name,
            cancelRequested = true,
            updatedAt = clock(),
        )
    }

    override fun observeActiveRevision(bookId: String): Flow<AttributionRevision?> =
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

    override suspend fun replaceManifest(
        bookId: String,
        items: List<PhraseManifestItem>,
    ) {
        database.withTransaction {
            manifestDao.deleteForBook(bookId)
            manifestDao.upsert(items.map { it.toEntity(bookId) })
        }
    }

    suspend fun manifest(bookId: String): List<PhraseManifestItem> =
        manifestDao.forBook(bookId).map { it.toDomain() }

    override suspend fun findByPhraseIds(
        bookId: String,
        phraseIds: Collection<PhraseId>,
    ): Map<PhraseId, SpeakerAttribution> {
        if (phraseIds.isEmpty()) return emptyMap()
        return attributionDao.activeForPhraseIds(
            bookId = bookId,
            phraseIds = phraseIds.map(PhraseId::value),
        ).associate { entity ->
            val attribution = entity.toDomain()
            attribution.phraseId to attribution
        }
    }

    suspend fun activeAttributions(bookId: String): Map<PhraseId, SpeakerAttribution> =
        attributionDao.activeForBook(bookId).associate { entity ->
            val attribution = entity.toDomain()
            attribution.phraseId to attribution
        }

    override suspend fun activateRevision(runId: AnalysisRunId) {
        database.withTransaction {
            val run = requireNotNull(runDao.find(runId.value)) {
                "Unknown analysis run ${runId.value}"
            }
            val status = run.status.toRunStatus()
            require(status == AnalysisRunStatus.READY || status == AnalysisRunStatus.PARTIAL) {
                "Only a validated READY or PARTIAL revision can be activated."
            }
            val now = clock()
            runDao.deactivateBook(run.bookId, now)
            runDao.activate(run.runId, now)
        }
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

    override suspend fun characters(
        bookId: String,
        runId: AnalysisRunId?,
    ): List<CharacterProfile> {
        val resolvedRunId = runId?.value ?: runDao.findActive(bookId)?.runId ?: return emptyList()
        return characterDao.forRun(bookId, resolvedRunId).map { it.toDomain() }
    }

    override suspend fun upsertCharacters(
        bookId: String,
        runId: AnalysisRunId,
        characters: List<CharacterProfile>,
    ) {
        characterDao.upsert(characters.map { it.toEntity(bookId, runId.value) })
    }

    suspend fun prepareRun(
        runId: AnalysisRunId,
        totalPhrases: Int,
        providerModelId: String?,
    ) {
        val current = requireNotNull(runDao.find(runId.value))
        val next = AnalysisRunStatus.PREPARING
        require(current.status.toRunStatus().canTransitionTo(next))
        runDao.upsert(
            current.copy(
                providerModelId = providerModelId,
                status = next.name,
                totalPhrases = totalPhrases,
                updatedAt = clock(),
            ),
        )
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

    private fun AnalysisRunEntity.toDomain(): AnalysisRun =
        AnalysisRun(
            id = AnalysisRunId(runId),
            bookId = bookId,
            providerId = providerId,
            status = status.toRunStatus(),
            totalPhrases = totalPhrases,
            completedPhrases = completedPhrases,
            attributedPhrases = attributedPhrases,
            active = active,
            createdAt = createdAt,
            updatedAt = updatedAt,
            safeErrorCode = safeErrorCode,
        )

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
        createdAt = createdAt,
    )

    private fun PhraseAttributionEntity.toDomain() =
        SpeakerAttribution(
            phraseId = PhraseId(phraseId),
            characterId = characterId,
            confidence = confidence,
            source = enumValueOrDefault(source, AttributionSource.UNKNOWN),
            evidence = enumValueOrDefault(evidence, EvidenceKind.UNKNOWN),
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
