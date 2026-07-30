package com.bifel.voxreader.domain.preanalysis.model

import com.bifel.voxreader.domain.model.CharacterProfile

@JvmInline
value class PhraseId(val value: String)

@JvmInline
value class AnalysisRunId(val value: String)

data class PhraseManifestItem(
    val phraseId: PhraseId,
    val phraseIndex: Int,
    val start: Int,
    val end: Int,
    val normalizedTextHash: String,
)

enum class AnalysisRunStatus {
    QUEUED,
    PREPARING,
    SUBMITTING,
    PROCESSING,
    DOWNLOADING,
    VALIDATING,
    RECONCILING,
    PARTIAL,
    READY,
    CANCELING,
    CANCELED,
    FAILED,
    OUTDATED,
}

fun AnalysisRunStatus.canTransitionTo(next: AnalysisRunStatus): Boolean =
    next in when (this) {
        AnalysisRunStatus.QUEUED -> setOf(
            AnalysisRunStatus.PREPARING,
            AnalysisRunStatus.CANCELING,
            AnalysisRunStatus.FAILED,
        )
        AnalysisRunStatus.PREPARING -> setOf(
            AnalysisRunStatus.SUBMITTING,
            AnalysisRunStatus.PROCESSING,
            AnalysisRunStatus.CANCELING,
            AnalysisRunStatus.FAILED,
        )
        AnalysisRunStatus.SUBMITTING -> setOf(
            AnalysisRunStatus.PROCESSING,
            AnalysisRunStatus.CANCELING,
            AnalysisRunStatus.FAILED,
        )
        AnalysisRunStatus.PROCESSING -> setOf(
            AnalysisRunStatus.DOWNLOADING,
            AnalysisRunStatus.VALIDATING,
            AnalysisRunStatus.PARTIAL,
            AnalysisRunStatus.CANCELING,
            AnalysisRunStatus.FAILED,
        )
        AnalysisRunStatus.DOWNLOADING -> setOf(
            AnalysisRunStatus.VALIDATING,
            AnalysisRunStatus.PARTIAL,
            AnalysisRunStatus.CANCELING,
            AnalysisRunStatus.FAILED,
        )
        AnalysisRunStatus.VALIDATING -> setOf(
            AnalysisRunStatus.RECONCILING,
            AnalysisRunStatus.PARTIAL,
            AnalysisRunStatus.FAILED,
        )
        AnalysisRunStatus.RECONCILING -> setOf(
            AnalysisRunStatus.READY,
            AnalysisRunStatus.PARTIAL,
            AnalysisRunStatus.FAILED,
        )
        AnalysisRunStatus.PARTIAL,
        AnalysisRunStatus.FAILED,
        -> setOf(AnalysisRunStatus.QUEUED, AnalysisRunStatus.OUTDATED)
        AnalysisRunStatus.CANCELING -> setOf(AnalysisRunStatus.CANCELED)
        AnalysisRunStatus.READY -> setOf(AnalysisRunStatus.OUTDATED)
        AnalysisRunStatus.CANCELED,
        AnalysisRunStatus.OUTDATED,
        -> emptySet()
    }

data class AnalysisRun(
    val id: AnalysisRunId,
    val bookId: String,
    val providerId: String,
    val status: AnalysisRunStatus,
    val totalPhrases: Int,
    val completedPhrases: Int,
    val attributedPhrases: Int,
    val active: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val safeErrorCode: String? = null,
)

enum class AttributionSource {
    USER_LOCKED,
    PREANALYSIS_CACHE,
    DETERMINISTIC,
    TINY_BERT,
    UNKNOWN,
}

enum class EvidenceKind {
    USER_LOCKED,
    EXPLICIT_TAG,
    QUOTE_CONTINUATION,
    TURN_PATTERN,
    CONTEXTUAL,
    UNKNOWN,
}

data class SpeakerAttribution(
    val phraseId: PhraseId,
    val characterId: String,
    val confidence: Float,
    val source: AttributionSource,
    val evidence: EvidenceKind,
)

data class AttributionRevision(
    val runId: AnalysisRunId,
    val bookId: String,
    val providerId: String,
    val active: Boolean,
    val coverage: Float,
    val characters: List<CharacterProfile>,
)

data class PreAnalysisRequest(
    val bookId: String,
    val providerId: String,
    val replaceExisting: Boolean = false,
)

data class ProviderDescriptor(
    val id: String,
    val displayName: String,
    val requiresNetwork: Boolean,
    val paid: Boolean,
)

const val PHRASE_MANIFEST_VERSION = 1
const val SEGMENTATION_VERSION = 2
const val ATTRIBUTION_POLICY_VERSION = 1
const val PROVIDER_SCHEMA_VERSION = 1
