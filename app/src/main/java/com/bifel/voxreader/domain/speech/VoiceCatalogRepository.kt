package com.bifel.voxreader.domain.speech

import com.bifel.voxreader.domain.model.SpeechEngineMode
import com.bifel.voxreader.domain.model.VoiceGroup
import kotlinx.coroutines.flow.Flow

enum class VoiceLanguage { ENGLISH, UKRAINIAN }
enum class VoiceInstallState { NOT_INSTALLED, DOWNLOADING, INSTALLED, FAILED }

data class VoiceModel(
    val id: String,
    val displayName: String,
    val description: String,
    val engineMode: SpeechEngineMode,
    val language: VoiceLanguage = VoiceLanguage.ENGLISH,
    val voiceGroup: VoiceGroup = VoiceGroup.NEUTRAL,
    val speakerId: Int = 0,
    val downloadState: VoiceInstallState = VoiceInstallState.NOT_INSTALLED,
    val downloadSizeBytes: Long? = null,
    val installedSizeBytes: Long? = null,
    val downloadProgress: Float? = null,
    val error: String? = null,
) {
    val installed: Boolean get() = downloadState == VoiceInstallState.INSTALLED
}

data class VoiceOption(
    val id: String,
    val displayName: String,
    val engineMode: SpeechEngineMode,
    val language: VoiceLanguage,
    val voiceGroup: VoiceGroup,
    val speakerId: Int = 0,
)

data class VoiceCatalog(
    val models: List<VoiceModel> = emptyList(),
    val voices: List<VoiceOption> = emptyList(),
)

/** Engine-neutral catalog and installation boundary used by presentation. */
interface VoiceCatalogRepository {
    val catalog: Flow<VoiceCatalog>
    suspend fun loadProceduralVoices()
    suspend fun download(modelId: String)
    suspend fun remove(modelId: String)
}
