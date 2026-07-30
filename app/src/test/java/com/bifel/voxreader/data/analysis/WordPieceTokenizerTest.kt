package com.bifel.voxreader.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordPieceTokenizerTest {

    private val vocabulary = listOf(
        "[PAD]",
        "[UNK]",
        "[CLS]",
        "[SEP]",
        "lind",
        "##on",
        "said",
        ",",
        "\u201c",
        "wait",
        "\u201d",
        "candidate",
        "speaker",
        ":",
    )

    @Test
    fun `lowercases accents and creates word pieces`() {
        val tokenizer = WordPieceTokenizer(vocabulary, maxLength = 16)

        assertEquals(
            listOf("lind", "##on", "said", ",", "\u201c", "wait", "\u201d"),
            tokenizer.tokenize("Lindon said, \u201cWait\u201d"),
        )
    }

    @Test
    fun `pair encoding pads to the model length and marks second sequence`() {
        val tokenizer = WordPieceTokenizer(vocabulary, maxLength = 16)

        val encoded = tokenizer.encodePair(
            first = "Lindon said",
            second = "Candidate speaker: Lindon",
        )

        assertEquals(16, encoded.inputIds.size)
        assertEquals(16, encoded.attentionMask.size)
        assertEquals(16, encoded.tokenTypeIds.size)
        assertEquals(1L, encoded.attentionMask.first())
        assertEquals(0L, encoded.attentionMask.last())
        assertTrue(encoded.tokenTypeIds.any { it == 1L })
    }
}
