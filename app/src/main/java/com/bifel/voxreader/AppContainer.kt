package com.bifel.voxreader

import android.app.Application
import com.bifel.voxreader.data.database.VoxReaderDatabase
import com.bifel.voxreader.data.epub.EpubParser
import com.bifel.voxreader.data.fb2.Fb2Parser
import com.bifel.voxreader.data.epub.LocalBookRepository
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

class AppContainer(application: Application) {
    private val dataStoreRepository = ReaderDataStoreRepository(application)
    private val bookRepository = LocalBookRepository(application, EpubParser(), Fb2Parser())
    private val database = VoxReaderDatabase.create(application)

    val libraryRepository = dataStoreRepository
    val settingsRepository = dataStoreRepository
    private val preAnalysisStore = RoomPreAnalysisStore(database)
    val portableAnalysisService = PortableAnalysisService(preAnalysisStore)
    val speechEngine = AndroidSpeechEngine(application)
    val piperVoicePackManager = PiperVoicePackManager(application)
    val generativeSpeechEngine = SherpaPiperSpeechEngine(application, piperVoicePackManager)
    val advancedVoiceModelManager = AdvancedVoiceModelManager(application)
    val advancedSpeechEngine = SherpaKittenSpeechEngine(application, advancedVoiceModelManager)
    val playbackNotificationController = AndroidPlaybackNotificationController(application)
    val importBook = ImportBookUseCase(bookRepository, libraryRepository)
    val loadLatestBook = LoadLatestBookUseCase(bookRepository, libraryRepository)
}
