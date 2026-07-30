package com.bifel.voxreader.domain.text

import com.bifel.voxreader.domain.model.SpeechPhrase
import java.text.BreakIterator
import java.util.Locale

object SentenceSegmenter {
    fun segment(
        text: String,
        locale: Locale = Locale.getDefault(),
    ): List<SpeechPhrase> {
        if (text.isBlank()) return emptyList()

        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(text)

        val result = mutableListOf<SpeechPhrase>()
        var quoteMode = QuoteMode.NONE
        var start = iterator.first()
        var end = iterator.next()

        while (end != BreakIterator.DONE) {
            var segmentStart = start
            var index = start
            while (index < end) {
                val nextMode = quoteMode.after(
                    character = text[index],
                    opensDialogue = text.isDialogueOpening(segmentStart, index),
                )
                when {
                    !quoteMode.isDialogue && nextMode.isDialogue -> {
                        result.addTrimmed(text, segmentStart, index)
                        segmentStart = index
                    }

                    quoteMode.isDialogue && !nextMode.isDialogue -> {
                        result.addTrimmed(text, segmentStart, index + 1)
                        segmentStart = index + 1
                    }
                }
                quoteMode = nextMode
                index++
            }
            result.addTrimmed(text, segmentStart, end)
            start = end
            end = iterator.next()
        }

        return result
    }

    private fun MutableList<SpeechPhrase>.addTrimmed(
        source: String,
        rawStart: Int,
        rawEnd: Int,
    ) {
        val firstContent = (rawStart until rawEnd)
            .firstOrNull { !source[it].isWhitespace() }
            ?: return
        val lastContent = (rawEnd - 1 downTo firstContent)
            .firstOrNull { !source[it].isWhitespace() }
            ?: return
        val end = lastContent + 1
        add(
            SpeechPhrase(
                index = size,
                start = firstContent,
                end = end,
                text = source.substring(firstContent, end),
            ),
        )
    }

    private fun String.isDialogueOpening(
        segmentStart: Int,
        quoteIndex: Int,
    ): Boolean {
        val prefix = substring(segmentStart, quoteIndex).trim()
        return prefix.isEmpty() || dialogueLeadIn.containsMatchIn(prefix)
    }

    private enum class QuoteMode(val isDialogue: Boolean) {
        NONE(false),
        STRAIGHT(true),
        CURLY(true),
        GUILLEMET(true),
        INLINE_STRAIGHT(false),
        INLINE_CURLY(false),
        INLINE_GUILLEMET(false);

        fun after(
            character: Char,
            opensDialogue: Boolean,
        ): QuoteMode =
            when (character) {
                '"' -> when (this) {
                    STRAIGHT, INLINE_STRAIGHT -> NONE
                    NONE -> if (opensDialogue) STRAIGHT else INLINE_STRAIGHT
                    else -> this
                }
                '\u201c', '\u201e' -> if (this == NONE) {
                    if (opensDialogue) CURLY else INLINE_CURLY
                } else {
                    this
                }
                '\u201d' -> if (this == CURLY || this == INLINE_CURLY) NONE else this
                '\u00ab' -> if (this == NONE) {
                    if (opensDialogue) GUILLEMET else INLINE_GUILLEMET
                } else {
                    this
                }
                '\u00bb' ->
                    if (this == GUILLEMET || this == INLINE_GUILLEMET) NONE else this
                else -> this
            }
    }

    private val dialogueLeadIn = Regex(
        """(?i)\b(?:said|asked|replied|answered|whispered|murmured|shouted|""" +
            """cried|called|added|continued|remarked|exclaimed|observed)\b""" +
            """[^.!?]{0,40}[,:—-]\s*$|[:—-]\s*$""",
    )
}
