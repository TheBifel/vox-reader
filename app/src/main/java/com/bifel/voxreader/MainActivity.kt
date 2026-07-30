package com.bifel.voxreader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bifel.voxreader.presentation.reader.ReaderScreen
import com.bifel.voxreader.presentation.reader.ReaderViewModel
import com.bifel.voxreader.presentation.theme.VoxReaderTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ReaderViewModel by viewModels {
        val container = (application as VoxReaderApplication).container
        ReaderViewModel.Factory(
            importBook = container.importBook,
            loadLatestBook = container.loadLatestBook,
            libraryRepository = container.libraryRepository,
            settingsRepository = container.settingsRepository,
            characterAnalyzer = container.characterAnalyzer,
            speechEngine = container.speechEngine,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.initialize(intent.epubUri())

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            VoxReaderTheme(darkTheme = state.darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    ReaderScreen(
                        state = state,
                        onToggleChrome = viewModel::toggleChrome,
                        onHideChrome = viewModel::hideChrome,
                        onToggleTheme = viewModel::toggleTheme,
                        onEnableSpeech = viewModel::enableSpeechControls,
                        onDisableSpeech = viewModel::disableSpeechControls,
                        onPlayPause = viewModel::playOrPause,
                        onPreviousPhrase = viewModel::previousPhrase,
                        onNextPhrase = viewModel::nextPhrase,
                        onPageChanged = viewModel::onPageChanged,
                        onDismissError = viewModel::dismissError,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.epubUri()?.let(viewModel::openBook)
    }

    private fun Intent?.epubUri(): String? =
        this?.dataString?.takeIf { action == Intent.ACTION_VIEW }
}
