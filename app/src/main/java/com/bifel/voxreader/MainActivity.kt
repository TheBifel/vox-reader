package com.bifel.voxreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bifel.voxreader.presentation.reader.ReaderScreen
import com.bifel.voxreader.presentation.reader.ReaderViewModel
import com.bifel.voxreader.presentation.reader.ReaderActions
import com.bifel.voxreader.presentation.reader.ReaderAnalysisActions
import com.bifel.voxreader.presentation.reader.ReaderChromeActions
import com.bifel.voxreader.presentation.reader.ReaderPlaybackActions
import com.bifel.voxreader.presentation.reader.ReaderPlatformActions
import com.bifel.voxreader.presentation.reader.ReaderReadingActions
import com.bifel.voxreader.presentation.reader.ReaderSettingsActions
import com.bifel.voxreader.presentation.reader.ReaderVoiceActions
import com.bifel.voxreader.presentation.theme.VoxReaderTheme
import com.bifel.voxreader.domain.model.ReaderOrientation
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val viewModel: ReaderViewModel by viewModels {
        val container = (application as VoxReaderApplication).container
        ReaderViewModel.Factory(
            settingsRepository = container.settingsRepository,
            voiceCatalogRepository = container.voiceCatalogRepository,
            readerSessionCoordinator = container.readerSessionCoordinator,
            playbackControllerFactory = container.readerPlaybackControllerFactory,
        )
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

    private val openBookLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.openBook(it.toString()) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.initialize(intent.bookUri())

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            DisposableEffect(state.orientation, state.keepScreenOn) {
                requestedOrientation = when (state.orientation) {
                    ReaderOrientation.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    ReaderOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    ReaderOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                if (state.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { }
            }
            VoxReaderTheme(darkTheme = state.darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    ReaderScreen(
                        state = state,
                        actions = readerActions(),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.bookUri()?.let(viewModel::openBook)
    }

    private fun readerActions(): ReaderActions {
        val container = (application as VoxReaderApplication).container
        return ReaderActions(
            chrome = ReaderChromeActions(
                toggle = viewModel::toggleChrome,
                hide = viewModel::hideChrome,
                dismissError = viewModel::dismissError,
            ),
            reading = ReaderReadingActions(
                pageChanged = viewModel::onPageChanged,
                setMode = viewModel::setReadingMode,
                historyBack = viewModel::goBackInHistory,
                historyForward = viewModel::goForwardInHistory,
                openInternalLink = viewModel::openInternalLink,
                registerUserActivity = viewModel::registerUserActivity,
                loadImage = container.bookAssetRepository::readImage,
            ),
            playback = ReaderPlaybackActions(
                enable = viewModel::enableSpeechControls,
                disable = viewModel::disableSpeechControls,
                playPause = viewModel::playOrPause,
                previousPhrase = viewModel::previousPhrase,
                nextPhrase = viewModel::nextPhrase,
                setSpeed = viewModel::setPlaybackSpeed,
                toggleSleepTimer = viewModel::toggleSleepTimer,
            ),
            voices = ReaderVoiceActions(
                setEngineMode = viewModel::setSpeechEngineMode,
                setPostProcessing = viewModel::setSpeechPostProcessing,
                downloadLocal = viewModel::downloadLocalVoice,
                removeLocal = viewModel::removeLocalVoice,
                previewLocal = viewModel::previewLocalVoice,
                downloadAdvanced = viewModel::downloadAdvancedModel,
                removeAdvanced = viewModel::removeAdvancedModel,
                previewAdvanced = viewModel::previewAdvancedVoice,
                loadProcedural = viewModel::loadProceduralVoices,
                previewProcedural = viewModel::previewProceduralVoice,
                updateCharacter = viewModel::updateCharacterVoice,
                previewCharacter = viewModel::previewCharacterVoice,
            ),
            settings = ReaderSettingsActions(
                toggleTheme = viewModel::toggleTheme,
                setReadingAppearance = viewModel::setReadingAppearance,
                setTypography = viewModel::setTypography,
                setDisplayBehavior = viewModel::setDisplayBehavior,
                setImageAppearance = viewModel::setImageAppearance,
                setColors = viewModel::setReaderColors,
                setSleepTimer = viewModel::setSleepTimerSettings,
            ),
            analysis = ReaderAnalysisActions(
                import = {
                    importAnalysisLauncher.launch(
                        arrayOf("application/json", "text/json", "application/octet-stream"),
                    )
                },
                speakerAt = viewModel::speakerAt,
            ),
            platform = ReaderPlatformActions(
                openBook = {
                    openBookLauncher.launch(
                        arrayOf(
                            "application/epub+zip",
                            "application/x-epub+zip",
                            "application/x-fictionbook+xml",
                            "application/fb2+xml",
                            "application/octet-stream",
                        ),
                    )
                },
                openExternalLink = { url ->
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        .onFailure(viewModel::reportAnalysisFileError)
                },
            ),
        )
    }

    private fun Intent?.bookUri(): String? =
        this?.dataString?.takeIf { action == Intent.ACTION_VIEW }

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
