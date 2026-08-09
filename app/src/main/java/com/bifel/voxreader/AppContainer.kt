package com.bifel.voxreader

import android.app.Application
import com.bifel.voxreader.data.database.VoxReaderDatabase
import com.bifel.voxreader.data.epub.EpubParser
import com.bifel.voxreader.data.fb2.Fb2Parser
import com.bifel.voxreader.data.epub.LocalBookRepository
import com.bifel.voxreader.data.epub.LocalBookAssetRepository
import com.bifel.voxreader.data.preanalysis.RoomPreAnalysisStore
import com.bifel.voxreader.data.preanalysis.PortableAnalysisService
import com.bifel.voxreader.data.preferences.ReaderDataStoreRepository
import com.bifel.voxreader.data.speech.AndroidSpeechEngine
import com.bifel.voxreader.data.speech.SherpaPiperSpeechEngine
import com.bifel.voxreader.data.speech.PiperVoicePackManager
import com.bifel.voxreader.data.speech.AndroidPlaybackNotificationController
import com.bifel.voxreader.data.speech.AdvancedVoiceModelManager
import com.bifel.voxreader.data.speech.SherpaKittenSpeechEngine
import com.bifel.voxreader.domain.usecase.ImportBookUseCase
import com.bifel.voxreader.domain.usecase.LoadLatestBookUseCase
import com.bifel.voxreader.domain.model.SpeechEngineMode
import com.bifel.voxreader.domain.speech.DefaultSpeechEngineRegistry
import com.bifel.voxreader.domain.speech.SpeechBackendAvailability
import com.bifel.voxreader.application.reader.ReaderSessionCoordinator
import com.bifel.voxreader.data.speech.LocalVoiceCatalogRepository
import com.bifel.voxreader.application.reader.ReaderPlaybackControllerFactory

class AppContainer(application: Application) {
    private val dataStoreRepository = ReaderDataStoreRepository(application)
    private val bookRepository = LocalBookRepository(application, EpubParser(), Fb2Parser())
    val bookAssetRepository = LocalBookAssetRepository()
    private val database = VoxReaderDatabase.create(application)

    private val libraryRepository = dataStoreRepository
    val settingsRepository = dataStoreRepository
    private val preAnalysisStore = RoomPreAnalysisStore(database)
    private val portableAnalysisService = PortableAnalysisService(preAnalysisStore)
    private val speechEngine = AndroidSpeechEngine(application)
    private val piperVoicePackManager = PiperVoicePackManager(application)
    private val generativeSpeechEngine = SherpaPiperSpeechEngine(application, piperVoicePackManager)
    private val advancedVoiceModelManager = AdvancedVoiceModelManager(application)
    private val advancedSpeechEngine = SherpaKittenSpeechEngine(application, advancedVoiceModelManager)
    val voiceCatalogRepository = LocalVoiceCatalogRepository(
        proceduralCatalog = speechEngine,
        piper = piperVoicePackManager,
        advanced = advancedVoiceModelManager,
    )
    private val speechEngineRegistry = DefaultSpeechEngineRegistry(
        engines = mapOf(
            SpeechEngineMode.PROCEDURAL to speechEngine,
            SpeechEngineMode.LOCAL_GENERATIVE to generativeSpeechEngine,
            SpeechEngineMode.LOCAL_ADVANCED to advancedSpeechEngine,
        ),
        availability = { mode ->
            when (mode) {
                SpeechEngineMode.PROCEDURAL -> SpeechBackendAvailability(true)
                SpeechEngineMode.LOCAL_GENERATIVE -> SpeechBackendAvailability(
                    available = piperVoicePackManager.installedPacks().isNotEmpty(),
                    unavailableMessage = "Local AI voice is selected, but no compatible voice model is installed yet.",
                )
                SpeechEngineMode.LOCAL_ADVANCED -> SpeechBackendAvailability(
                    available = advancedVoiceModelManager.installed(),
                    unavailableMessage = "Advanced AI is selected, but KittenTTS Micro is not installed yet.",
                )
            }
        },
    )
    private val playbackNotificationController = AndroidPlaybackNotificationController(application)
    val readerPlaybackControllerFactory = ReaderPlaybackControllerFactory(
        engines = speechEngineRegistry,
        notification = playbackNotificationController,
    )
    private val importBook = ImportBookUseCase(bookRepository, libraryRepository)
    private val loadLatestBook = LoadLatestBookUseCase(bookRepository, libraryRepository)
    val readerSessionCoordinator = ReaderSessionCoordinator(
        importBook = importBook,
        loadLatestBook = loadLatestBook,
        libraryRepository = libraryRepository,
        analysisRepository = portableAnalysisService,
    )
}
