package com.bifel.voxreader

import android.app.Application
import com.bifel.voxreader.data.analysis.RuleBasedCharacterAnalyzer
import com.bifel.voxreader.data.epub.EpubParser
import com.bifel.voxreader.data.epub.LocalBookRepository
import com.bifel.voxreader.data.preferences.ReaderDataStoreRepository
import com.bifel.voxreader.data.speech.AndroidSpeechEngine
import com.bifel.voxreader.domain.usecase.ImportBookUseCase
import com.bifel.voxreader.domain.usecase.LoadLatestBookUseCase

class AppContainer(application: Application) {
    private val dataStoreRepository = ReaderDataStoreRepository(application)
    private val bookRepository = LocalBookRepository(application, EpubParser())

    val libraryRepository = dataStoreRepository
    val settingsRepository = dataStoreRepository
    val characterAnalyzer = RuleBasedCharacterAnalyzer()
    val speechEngine = AndroidSpeechEngine(application)
    val importBook = ImportBookUseCase(bookRepository, libraryRepository)
    val loadLatestBook = LoadLatestBookUseCase(bookRepository, libraryRepository)
}

