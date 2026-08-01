package com.bifel.voxreader.data.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "analysis_run",
    indices = [
        Index("bookId"),
        Index(value = ["bookId", "active"]),
        Index(value = ["bookId", "updatedAt"]),
    ],
)
data class AnalysisRunEntity(
    @androidx.room.PrimaryKey val runId: String,
    val bookId: String,
    val providerId: String,
    val providerModelId: String?,
    val status: String,
    val totalPhrases: Int,
    val completedPhrases: Int,
    val attributedPhrases: Int,
    val active: Boolean,
    val cancelRequested: Boolean,
    val manifestVersion: Int,
    val segmentationVersion: Int,
    val attributionPolicyVersion: Int,
    val providerSchemaVersion: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val safeErrorCode: String?,
)

@Entity(
    tableName = "phrase_manifest",
    primaryKeys = ["bookId", "phraseId"],
    indices = [
        Index(value = ["bookId", "phraseIndex"], unique = true),
        Index(value = ["bookId", "start", "end"]),
    ],
)
data class PhraseManifestEntity(
    val bookId: String,
    val phraseId: String,
    val phraseIndex: Int,
    val start: Int,
    val end: Int,
    val normalizedTextHash: String,
    val manifestVersion: Int,
    val segmentationVersion: Int,
)

@Entity(
    tableName = "phrase_attribution",
    primaryKeys = ["runId", "phraseId"],
    indices = [
        Index("bookId"),
        Index(value = ["bookId", "phraseId"]),
        Index("characterId"),
    ],
)
data class PhraseAttributionEntity(
    val runId: String,
    val bookId: String,
    val phraseId: String,
    val characterId: String,
    val confidence: Float,
    val source: String,
    val evidence: String,
    val emotionType: String?,
    val emotionIntensity: Float?,
    val emotionConfidence: Float?,
    val createdAt: Long,
)

@Entity(
    tableName = "character_registry",
    primaryKeys = ["bookId", "runId", "characterId"],
    indices = [
        Index(value = ["bookId", "runId"]),
        Index(value = ["bookId", "displayName"]),
    ],
)
data class CharacterEntity(
    val bookId: String,
    val runId: String,
    val characterId: String,
    val displayName: String,
    val aliases: String,
    val voiceGroup: String,
    val voiceSlot: Int,
    val pitch: Float,
    val speechRate: Float,
    val ageBand: String?,
    val genderPresentation: String?,
    val vocalRegister: String?,
)
