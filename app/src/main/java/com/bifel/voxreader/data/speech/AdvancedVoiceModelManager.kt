package com.bifel.voxreader.data.speech

import android.content.Context
import com.bifel.voxreader.domain.model.VoiceGroup
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Owns the advanced neural model independently from the lightweight Piper catalog. */
class AdvancedVoiceModelManager(context: Context) {
    private val root = File(context.applicationContext.filesDir, "advanced-voices/$MODEL_ID")
    private val legacyKokoroRoot = File(
        context.applicationContext.filesDir,
        "advanced-voices/kokoro-int8-multi-lang-v1_0",
    )
    private val mutableModel = MutableStateFlow(loadState())
    val model: StateFlow<AdvancedVoiceModel> = mutableModel.asStateFlow()

    val voices: List<AdvancedVoice> = KITTEN_ENGLISH_VOICES

    fun installed(): Boolean = model.value.installed

    suspend fun download() = withContext(Dispatchers.IO) {
        mutableModel.value = model.value.copy(
            downloadState = VoicePackDownloadState.DOWNLOADING,
            downloadProgress = 0f,
            error = null,
        )
        root.mkdirs()
        try {
            var completedBytes = 0L
            FILES.forEach { remote ->
                val finalFile = File(root, remote.localName)
                val partialFile = File(root, "${remote.localName}.part")
                downloadFile(remote.url, partialFile) { downloaded ->
                    mutableModel.value = model.value.copy(
                        downloadState = VoicePackDownloadState.DOWNLOADING,
                        downloadProgress = ((completedBytes + downloaded).toFloat() / TOTAL_DOWNLOAD_BYTES)
                            .coerceIn(0f, 1f),
                        error = null,
                    )
                }
                check(partialFile.length() >= remote.expectedBytes) {
                    "Downloaded ${remote.localName} is incomplete."
                }
                if (finalFile.exists() && !finalFile.delete()) error("Could not replace ${remote.localName}.")
                if (!partialFile.renameTo(finalFile)) error("Could not finalize ${remote.localName}.")
                completedBytes += remote.expectedBytes
            }
            File(root, FORMAT_MARKER).writeText(FORMAT_VERSION)
            removeLegacyKokoro()
            mutableModel.value = loadState()
        } catch (error: Throwable) {
            root.listFiles { file -> file.name.endsWith(".part") }.orEmpty().forEach(File::delete)
            mutableModel.value = model.value.copy(
                downloadState = VoicePackDownloadState.FAILED,
                downloadProgress = null,
                error = error.message ?: "Advanced model download failed.",
            )
        }
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        check(root.parentFile?.name == "advanced-voices") { "Invalid advanced model directory." }
        root.listFiles().orEmpty().forEach(File::delete)
        root.delete()
        mutableModel.value = loadState()
    }

    fun modelPath(): String = File(root, "model.onnx").absolutePath
    fun voicesPath(): String = File(root, "voices.bin").absolutePath
    fun tokensPath(): String = File(root, "tokens.txt").absolutePath

    private fun removeLegacyKokoro() {
        if (legacyKokoroRoot.name != "kokoro-int8-multi-lang-v1_0") return
        legacyKokoroRoot.listFiles().orEmpty().forEach(File::delete)
        legacyKokoroRoot.delete()
    }

    private fun loadState(): AdvancedVoiceModel {
        val complete = FILES.all { remote ->
            File(root, remote.localName).let { it.isFile && it.length() >= remote.expectedBytes }
        } && File(root, FORMAT_MARKER).readTextOrNull() == FORMAT_VERSION
        return AdvancedVoiceModel(
            id = MODEL_ID,
            displayName = "KittenTTS Micro 0.8",
            description = "Fast 40M-parameter English neural speech with eight character voices, optimized for CPU playback.",
            downloadState = if (complete) VoicePackDownloadState.INSTALLED else VoicePackDownloadState.NOT_INSTALLED,
            downloadSizeBytes = TOTAL_DOWNLOAD_BYTES,
            installedSizeBytes = if (complete) FILES.sumOf { File(root, it.localName).length() } else null,
        )
    }

    private fun downloadFile(source: String, destination: File, onProgress: (Long) -> Unit) {
        val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
        }
        connection.inputStream.use { input ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloaded = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    downloaded += count
                    onProgress(downloaded)
                }
            }
        }
    }

    private fun File.readTextOrNull(): String? = runCatching { readText() }.getOrNull()

    private data class RemoteFile(val localName: String, val expectedBytes: Long) {
        val url: String get() = "$MODEL_BASE_URL/$localName"
    }

    companion object {
        const val MODEL_ID = "kitten-micro-en-v0_8"
        private const val MODEL_BASE_URL =
            "https://huggingface.co/csukuangfj2/kitten-micro-en-v0_8/resolve/main"
        private const val FORMAT_MARKER = ".voxreader-model-format"
        private const val FORMAT_VERSION = "kitten-micro-v0.8-v1"
        private val FILES = listOf(
            RemoteFile("model.onnx", 41_385_869L),
            RemoteFile("voices.bin", 3_276_800L),
            RemoteFile("tokens.txt", 1_064L),
        )
        val TOTAL_DOWNLOAD_BYTES: Long = FILES.sumOf(RemoteFile::expectedBytes)

        private val KITTEN_ENGLISH_VOICES = listOf(
            AdvancedVoice("kitten-bella", "Bella", VoiceGroup.FEMALE, 1),
            AdvancedVoice("kitten-jasper", "Jasper", VoiceGroup.MALE, 0),
            AdvancedVoice("kitten-luna", "Luna", VoiceGroup.FEMALE, 3),
            AdvancedVoice("kitten-bruno", "Bruno", VoiceGroup.MALE, 2),
            AdvancedVoice("kitten-rosie", "Rosie", VoiceGroup.FEMALE, 5),
            AdvancedVoice("kitten-hugo", "Hugo", VoiceGroup.MALE, 4),
            AdvancedVoice("kitten-kiki", "Kiki", VoiceGroup.FEMALE, 7),
            AdvancedVoice("kitten-leo", "Leo", VoiceGroup.MALE, 6),
        )
    }
}

data class AdvancedVoiceModel(
    val id: String,
    val displayName: String,
    val description: String,
    val downloadState: VoicePackDownloadState,
    val downloadSizeBytes: Long,
    val installedSizeBytes: Long? = null,
    val downloadProgress: Float? = null,
    val error: String? = null,
) {
    val installed: Boolean get() = downloadState == VoicePackDownloadState.INSTALLED
}

data class AdvancedVoice(
    val id: String,
    val displayName: String,
    val voiceGroup: VoiceGroup,
    val speakerId: Int,
)
