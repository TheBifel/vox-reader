package com.bifel.voxreader.domain.preanalysis.sidecar

import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NARRATOR_ID
import com.bifel.voxreader.domain.model.UNKNOWN_SPEAKER_ID
import com.bifel.voxreader.domain.model.VoiceGroup
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
import java.util.Locale

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
        manifest: List<PhraseManifestItem>,
        textByPhraseId: Map<PhraseId, String>,
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

        val manifestById = manifest.associateBy(PhraseManifestItem::phraseId)
        val seenPhraseIds = mutableSetOf<PhraseId>()
        val seenIndexes = mutableSetOf<Int>()
        val characterDefinitions = mutableMapOf<String, CharacterDefinition>()
        rows.forEachIndexed { rowIndex, row ->
            val label = "Row ${rowIndex + 1}"
            if (!seenPhraseIds.add(row.phraseId)) errors += "$label duplicates phrase_id."
            if (!seenIndexes.add(row.phraseIndex)) errors += "$label duplicates phrase_index."
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

            val expected = manifestById[row.phraseId]
            when {
                expected == null -> errors += "$label references an unknown phrase_id."
                expected.phraseIndex != row.phraseIndex ||
                    expected.start != row.start ||
                    expected.end != row.end ||
                    expected.normalizedTextHash != row.textHash ->
                    errors += "$label does not match the current phrase manifest."
            }
            val expectedText = textByPhraseId[row.phraseId]
            if (row.text.isNotEmpty() && expectedText != null && row.text != expectedText) {
                errors += "$label text does not match the current book."
            }

            if (row.speakerId != UNKNOWN_SPEAKER_ID) {
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
        }
        return errors.distinct()
    }

    fun toProviderResult(runId: AnalysisRunId): ProviderBookResult {
        val accepted = rows.filter { it.speakerId != UNKNOWN_SPEAKER_ID }
        val characters = accepted
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
        val attributions = accepted.map { row ->
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

object AnalysisSidecarTsv {

    fun encode(bundle: AnalysisSidecarBundle): String = buildString {
        appendLine("# voxreader_speaker_analysis_tsv=$SIDECAR_SCHEMA_VERSION")
        appendLine("# book_id=${bundle.metadata.bookId}")
        appendLine("# provider_id=${escape(bundle.metadata.providerId)}")
        appendLine("# created_at=${bundle.metadata.createdAt}")
        appendLine("# manifest_version=${bundle.metadata.manifestVersion}")
        appendLine("# segmentation_version=${bundle.metadata.segmentationVersion}")
        appendLine("# attribution_policy_version=${bundle.metadata.attributionPolicyVersion}")
        appendLine("# provider_schema_version=${bundle.metadata.providerSchemaVersion}")
        appendLine(COLUMNS.joinToString("\t"))
        bundle.rows.forEach { row ->
            appendLine(
                listOf(
                    row.phraseId.value,
                    row.phraseIndex.toString(),
                    row.start.toString(),
                    row.end.toString(),
                    row.textHash,
                    row.text,
                    row.speakerId,
                    row.speakerName,
                    row.voiceGroup.name,
                    row.voiceSlot.toString(),
                    row.pitch.toString(),
                    row.speechRate.toString(),
                    row.confidence.toString(),
                    row.evidence.name,
                    row.source.name,
                ).joinToString("\t", transform = ::escape),
            )
        }
    }

    fun decode(tsv: String): AnalysisSidecarBundle {
        require(tsv.length <= MAX_SIDECAR_CHARACTERS) { "The sidecar file is too large." }
        val metadata = linkedMapOf<String, String>()
        val dataLines = mutableListOf<String>()
        tsv.lineSequence().forEach { rawLine ->
            val line = rawLine.removeSuffix("\r").removePrefix("\uFEFF")
            when {
                line.isBlank() -> Unit
                line.startsWith("# ") -> {
                    val separator = line.indexOf('=')
                    require(separator > 2) { "Invalid sidecar metadata line." }
                    metadata[line.substring(2, separator)] =
                        unescape(line.substring(separator + 1))
                }
                else -> dataLines += line
            }
        }
        require(metadata["voxreader_speaker_analysis_tsv"]?.toIntOrNull() == SIDECAR_SCHEMA_VERSION) {
            "Not a supported VoxReader speaker-analysis TSV file."
        }
        require(dataLines.isNotEmpty()) { "The sidecar has no TSV header." }
        require(dataLines.first().split('\t') == COLUMNS) { "The sidecar TSV header is invalid." }
        require(dataLines.size - 1 <= MAX_SIDECAR_ROWS) { "The sidecar has too many rows." }

        val rows = dataLines.drop(1).mapIndexed { index, line ->
            val values = line.split('\t')
            require(values.size == COLUMNS.size) {
                "TSV row ${index + 1} has ${values.size} columns; expected ${COLUMNS.size}."
            }
            val cells = values.map(::unescape)
            AnalysisSidecarRow(
                phraseId = PhraseId(cells[0]),
                phraseIndex = cells[1].requiredInt("phrase_index", index),
                start = cells[2].requiredInt("start", index),
                end = cells[3].requiredInt("end", index),
                textHash = cells[4],
                text = cells[5],
                speakerId = cells[6],
                speakerName = cells[7],
                voiceGroup = cells[8].requiredEnum("voice_group", index),
                voiceSlot = cells[9].requiredInt("voice_slot", index),
                pitch = cells[10].requiredFloat("pitch", index),
                speechRate = cells[11].requiredFloat("speech_rate", index),
                confidence = cells[12].requiredFloat("confidence", index),
                evidence = cells[13].requiredEnum("evidence", index),
                source = cells[14].requiredEnum("source", index),
            )
        }
        return AnalysisSidecarBundle(
            metadata = AnalysisSidecarMetadata(
                bookId = metadata.required("book_id"),
                providerId = metadata.required("provider_id"),
                createdAt = metadata.required("created_at").toLong(),
                schemaVersion = metadata.required("voxreader_speaker_analysis_tsv").toInt(),
                manifestVersion = metadata.required("manifest_version").toInt(),
                segmentationVersion = metadata.required("segmentation_version").toInt(),
                attributionPolicyVersion =
                    metadata.required("attribution_policy_version").toInt(),
                providerSchemaVersion = metadata.required("provider_schema_version").toInt(),
            ),
            rows = rows,
        )
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            append(
                when (character) {
                    '\\' -> "\\\\"
                    '\t' -> "\\t"
                    '\n' -> "\\n"
                    '\r' -> "\\r"
                    else -> character
                },
            )
        }
    }

    private fun unescape(value: String): String = buildString(value.length) {
        var escaping = false
        value.forEach { character ->
            if (escaping) {
                append(
                    when (character) {
                        't' -> '\t'
                        'n' -> '\n'
                        'r' -> '\r'
                        '\\' -> '\\'
                        else -> error("Invalid TSV escape sequence \\$character.")
                    },
                )
                escaping = false
            } else if (character == '\\') {
                escaping = true
            } else {
                append(character)
            }
        }
        require(!escaping) { "Invalid trailing TSV escape." }
    }

    private fun Map<String, String>.required(key: String): String =
        requireNotNull(this[key]) { "Missing sidecar metadata '$key'." }

    private fun String.requiredInt(name: String, row: Int): Int =
        toIntOrNull() ?: error("Invalid $name on TSV row ${row + 1}.")

    private fun String.requiredFloat(name: String, row: Int): Float =
        toFloatOrNull() ?: error("Invalid $name on TSV row ${row + 1}.")

    private inline fun <reified T : Enum<T>> String.requiredEnum(
        name: String,
        row: Int,
    ): T = enumValues<T>().firstOrNull { it.name == uppercase(Locale.ROOT) }
        ?: error("Invalid $name on TSV row ${row + 1}.")

    private val COLUMNS = listOf(
        "phrase_id",
        "phrase_index",
        "start",
        "end",
        "text_hash",
        "text",
        "speaker_id",
        "speaker_name",
        "voice_group",
        "voice_slot",
        "pitch",
        "speech_rate",
        "confidence",
        "evidence",
        "source",
    )
}

const val SIDECAR_SCHEMA_VERSION = 1
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
