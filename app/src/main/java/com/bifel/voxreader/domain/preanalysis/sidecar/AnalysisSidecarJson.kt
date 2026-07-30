package com.bifel.voxreader.domain.preanalysis.sidecar

import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NARRATOR_ID
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.model.UNKNOWN_SPEAKER_ID
import com.bifel.voxreader.domain.model.VoiceGroup
import com.bifel.voxreader.domain.preanalysis.PhraseManifestBuilder
import com.bifel.voxreader.domain.preanalysis.ProviderBookResult
import com.bifel.voxreader.domain.preanalysis.model.ATTRIBUTION_POLICY_VERSION
import com.bifel.voxreader.domain.preanalysis.model.AnalysisRunId
import com.bifel.voxreader.domain.preanalysis.model.AttributionSource
import com.bifel.voxreader.domain.preanalysis.model.EvidenceKind
import com.bifel.voxreader.domain.preanalysis.model.PHRASE_MANIFEST_VERSION
import com.bifel.voxreader.domain.preanalysis.model.PROVIDER_SCHEMA_VERSION
import com.bifel.voxreader.domain.preanalysis.model.PhraseId
import com.bifel.voxreader.domain.preanalysis.model.PhraseManifestItem
import com.bifel.voxreader.domain.preanalysis.model.SEGMENTATION_VERSION
import com.bifel.voxreader.domain.preanalysis.model.SpeakerAttribution
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

data class AnalysisSidecarMetadata(
    val bookId: String,
    val providerId: String,
    val createdAt: Long,
    val schemaVersion: Int = SIDECAR_SCHEMA_VERSION,
    val manifestVersion: Int = PHRASE_MANIFEST_VERSION,
    val segmentationVersion: Int = SEGMENTATION_VERSION,
    val attributionPolicyVersion: Int = ATTRIBUTION_POLICY_VERSION,
    val providerSchemaVersion: Int = PROVIDER_SCHEMA_VERSION,
)

data class AnalysisSidecarRow(
    val phraseId: PhraseId,
    val phraseIndex: Int,
    val start: Int,
    val end: Int,
    val textHash: String,
    val text: String,
    val speakerId: String,
    val speakerName: String,
    val voiceGroup: VoiceGroup,
    val voiceSlot: Int,
    val pitch: Float,
    val speechRate: Float,
    val confidence: Float,
    val evidence: EvidenceKind,
    val source: AttributionSource,
)

data class AnalysisSidecarBundle(
    val metadata: AnalysisSidecarMetadata,
    val rows: List<AnalysisSidecarRow>,
) {
    fun validateAgainst(
        bookId: String,
        bookText: String,
    ): List<String> {
        val errors = mutableListOf<String>()
        if (metadata.schemaVersion != SIDECAR_SCHEMA_VERSION) {
            errors += "Unsupported sidecar schema version ${metadata.schemaVersion}."
        }
        if (metadata.bookId != bookId) errors += "The sidecar belongs to a different book."
        if (metadata.manifestVersion != PHRASE_MANIFEST_VERSION) {
            errors += "The phrase manifest version does not match."
        }
        if (metadata.segmentationVersion != SEGMENTATION_VERSION) {
            errors += "The sentence segmentation version does not match."
        }
        if (metadata.attributionPolicyVersion != ATTRIBUTION_POLICY_VERSION) {
            errors += "The attribution policy version does not match."
        }
        if (metadata.providerSchemaVersion != PROVIDER_SCHEMA_VERSION) {
            errors += "The provider schema version does not match."
        }

        if (rows.isEmpty() && bookText.isNotBlank()) {
            errors += "The sidecar contains no phrases."
        }

        val expectedManifest = PhraseManifestBuilder.build(
            bookId = bookId,
            phrases = rows.map { row ->
                SpeechPhrase(
                    index = row.phraseIndex,
                    start = row.start,
                    end = row.end,
                    text = row.text,
                )
            },
        )
        val seenPhraseIds = mutableSetOf<PhraseId>()
        val seenIndexes = mutableSetOf<Int>()
        val characterDefinitions = mutableMapOf<String, CharacterDefinition>()
        var previousEnd = 0
        var coverageCanBeChecked = true
        rows.forEachIndexed { rowIndex, row ->
            val label = "Row ${rowIndex + 1}"
            if (!seenPhraseIds.add(row.phraseId)) errors += "$label duplicates phrase_id."
            if (!seenIndexes.add(row.phraseIndex)) errors += "$label duplicates phrase_index."
            if (row.phraseIndex != rowIndex) {
                errors += "$label is out of order or has a non-contiguous phrase_index."
            }
            if (row.speakerId.isBlank() || row.speakerId.length > MAX_SPEAKER_ID_LENGTH) {
                errors += "$label has an invalid speaker_id."
            }
            if (row.speakerName.isBlank() || row.speakerName.length > MAX_SPEAKER_NAME_LENGTH) {
                errors += "$label has an invalid speaker_name."
            }
            if (!row.confidence.isFinite() || row.confidence !in 0f..1f) {
                errors += "$label has confidence outside 0..1."
            }
            if (!row.pitch.isFinite() || row.pitch !in 0.5f..1.6f) {
                errors += "$label has pitch outside 0.5..1.6."
            }
            if (!row.speechRate.isFinite() || row.speechRate !in 0.5f..1.5f) {
                errors += "$label has speech_rate outside 0.5..1.5."
            }
            if (row.voiceSlot < 0) errors += "$label has a negative voice_slot."

            val expected = expectedManifest[rowIndex]
            if (expected.phraseId != row.phraseId ||
                expected.phraseIndex != row.phraseIndex ||
                expected.start != row.start ||
                expected.end != row.end ||
                expected.normalizedTextHash != row.textHash
            ) {
                errors += "$label has invalid immutable phrase identity fields."
            }

            val validRange = row.start >= 0 &&
                row.end > row.start &&
                row.end <= bookText.length
            if (!validRange) {
                errors += "$label has offsets outside the current book."
                coverageCanBeChecked = false
            } else if (row.start < previousEnd) {
                errors += "$label overlaps or precedes the previous phrase."
                coverageCanBeChecked = false
            } else {
                if (bookText.substring(previousEnd, row.start).any { !it.isWhitespace() }) {
                    errors += "$label leaves non-whitespace book text uncovered."
                }
                if (bookText.substring(row.start, row.end) != row.text) {
                    errors += "$label text does not match the current book."
                }
                previousEnd = row.end
            }

            val definition = CharacterDefinition(
                name = row.speakerName,
                group = row.voiceGroup,
                voiceSlot = row.voiceSlot,
                pitch = row.pitch,
                speechRate = row.speechRate,
            )
            val previous = characterDefinitions.putIfAbsent(row.speakerId, definition)
            if (previous != null && previous != definition) {
                errors += "$label defines speaker_id '${row.speakerId}' inconsistently."
            }
        }
        if (coverageCanBeChecked &&
            bookText.substring(previousEnd).any { !it.isWhitespace() }
        ) {
            errors += "The sidecar does not cover the end of the current book."
        }
        return errors.distinct()
    }

    fun toManifest(): List<PhraseManifestItem> =
        rows.map { row ->
            PhraseManifestItem(
                phraseId = row.phraseId,
                phraseIndex = row.phraseIndex,
                start = row.start,
                end = row.end,
                normalizedTextHash = row.textHash,
            )
        }

    fun toProviderResult(runId: AnalysisRunId): ProviderBookResult {
        val characters = rows
            .distinctBy(AnalysisSidecarRow::speakerId)
            .map { row ->
                CharacterProfile(
                    id = row.speakerId,
                    displayName = row.speakerName,
                    aliases = setOf(row.speakerName),
                    voiceGroup = row.voiceGroup,
                    voiceSlot = row.voiceSlot,
                    pitch = row.pitch,
                    speechRate = row.speechRate,
                )
            }
        val attributions = rows.map { row ->
            SpeakerAttribution(
                phraseId = row.phraseId,
                characterId = row.speakerId,
                confidence = row.confidence,
                source = row.source,
                evidence = row.evidence,
            )
        }
        return ProviderBookResult(
            runId = runId,
            attributions = attributions,
            characters = characters,
        )
    }

    private data class CharacterDefinition(
        val name: String,
        val group: VoiceGroup,
        val voiceSlot: Int,
        val pitch: Float,
        val speechRate: Float,
    )
}

object AnalysisSidecarJson {

    private val codec = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    fun encode(bundle: AnalysisSidecarBundle): String =
        codec.encodeToString(SidecarDocument.serializer(), bundle.toDocument())

    fun decode(content: String): AnalysisSidecarBundle {
        require(content.length <= MAX_SIDECAR_CHARACTERS) { "The sidecar file is too large." }
        val document = codec.decodeFromString(SidecarDocument.serializer(), content)
        require(document.format == SIDECAR_FORMAT) {
            "Not a VoxReader speaker-analysis JSON file."
        }
        require(document.schemaVersion == SIDECAR_SCHEMA_VERSION) {
            "Unsupported sidecar schema version ${document.schemaVersion}."
        }
        require(document.phrases.size <= MAX_SIDECAR_ROWS) {
            "The sidecar has too many phrases."
        }
        return document.toBundle()
    }

    private fun AnalysisSidecarBundle.toDocument() =
        SidecarDocument(
            bookId = metadata.bookId,
            providerId = metadata.providerId,
            createdAt = metadata.createdAt,
            schemaVersion = metadata.schemaVersion,
            manifestVersion = metadata.manifestVersion,
            segmentationVersion = metadata.segmentationVersion,
            attributionPolicyVersion = metadata.attributionPolicyVersion,
            providerSchemaVersion = metadata.providerSchemaVersion,
            phrases = rows.map { it.toDocumentRow() },
        )

    private fun AnalysisSidecarRow.toDocumentRow() =
        SidecarPhrase(
            phraseId = phraseId.value,
            phraseIndex = phraseIndex,
            start = start,
            end = end,
            textHash = textHash,
            text = text,
            speakerId = speakerId,
            speakerName = speakerName,
            voiceGroup = voiceGroup.name,
            voiceSlot = voiceSlot,
            pitch = pitch,
            speechRate = speechRate,
            confidence = confidence,
            evidence = evidence.name,
            source = source.name,
        )

    private fun SidecarDocument.toBundle() =
        AnalysisSidecarBundle(
            metadata = AnalysisSidecarMetadata(
                bookId = bookId,
                providerId = providerId,
                createdAt = createdAt,
                schemaVersion = schemaVersion,
                manifestVersion = manifestVersion,
                segmentationVersion = segmentationVersion,
                attributionPolicyVersion = attributionPolicyVersion,
                providerSchemaVersion = providerSchemaVersion,
            ),
            rows = phrases.mapIndexed { index, row -> row.toDomain(index) },
        )

    private fun SidecarPhrase.toDomain(index: Int) =
        AnalysisSidecarRow(
            phraseId = PhraseId(phraseId),
            phraseIndex = phraseIndex,
            start = start,
            end = end,
            textHash = textHash,
            text = text,
            speakerId = speakerId,
            speakerName = speakerName,
            voiceGroup = voiceGroup.requiredEnum("voice_group", index),
            voiceSlot = voiceSlot,
            pitch = pitch,
            speechRate = speechRate,
            confidence = confidence,
            evidence = evidence.requiredEnum("evidence", index),
            source = source.requiredEnum("source", index),
        )

    private inline fun <reified T : Enum<T>> String.requiredEnum(
        name: String,
        row: Int,
    ): T = enumValues<T>().firstOrNull { it.name == this }
        ?: error("Invalid $name on JSON phrase ${row + 1}.")
}

const val SIDECAR_SCHEMA_VERSION = 1
const val SIDECAR_FORMAT = "voxreader-speaker-analysis"
private const val MAX_SIDECAR_ROWS = 250_000
private const val MAX_SIDECAR_CHARACTERS = 64 * 1024 * 1024
private const val MAX_SPEAKER_ID_LENGTH = 128
private const val MAX_SPEAKER_NAME_LENGTH = 160

fun unknownSidecarRow(
    item: PhraseManifestItem,
    text: String,
): AnalysisSidecarRow =
    AnalysisSidecarRow(
        phraseId = item.phraseId,
        phraseIndex = item.phraseIndex,
        start = item.start,
        end = item.end,
        textHash = item.normalizedTextHash,
        text = text,
        speakerId = UNKNOWN_SPEAKER_ID,
        speakerName = "Unknown",
        voiceGroup = VoiceGroup.NEUTRAL,
        voiceSlot = 0,
        pitch = 1f,
        speechRate = 1f,
        confidence = 0f,
        evidence = EvidenceKind.UNKNOWN,
        source = AttributionSource.UNKNOWN,
    )

fun narratorSidecarRow(
    item: PhraseManifestItem,
    text: String,
): AnalysisSidecarRow =
    unknownSidecarRow(item, text).copy(
        speakerId = NARRATOR_ID,
        speakerName = "Narrator",
        voiceGroup = VoiceGroup.NARRATOR,
        confidence = 1f,
        evidence = EvidenceKind.EXPLICIT_TAG,
        source = AttributionSource.DETERMINISTIC,
    )

@Serializable
private data class SidecarDocument(
    val format: String = SIDECAR_FORMAT,
    @SerialName("schema_version")
    val schemaVersion: Int = SIDECAR_SCHEMA_VERSION,
    @SerialName("book_id")
    val bookId: String,
    @SerialName("provider_id")
    val providerId: String,
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("manifest_version")
    val manifestVersion: Int,
    @SerialName("segmentation_version")
    val segmentationVersion: Int,
    @SerialName("attribution_policy_version")
    val attributionPolicyVersion: Int,
    @SerialName("provider_schema_version")
    val providerSchemaVersion: Int,
    val phrases: List<SidecarPhrase>,
)

@Serializable
private data class SidecarPhrase(
    @SerialName("phrase_id")
    val phraseId: String,
    @SerialName("phrase_index")
    val phraseIndex: Int,
    val start: Int,
    val end: Int,
    @SerialName("text_hash")
    val textHash: String,
    val text: String,
    @SerialName("speaker_id")
    val speakerId: String,
    @SerialName("speaker_name")
    val speakerName: String,
    @SerialName("voice_group")
    val voiceGroup: String,
    @SerialName("voice_slot")
    val voiceSlot: Int,
    val pitch: Float,
    @SerialName("speech_rate")
    val speechRate: Float,
    val confidence: Float,
    val evidence: String,
    val source: String,
    val emotion: JsonElement? = null,
)
