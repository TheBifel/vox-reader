package com.bifel.voxreader.domain.preanalysis.model

import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.PhraseEmotion

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
    PREPARING,
    PARTIAL,
    READY,
    FAILED,
}

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
    val emotion: PhraseEmotion? = null,
)

data class AttributionRevision(
    val runId: AnalysisRunId,
    val bookId: String,
    val providerId: String,
    val active: Boolean,
    val coverage: Float,
    val characters: List<CharacterProfile>,
)

const val PHRASE_MANIFEST_VERSION = 1
const val SEGMENTATION_VERSION = 2
const val ATTRIBUTION_POLICY_VERSION = 1
const val PROVIDER_SCHEMA_VERSION = 1
