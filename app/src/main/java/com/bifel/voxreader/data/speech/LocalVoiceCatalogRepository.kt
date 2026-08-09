package com.bifel.voxreader.data.speech

import com.bifel.voxreader.domain.model.SpeechEngineMode
import com.bifel.voxreader.domain.model.VoiceGroup
import com.bifel.voxreader.domain.speech.ProceduralVoiceCatalog
import com.bifel.voxreader.domain.speech.ProceduralVoiceLanguage
import com.bifel.voxreader.domain.speech.VoiceCatalog
import com.bifel.voxreader.domain.speech.VoiceCatalogRepository
import com.bifel.voxreader.domain.speech.VoiceInstallState
import com.bifel.voxreader.domain.speech.VoiceLanguage
import com.bifel.voxreader.domain.speech.VoiceModel
import com.bifel.voxreader.domain.speech.VoiceOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class LocalVoiceCatalogRepository(
    private val proceduralCatalog: ProceduralVoiceCatalog,
    private val piper: PiperVoicePackManager,
    private val advanced: AdvancedVoiceModelManager,
) : VoiceCatalogRepository {

    override val catalog: Flow<VoiceCatalog> = combine(
        proceduralCatalog.proceduralVoices,
        piper.packs,
        advanced.model,
    ) { procedural, piperPacks, advancedModel ->
        VoiceCatalog(
            models = piperPacks.map(PiperVoicePack::toDomain) + advancedModel.toDomain(),
            voices = procedural.map { voice ->
                VoiceOption(
                    id = voice.id,
                    displayName = voice.displayName,
                    engineMode = SpeechEngineMode.PROCEDURAL,
                    language = voice.language.toDomain(),
                    voiceGroup = VoiceGroup.NEUTRAL,
                )
            } + advanced.voices.map { voice ->
                VoiceOption(
                    id = voice.id,
                    displayName = voice.displayName,
                    engineMode = SpeechEngineMode.LOCAL_ADVANCED,
                    language = VoiceLanguage.ENGLISH,
                    voiceGroup = voice.voiceGroup,
                    speakerId = voice.speakerId,
                )
            },
        )
    }

    override suspend fun loadProceduralVoices() {
        proceduralCatalog.loadVoiceCatalog()
    }

    override suspend fun download(modelId: String) {
        if (modelId == AdvancedVoiceModelManager.MODEL_ID) advanced.download()
        else piper.download(modelId)
    }

    override suspend fun remove(modelId: String) {
        if (modelId == AdvancedVoiceModelManager.MODEL_ID) advanced.remove()
        else piper.remove(modelId)
    }
}

private fun PiperVoicePack.toDomain() = VoiceModel(
    id = id,
    displayName = displayName,
    description = description,
    engineMode = SpeechEngineMode.LOCAL_GENERATIVE,
    language = language.toDomain(),
    voiceGroup = voiceGroup,
    speakerId = speakerId,
    downloadState = downloadState.toDomain(),
    installedSizeBytes = installedSizeBytes,
    downloadProgress = downloadProgress,
    error = error,
)

private fun AdvancedVoiceModel.toDomain() = VoiceModel(
    id = id,
    displayName = displayName,
    description = description,
    engineMode = SpeechEngineMode.LOCAL_ADVANCED,
    downloadState = downloadState.toDomain(),
    downloadSizeBytes = downloadSizeBytes,
    installedSizeBytes = installedSizeBytes,
    downloadProgress = downloadProgress,
    error = error,
)

private fun VoicePackDownloadState.toDomain() = when (this) {
    VoicePackDownloadState.NOT_INSTALLED -> VoiceInstallState.NOT_INSTALLED
    VoicePackDownloadState.DOWNLOADING -> VoiceInstallState.DOWNLOADING
    VoicePackDownloadState.INSTALLED -> VoiceInstallState.INSTALLED
    VoicePackDownloadState.FAILED -> VoiceInstallState.FAILED
}

private fun VoicePackLanguage.toDomain() = when (this) {
    VoicePackLanguage.ENGLISH -> VoiceLanguage.ENGLISH
    VoicePackLanguage.UKRAINIAN -> VoiceLanguage.UKRAINIAN
}

private fun ProceduralVoiceLanguage.toDomain() = when (this) {
    ProceduralVoiceLanguage.ENGLISH -> VoiceLanguage.ENGLISH
    ProceduralVoiceLanguage.UKRAINIAN -> VoiceLanguage.UKRAINIAN
}
