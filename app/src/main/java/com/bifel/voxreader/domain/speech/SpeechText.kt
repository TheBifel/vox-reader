package com.bifel.voxreader.domain.speech

object SpeechText {
    fun isSpeakable(text: String): Boolean =
        text.any(Char::isLetterOrDigit)

    /**
     * Replacing quote glyphs with spaces prevents Android TTS engines from announcing "quote".
     * The string length remains unchanged so range callbacks still map to the book offsets.
     */
    fun withoutSpokenQuotes(text: String): String =
        text.map { character ->
            if (character in QUOTE_MARKS) ' ' else character
        }.joinToString(separator = "")

    private val QUOTE_MARKS = setOf(
        '"',
        '\u201c',
        '\u201d',
        '\u201e',
        '\u00ab',
        '\u00bb',
    )
}
