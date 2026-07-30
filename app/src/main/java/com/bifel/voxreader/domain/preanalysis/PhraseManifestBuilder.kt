package com.bifel.voxreader.domain.preanalysis

import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.preanalysis.model.PHRASE_MANIFEST_VERSION
import com.bifel.voxreader.domain.preanalysis.model.PhraseId
import com.bifel.voxreader.domain.preanalysis.model.PhraseManifestItem
import com.bifel.voxreader.domain.preanalysis.model.SEGMENTATION_VERSION
import java.security.MessageDigest
import java.util.Locale

object PhraseManifestBuilder {

    fun build(
        bookId: String,
        phrases: List<SpeechPhrase>,
    ): List<PhraseManifestItem> {
        val digest = MessageDigest.getInstance("SHA-256")
        return phrases.map { phrase ->
            val normalizedHash = sha256(normalize(phrase.text), digest)
            PhraseManifestItem(
                phraseId = PhraseId(
                    sha256(
                        "$bookId|$PHRASE_MANIFEST_VERSION|$SEGMENTATION_VERSION|" +
                            "${phrase.start}|${phrase.end}|$normalizedHash",
                        digest,
                    ),
                ),
                phraseIndex = phrase.index,
                start = phrase.start,
                end = phrase.end,
                normalizedTextHash = normalizedHash,
            )
        }
    }

    private fun normalize(text: String): String =
        text
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE, " ")
            .trim()

    private fun sha256(
        value: String,
        digest: MessageDigest,
    ): String =
        digest.digest(value.toByteArray(Charsets.UTF_8))
            .toHex()

    private fun ByteArray.toHex(): String {
        val output = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            output[index * 2] = HEX[value ushr 4]
            output[index * 2 + 1] = HEX[value and 0x0f]
        }
        return output.concatToString()
    }

    private val WHITESPACE = Regex("""\s+""")
    private const val HEX = "0123456789abcdef"
}
