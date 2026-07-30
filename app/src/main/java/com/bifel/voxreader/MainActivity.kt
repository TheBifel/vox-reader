package com.bifel.voxreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.lifecycleScope
import com.bifel.voxreader.presentation.reader.ReaderScreen
import com.bifel.voxreader.presentation.reader.ReaderViewModel
import com.bifel.voxreader.presentation.theme.VoxReaderTheme
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            portableAnalysisService = container.portableAnalysisService,
        )
    }

    private val exportAnalysisLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/tab-separated-values"),
    ) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            runCatching {
                val content = viewModel.createAnalysisSidecar()
                withContext(Dispatchers.IO) { writeAnalysisFile(uri, content) }
            }.onSuccess {
                viewModel.analysisFileExported()
            }.onFailure(viewModel::reportAnalysisFileError)
        }
    }

    private val importAnalysisLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { readAnalysisFile(uri) }
            }.onSuccess(viewModel::importAnalysisSidecar)
                .onFailure(viewModel::reportAnalysisFileError)
        }
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
                        onExportAnalysis = {
                            exportAnalysisLauncher.launch("voxreader-speaker-analysis.tsv")
                        },
                        onImportAnalysis = {
                            importAnalysisLauncher.launch(
                                arrayOf(
                                    "text/tab-separated-values",
                                    "text/plain",
                                    "text/*",
                                    "application/octet-stream",
                                ),
                            )
                        },
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

    private fun writeAnalysisFile(
        uri: Uri,
        content: String,
    ) {
        val stream = requireNotNull(contentResolver.openOutputStream(uri, "wt")) {
            "The selected analysis file cannot be opened for writing."
        }
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
    }

    private fun readAnalysisFile(uri: Uri): String {
        val stream = requireNotNull(contentResolver.openInputStream(uri)) {
            "The selected analysis file cannot be opened."
        }
        return InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            val output = StringBuilder()
            val buffer = CharArray(8_192)
            var total = 0
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_ANALYSIS_FILE_CHARACTERS) {
                    "The selected analysis file is larger than 64 MB."
                }
                output.append(buffer, 0, count)
            }
            output.toString()
        }
    }

    private companion object {
        const val MAX_ANALYSIS_FILE_CHARACTERS = 64 * 1024 * 1024
    }
}
