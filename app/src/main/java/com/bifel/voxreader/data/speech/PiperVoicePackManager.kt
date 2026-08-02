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

/** Catalog of compatible Piper voices and the app-private files downloaded for them. */
class PiperVoicePackManager(context: Context) {
    private val root = File(context.applicationContext.filesDir, "piper-voices")
    private val mutablePacks = MutableStateFlow(loadPacks())
    val packs: StateFlow<List<PiperVoicePack>> = mutablePacks.asStateFlow()

    fun installedPacks(): List<PiperVoicePack> = packs.value.filter { it.installed }

    suspend fun download(id: String) = withContext(Dispatchers.IO) {
        val pack = catalog.firstOrNull { it.id == id } ?: return@withContext
        update(pack.copy(downloadState = VoicePackDownloadState.DOWNLOADING))
        val directory = directoryFor(pack)
        val temporaryModel = File(directory, "model.onnx.part")
        val temporaryConfig = File(directory, "model.onnx.json.part")
        try {
            directory.mkdirs()
            downloadFile(pack.modelUrl, temporaryModel) { downloaded, total ->
                update(pack.copy(
                    downloadState = VoicePackDownloadState.DOWNLOADING,
                    downloadProgress = total?.let { (downloaded.toFloat() / it).coerceIn(0f, 1f) * 0.98f },
                ))
            }
            downloadFile(pack.configUrl, temporaryConfig) { downloaded, total ->
                update(pack.copy(
                    downloadState = VoicePackDownloadState.DOWNLOADING,
                    downloadProgress = total?.let { 0.98f + (downloaded.toFloat() / it).coerceIn(0f, 1f) * 0.02f },
                ))
            }
            val model = File(directory, "model.onnx")
            val config = File(directory, "model.onnx.json")
            if (!temporaryModel.renameTo(model) || !temporaryConfig.renameTo(config)) {
                error("Could not finalize the voice download.")
            }
            File(directory, FORMAT_MARKER).writeText(FORMAT_VERSION)
            update(pack.copy(
                downloadState = VoicePackDownloadState.INSTALLED,
                installedSizeBytes = model.length() + config.length(),
                downloadProgress = null,
            ))
        } catch (error: Throwable) {
            temporaryModel.delete()
            temporaryConfig.delete()
            update(pack.copy(
                downloadState = VoicePackDownloadState.FAILED,
                downloadProgress = null,
                error = error.message ?: "Download failed",
            ))
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        val pack = catalog.firstOrNull { it.id == id } ?: return@withContext
        val directory = directoryFor(pack)
        check(directory.parentFile?.canonicalFile == root.canonicalFile) { "Invalid voice-pack directory." }
        File(directory, "model.onnx").delete()
        File(directory, "model.onnx.json").delete()
        File(directory, "model.onnx.part").delete()
        File(directory, "model.onnx.json.part").delete()
        File(directory, FORMAT_MARKER).delete()
        directory.delete()
        update(pack.copy(downloadState = VoicePackDownloadState.NOT_INSTALLED, installedSizeBytes = null))
    }

    private fun loadPacks(): List<PiperVoicePack> = catalog.map { pack ->
        pack.copy(downloadState = if (
            modelFile(pack).isFile && configFile(pack).isFile &&
            File(directoryFor(pack), FORMAT_MARKER).readTextOrNull() == FORMAT_VERSION
        ) {
            VoicePackDownloadState.INSTALLED
        } else {
            VoicePackDownloadState.NOT_INSTALLED
        }, installedSizeBytes = installedSize(pack))
    }

    private fun update(pack: PiperVoicePack) {
        mutablePacks.value = mutablePacks.value.map { current ->
            if (current.storageId == pack.storageId) {
                current.copy(
                    downloadState = pack.downloadState,
                    installedSizeBytes = pack.installedSizeBytes,
                    downloadProgress = pack.downloadProgress,
                    error = pack.error,
                )
            } else current
        }
    }

    private fun directoryFor(pack: PiperVoicePack) = File(root, pack.storageId)
    private fun modelFile(pack: PiperVoicePack) = File(directoryFor(pack), "model.onnx")
    private fun configFile(pack: PiperVoicePack) = File(directoryFor(pack), "model.onnx.json")
    private fun installedSize(pack: PiperVoicePack): Long? =
        if (modelFile(pack).isFile && configFile(pack).isFile) {
            modelFile(pack).length() + configFile(pack).length()
        } else {
            null
        }

    fun modelPath(pack: PiperVoicePack): String = modelFile(pack).absolutePath

    private fun downloadFile(
        source: String,
        destination: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ) {
        val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 90_000
            instanceFollowRedirects = true
        }
        val total = connection.contentLengthLong.takeIf { it > 0L }
        connection.inputStream.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloaded = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    downloaded += count
                    onProgress(downloaded, total)
                }
            }
        }
        check(destination.length() > 1_024) { "Downloaded file is incomplete." }
    }

    private fun File.readTextOrNull(): String? = runCatching { readText() }.getOrNull()

    companion object {
        // Sherpa-converted Piper models. Raw Piper models are not binary compatible with
        // Sherpa's Android runtime and can make its native layer terminate the process.
        val catalog = listOf(
            // Ukrainian models currently available in the Sherpa-compatible conversion catalog.
            pack(
                id = "uk-lada-low",
                name = "Lada (Ukrainian, x-low)",
                group = VoiceGroup.FEMALE,
                repository = "vits-piper-uk_UA-lada-x_low",
                fileName = "uk_UA-lada-x_low.onnx",
                language = VoicePackLanguage.UKRAINIAN,
            ),
            pack(
                id = "uk-ukrainian-tts-lada",
                name = "Lada (Ukrainian, medium)",
                group = VoiceGroup.FEMALE,
                repository = "vits-piper-uk_UA-ukrainian_tts-medium",
                fileName = "uk_UA-ukrainian_tts-medium.onnx",
                language = VoicePackLanguage.UKRAINIAN,
                storageId = "uk-ukrainian-tts-medium",
                speakerId = 0,
            ),
            pack(
                id = "uk-ukrainian-tts-mykyta",
                name = "Mykyta (Ukrainian, medium)",
                group = VoiceGroup.MALE,
                repository = "vits-piper-uk_UA-ukrainian_tts-medium",
                fileName = "uk_UA-ukrainian_tts-medium.onnx",
                language = VoicePackLanguage.UKRAINIAN,
                storageId = "uk-ukrainian-tts-medium",
                speakerId = 1,
            ),
            pack(
                id = "uk-ukrainian-tts-tetiana",
                name = "Tetiana (Ukrainian, medium)",
                group = VoiceGroup.FEMALE,
                repository = "vits-piper-uk_UA-ukrainian_tts-medium",
                fileName = "uk_UA-ukrainian_tts-medium.onnx",
                language = VoicePackLanguage.UKRAINIAN,
                storageId = "uk-ukrainian-tts-medium",
                speakerId = 2,
            ),
            // United States English
            pack("amy", "Amy (US, low)", VoiceGroup.FEMALE, "vits-piper-en_US-amy-low", "en_US-amy-low.onnx"),
            pack("us-amy-medium", "Amy (US, medium)", VoiceGroup.FEMALE, "vits-piper-en_US-amy-medium", "en_US-amy-medium.onnx"),
            pack("us-arctic", "Arctic (US)", VoiceGroup.NEUTRAL, "vits-piper-en_US-arctic-medium", "en_US-arctic-medium.onnx"),
            pack("us-bryce", "Bryce (US)", VoiceGroup.MALE, "vits-piper-en_US-bryce-medium", "en_US-bryce-medium.onnx"),
            pack("us-danny", "Danny (US, low)", VoiceGroup.MALE, "vits-piper-en_US-danny-low", "en_US-danny-low.onnx"),
            pack("us-glados", "GLaDOS (US)", VoiceGroup.FEMALE, "vits-piper-en_US-glados", "en_US-glados.onnx"),
            pack("us-glados-high", "GLaDOS (US, high)", VoiceGroup.FEMALE, "vits-piper-en_US-glados-high", "en_US-glados-high.onnx"),
            pack("hfc-female", "HFC Female (US)", VoiceGroup.FEMALE, "vits-piper-en_US-hfc_female-medium", "en_US-hfc_female-medium.onnx"),
            pack("hfc-male", "HFC Male (US)", VoiceGroup.MALE, "vits-piper-en_US-hfc_male-medium", "en_US-hfc_male-medium.onnx"),
            pack("us-joe", "Joe (US)", VoiceGroup.MALE, "vits-piper-en_US-joe-medium", "en_US-joe-medium.onnx"),
            pack("us-john", "John (US)", VoiceGroup.MALE, "vits-piper-en_US-john-medium", "en_US-john-medium.onnx"),
            pack("us-kathleen", "Kathleen (US, low)", VoiceGroup.FEMALE, "vits-piper-en_US-kathleen-low", "en_US-kathleen-low.onnx"),
            pack("us-kristin", "Kristin (US)", VoiceGroup.FEMALE, "vits-piper-en_US-kristin-medium", "en_US-kristin-medium.onnx"),
            pack("us-kusal", "Kusal (US)", VoiceGroup.FEMALE, "vits-piper-en_US-kusal-medium", "en_US-kusal-medium.onnx"),
            pack("us-l2arctic", "L2 Arctic (US, 24 voices)", VoiceGroup.NEUTRAL, "vits-piper-en_US-l2arctic-medium", "en_US-l2arctic-medium.onnx"),
            pack("us-lessac-low", "Lessac (US, low)", VoiceGroup.NARRATOR, "vits-piper-en_US-lessac-low", "en_US-lessac-low.onnx"),
            pack("lessac", "Lessac (US, medium)", VoiceGroup.NARRATOR, "vits-piper-en_US-lessac-medium", "en_US-lessac-medium.onnx"),
            pack("us-lessac-high", "Lessac (US, high)", VoiceGroup.NARRATOR, "vits-piper-en_US-lessac-high", "en_US-lessac-high.onnx"),
            pack("libritts-r", "LibriTTS R (US, 904 voices)", VoiceGroup.NEUTRAL, "vits-piper-en_US-libritts_r-medium", "en_US-libritts_r-medium.onnx"),
            pack("us-libritts", "LibriTTS (US, high)", VoiceGroup.NEUTRAL, "vits-piper-en_US-libritts-high", "en_US-libritts-high.onnx"),
            pack("us-ljspeech-medium", "LJSpeech (US, medium)", VoiceGroup.FEMALE, "vits-piper-en_US-ljspeech-medium", "en_US-ljspeech-medium.onnx"),
            pack("us-ljspeech-high", "LJSpeech (US, high)", VoiceGroup.FEMALE, "vits-piper-en_US-ljspeech-high", "en_US-ljspeech-high.onnx"),
            pack("us-miro", "Miro (US, high)", VoiceGroup.MALE, "vits-piper-en_US-miro-high", "en_US-miro-high.onnx"),
            pack("us-norman", "Norman (US)", VoiceGroup.MALE, "vits-piper-en_US-norman-medium", "en_US-norman-medium.onnx"),
            pack("ryan", "Ryan (US, low)", VoiceGroup.MALE, "vits-piper-en_US-ryan-low", "en_US-ryan-low.onnx"),
            pack("us-ryan-medium", "Ryan (US, medium)", VoiceGroup.MALE, "vits-piper-en_US-ryan-medium", "en_US-ryan-medium.onnx"),
            pack("us-ryan-high", "Ryan (US, high)", VoiceGroup.MALE, "vits-piper-en_US-ryan-high", "en_US-ryan-high.onnx"),

            // British English
            pack("gb-alan-low", "Alan (British, low)", VoiceGroup.MALE, "vits-piper-en_GB-alan-low", "en_GB-alan-low.onnx"),
            pack("gb-alan-medium", "Alan (British, medium)", VoiceGroup.MALE, "vits-piper-en_GB-alan-medium", "en_GB-alan-medium.onnx"),
            pack("gb-alba", "Alba (British)", VoiceGroup.FEMALE, "vits-piper-en_GB-alba-medium", "en_GB-alba-medium.onnx"),
            pack("gb-aru", "Aru (British)", VoiceGroup.FEMALE, "vits-piper-en_GB-aru-medium", "en_GB-aru-medium.onnx"),
            pack("gb-cori-medium", "Cori (British, medium)", VoiceGroup.FEMALE, "vits-piper-en_GB-cori-medium", "en_GB-cori-medium.onnx"),
            pack("gb-cori-high", "Cori (British, high)", VoiceGroup.FEMALE, "vits-piper-en_GB-cori-high", "en_GB-cori-high.onnx"),
            pack("gb-dii", "Dii (British, high)", VoiceGroup.MALE, "vits-piper-en_GB-dii-high", "en_GB-dii-high.onnx"),
            pack("gb-jenny", "Jenny Dioco (British)", VoiceGroup.FEMALE, "vits-piper-en_GB-jenny_dioco-medium", "en_GB-jenny_dioco-medium.onnx"),
            pack("gb-miro", "Miro (British, high)", VoiceGroup.MALE, "vits-piper-en_GB-miro-high", "en_GB-miro-high.onnx"),
            pack("gb-northern-male", "Northern English Male", VoiceGroup.MALE, "vits-piper-en_GB-northern_english_male-medium", "en_GB-northern_english_male-medium.onnx"),
            pack("gb-semaine", "Semaine (British)", VoiceGroup.NEUTRAL, "vits-piper-en_GB-semaine-medium", "en_GB-semaine-medium.onnx"),
            pack("gb-southern-female-low", "Southern English Female (low)", VoiceGroup.FEMALE, "vits-piper-en_GB-southern_english_female-low", "en_GB-southern_english_female-low.onnx"),
            pack("gb-southern-female-medium", "Southern English Female (medium)", VoiceGroup.FEMALE, "vits-piper-en_GB-southern_english_female-medium", "en_GB-southern_english_female-medium.onnx"),
            pack("gb-southern-male", "Southern English Male", VoiceGroup.MALE, "vits-piper-en_GB-southern_english_male-medium", "en_GB-southern_english_male-medium.onnx"),
            pack("gb-sweetbbak-amy", "Sweetbbak Amy (British)", VoiceGroup.FEMALE, "vits-piper-en_GB-sweetbbak-amy", "en_GB-sweetbbak-amy.onnx"),
            pack("gb-vctk", "VCTK (British, 109 voices)", VoiceGroup.NEUTRAL, "vits-piper-en_GB-vctk-medium", "en_GB-vctk-medium.onnx"),
        )

        private const val FORMAT_MARKER = ".voxreader-pack-format"
        private const val FORMAT_VERSION = "sherpa-piper-v1"

        private fun pack(
            id: String,
            name: String,
            group: VoiceGroup,
            repository: String,
            fileName: String,
            language: VoicePackLanguage = VoicePackLanguage.ENGLISH,
            storageId: String = id,
            speakerId: Int = 0,
        ): PiperVoicePack {
            val base = "https://huggingface.co/csukuangfj/$repository/resolve/main/$fileName"
            return PiperVoicePack(id, name, voiceDescription(name, group), group, language, base, "$base.json", storageId, speakerId)
        }

        private fun voiceDescription(name: String, group: VoiceGroup): String = when {
            "ukrainian, medium" in name.lowercase() ->
                "One shared download with three Ukrainian speakers; choose this speaker for a character."
            "glados" in name.lowercase() -> "Synthetic, robotic voice for unusual characters."
            "24 voices" in name.lowercase() || "109 voices" in name.lowercase() || "904 voices" in name.lowercase() ->
                "Multi-speaker collection for experimenting with varied supporting roles."
            "low" in name.lowercase() && group == VoiceGroup.MALE -> "Lower-pitched adult male voice; useful for older or grounded characters."
            "low" in name.lowercase() && group == VoiceGroup.FEMALE -> "Lower-pitched adult female voice; useful for calm or mature characters."
            "high" in name.lowercase() && group == VoiceGroup.MALE -> "Brighter adult male voice with more presence."
            "high" in name.lowercase() && group == VoiceGroup.FEMALE -> "Brighter adult female voice with more presence."
            group == VoiceGroup.NARRATOR -> "Balanced, clear voice suited to long-form narration."
            group == VoiceGroup.MALE -> "Clear adult male voice for character dialogue."
            group == VoiceGroup.FEMALE -> "Clear adult female voice for character dialogue."
            else -> "Neutral voice for narration or varied supporting characters."
        }
    }
}

data class PiperVoicePack(
    val id: String,
    val displayName: String,
    val description: String,
    val voiceGroup: VoiceGroup,
    val language: VoicePackLanguage = VoicePackLanguage.ENGLISH,
    val modelUrl: String,
    val configUrl: String,
    /** Variants may share one downloaded model directory. */
    val storageId: String = id,
    /** Speaker index inside a multi-speaker model. */
    val speakerId: Int = 0,
    val downloadState: VoicePackDownloadState = VoicePackDownloadState.NOT_INSTALLED,
    val installedSizeBytes: Long? = null,
    val downloadProgress: Float? = null,
    val error: String? = null,
) {
    val installed: Boolean get() = downloadState == VoicePackDownloadState.INSTALLED
}

enum class VoicePackDownloadState { NOT_INSTALLED, DOWNLOADING, INSTALLED, FAILED }

enum class VoicePackLanguage { ENGLISH, UKRAINIAN }
