package com.bifel.voxreader.domain.preanalysis

import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.preanalysis.model.AnalysisRun
import com.bifel.voxreader.domain.preanalysis.model.AnalysisRunId
import com.bifel.voxreader.domain.preanalysis.model.AttributionRevision
import com.bifel.voxreader.domain.preanalysis.model.PhraseId
import com.bifel.voxreader.domain.preanalysis.model.PhraseManifestItem
import com.bifel.voxreader.domain.preanalysis.model.PreAnalysisRequest
import com.bifel.voxreader.domain.preanalysis.model.ProviderDescriptor
import com.bifel.voxreader.domain.preanalysis.model.SpeakerAttribution
import kotlinx.coroutines.flow.Flow

interface PreAnalysisRepository {
    fun observeLatestRun(bookId: String): Flow<AnalysisRun?>
    suspend fun createRun(request: PreAnalysisRequest): AnalysisRunId
    suspend fun requestCancellation(runId: AnalysisRunId)
}

interface AttributionRepository {
    fun observeActiveRevision(bookId: String): Flow<AttributionRevision?>

    suspend fun replaceManifest(
        bookId: String,
        items: List<PhraseManifestItem>,
    )

    suspend fun findByPhraseIds(
        bookId: String,
        phraseIds: Collection<PhraseId>,
    ): Map<PhraseId, SpeakerAttribution>

    suspend fun activateRevision(runId: AnalysisRunId)
}

interface CharacterRegistryRepository {
    suspend fun characters(bookId: String, runId: AnalysisRunId?): List<CharacterProfile>
    suspend fun upsertCharacters(
        bookId: String,
        runId: AnalysisRunId,
        characters: List<CharacterProfile>,
    )
}

interface BookPreAnalysisProvider {
    val descriptor: ProviderDescriptor
    suspend fun analyze(request: ProviderBookRequest): ProviderBookResult
}

data class ProviderBookRequest(
    val runId: AnalysisRunId,
    val bookId: String,
    val manifest: List<PhraseManifestItem>,
    val textByPhraseId: Map<PhraseId, String>,
    val knownCharacters: List<CharacterProfile>,
)

data class ProviderBookResult(
    val runId: AnalysisRunId,
    val attributions: List<SpeakerAttribution>,
    val characters: List<CharacterProfile>,
)
