package com.bifel.voxreader.data.analysis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.bifel.voxreader.domain.analysis.SpeakerScorer
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlin.math.exp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OnnxTinyBertSpeakerScorer(
    context: Context,
) : SpeakerScorer {

    private val applicationContext = context.applicationContext
    private val initializationMutex = Mutex()

    @Volatile
    private var runtime: Runtime? = null

    init {
        removeLegacyGenerativeModel()
    }

    override suspend fun scoreCandidates(
        scene: String,
        candidates: List<String>,
    ): List<Float> = withContext(Dispatchers.Default) {
        if (candidates.isEmpty()) return@withContext emptyList()
        val activeRuntime = ensureRuntime()
        val encoded = candidates.map { candidate ->
            activeRuntime.tokenizer.encodePair(
                first = scene,
                second = "Candidate speaker: $candidate",
            )
        }
        val inputIds = Array(encoded.size) { encoded[it].inputIds }
        val attentionMasks = Array(encoded.size) { encoded[it].attentionMask }
        val tokenTypeIds = Array(encoded.size) { encoded[it].tokenTypeIds }
        val startedNanos = System.nanoTime()

        OnnxTensor.createTensor(activeRuntime.environment, inputIds).use { inputIdsTensor ->
            OnnxTensor.createTensor(
                activeRuntime.environment,
                attentionMasks,
            ).use { attentionMaskTensor ->
                OnnxTensor.createTensor(
                    activeRuntime.environment,
                    tokenTypeIds,
                ).use { tokenTypeTensor ->
                    val inputs = mapOf(
                        INPUT_IDS to inputIdsTensor,
                        ATTENTION_MASK to attentionMaskTensor,
                        TOKEN_TYPE_IDS to tokenTypeTensor,
                    )
                    activeRuntime.session.run(inputs).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        val logits = result[0].value as Array<FloatArray>
                        val scores = logits.map(::positiveProbability)
                        Log.i(
                            TAG,
                            "Scored ${candidates.size} candidates in " +
                                "${(System.nanoTime() - startedNanos) / 1_000_000} ms",
                        )
                        scores
                    }
                }
            }
        }
    }

    private suspend fun ensureRuntime(): Runtime {
        runtime?.let { return it }
        return initializationMutex.withLock {
            runtime?.let { return@withLock it }
            val startedNanos = System.nanoTime()
            val modelFile = copyModelAsset()
            val environment = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(INTRA_OP_THREADS)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val session = environment.createSession(modelFile.absolutePath, options)
            val tokenizer = WordPieceTokenizer(
                vocabulary = applicationContext.assets
                    .open(VOCABULARY_ASSET)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readLines() },
                maxLength = MAX_SEQUENCE_LENGTH,
            )
            Runtime(environment, session, tokenizer).also {
                runtime = it
                Log.i(
                    TAG,
                    "TinyBERT initialized in " +
                        "${(System.nanoTime() - startedNanos) / 1_000_000} ms",
                )
            }
        }
    }

    private fun copyModelAsset(): File {
        val modelDirectory = File(applicationContext.noBackupFilesDir, MODEL_DIRECTORY)
        check(modelDirectory.exists() || modelDirectory.mkdirs()) {
            "Unable to create the TinyBERT model directory."
        }
        File(modelDirectory, PREVIOUS_MODEL_FILE_NAME)
            .takeIf(File::isFile)
            ?.delete()
        val target = File(modelDirectory, MODEL_FILE_NAME)
        val assetLength = applicationContext.assets.openFd(MODEL_ASSET).length
        if (target.isFile && target.length() == assetLength) return target

        val temporary = File(modelDirectory, "$MODEL_FILE_NAME.tmp")
        applicationContext.assets.open(MODEL_ASSET).use { source ->
            temporary.outputStream().buffered().use(source::copyTo)
        }
        check(temporary.length() == assetLength) {
            "The bundled TinyBERT model could not be copied completely."
        }
        if (target.exists()) {
            check(target.delete()) { "Unable to replace the bundled TinyBERT model." }
        }
        check(temporary.renameTo(target)) { "Unable to activate the bundled TinyBERT model." }
        return target
    }

    private fun removeLegacyGenerativeModel() {
        val legacyModel = File(
            applicationContext.filesDir,
            "$LEGACY_MODEL_DIRECTORY/$LEGACY_MODEL_FILE_NAME",
        )
        val legacyBytes = legacyModel.takeIf(File::isFile)?.length()
        if (legacyBytes != null && legacyModel.delete()) {
            Log.i(TAG, "Removed the obsolete $legacyBytes byte generative model")
        }
        legacyModel.parentFile
            ?.takeIf { it.isDirectory && it.list().isNullOrEmpty() }
            ?.delete()

        val removedCacheBytes = applicationContext.cacheDir
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(LEGACY_MODEL_FILE_NAME) }
            .sumOf { cacheFile ->
                val bytes = cacheFile.length()
                if (cacheFile.delete()) bytes else 0L
            }
        if (removedCacheBytes > 0L) {
            Log.i(TAG, "Removed $removedCacheBytes bytes of obsolete generative-model caches")
        }
    }

    private fun positiveProbability(logits: FloatArray): Float {
        require(logits.size >= 2) { "TinyBERT returned an invalid output tensor." }
        val maximum = maxOf(logits[0], logits[1])
        val negative = exp((logits[0] - maximum).toDouble())
        val positive = exp((logits[1] - maximum).toDouble())
        return (positive / (negative + positive)).toFloat()
    }

    private data class Runtime(
        val environment: OrtEnvironment,
        val session: OrtSession,
        val tokenizer: WordPieceTokenizer,
    )

    private companion object {
        const val TAG = "VoxReaderTinyBERT"
        const val MODEL_DIRECTORY = "bundled-models"
        const val MODEL_FILE_NAME = "speaker_attribution_quantized.onnx"
        const val PREVIOUS_MODEL_FILE_NAME = "speaker_attribution_int8.onnx"
        const val MODEL_ASSET = "speaker_attribution_quantized.onnx"
        const val VOCABULARY_ASSET = "speaker_vocab.txt"
        const val LEGACY_MODEL_DIRECTORY = "models"
        const val LEGACY_MODEL_FILE_NAME = "character-attribution.litertlm"
        const val MAX_SEQUENCE_LENGTH = 160
        const val INTRA_OP_THREADS = 2
        const val INPUT_IDS = "input_ids"
        const val ATTENTION_MASK = "attention_mask"
        const val TOKEN_TYPE_IDS = "token_type_ids"
    }
}

internal class WordPieceTokenizer(
    vocabulary: List<String>,
    private val maxLength: Int,
) {
    private val tokenIds = vocabulary.withIndex().associate { (index, token) -> token to index }
    private val unknownId = requireNotNull(tokenIds["[UNK]"]) { "Vocabulary has no [UNK] token." }
    private val paddingId = requireNotNull(tokenIds["[PAD]"]) { "Vocabulary has no [PAD] token." }
    private val classificationId = requireNotNull(tokenIds["[CLS]"]) {
        "Vocabulary has no [CLS] token."
    }
    private val separatorId = requireNotNull(tokenIds["[SEP]"]) {
        "Vocabulary has no [SEP] token."
    }

    fun encodePair(
        first: String,
        second: String,
    ): EncodedInput {
        val firstTokens = tokenize(first).toMutableList()
        val secondTokens = tokenize(second).toMutableList()
        while (firstTokens.size + secondTokens.size > maxLength - SPECIAL_PAIR_TOKENS) {
            if (firstTokens.size > secondTokens.size) {
                firstTokens.removeLast()
            } else {
                secondTokens.removeLast()
            }
        }

        val ids = buildList {
            add(classificationId)
            addAll(firstTokens.map(::idFor))
            add(separatorId)
            addAll(secondTokens.map(::idFor))
            add(separatorId)
        }
        val secondStart = firstTokens.size + 2
        return EncodedInput(
            inputIds = LongArray(maxLength) { index ->
                ids.getOrNull(index)?.toLong() ?: paddingId.toLong()
            },
            attentionMask = LongArray(maxLength) { index ->
                if (index < ids.size) 1L else 0L
            },
            tokenTypeIds = LongArray(maxLength) { index ->
                if (index in secondStart until ids.size) 1L else 0L
            },
        )
    }

    internal fun tokenize(text: String): List<String> =
        basicTokens(text).flatMap(::wordPieces)

    private fun basicTokens(text: String): List<String> {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .lowercase(Locale.ROOT)
        val tokens = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            if (current.isNotEmpty()) {
                tokens += current.toString()
                current.clear()
            }
        }

        normalized.forEach { character ->
            when {
                Character.getType(character) == Character.NON_SPACING_MARK.toInt() -> Unit
                character.isWhitespace() || character.isControlCharacter() -> flush()
                character.isPunctuation() -> {
                    flush()
                    tokens += character.toString()
                }
                else -> current.append(character)
            }
        }
        flush()
        return tokens
    }

    private fun wordPieces(token: String): List<String> {
        if (token.length > MAX_INPUT_TOKEN_CHARACTERS) return listOf("[UNK]")
        if (tokenIds.containsKey(token)) return listOf(token)

        val pieces = mutableListOf<String>()
        var start = 0
        while (start < token.length) {
            var end = token.length
            var match: String? = null
            while (start < end) {
                val substring = token.substring(start, end)
                val candidate = if (start == 0) substring else "##$substring"
                if (tokenIds.containsKey(candidate)) {
                    match = candidate
                    break
                }
                end--
            }
            if (match == null) return listOf("[UNK]")
            pieces += match
            start = end
        }
        return pieces
    }

    private fun idFor(token: String): Int = tokenIds[token] ?: unknownId

    private fun Char.isControlCharacter(): Boolean =
        when (Character.getType(this)) {
            Character.CONTROL.toInt(),
            Character.FORMAT.toInt(),
            -> true
            else -> false
        }

    private fun Char.isPunctuation(): Boolean {
        if (this in '!'..'/' || this in ':'..'@' || this in '['..'`' || this in '{'..'~') {
            return true
        }
        return when (Character.getType(this)) {
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
            -> true
            else -> false
        }
    }

    data class EncodedInput(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray,
    )

    private companion object {
        const val SPECIAL_PAIR_TOKENS = 3
        const val MAX_INPUT_TOKEN_CHARACTERS = 100
    }
}
