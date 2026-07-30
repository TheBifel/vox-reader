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
        var start = iterator.first()
        var end = iterator.next()

        while (end != BreakIterator.DONE) {
            val firstContent = (start until end).firstOrNull { !text[it].isWhitespace() }
            val lastContent = (end - 1 downTo start).firstOrNull { !text[it].isWhitespace() }
            if (firstContent != null && lastContent != null) {
                val exclusiveEnd = lastContent + 1
                result += SpeechPhrase(
                    index = result.size,
                    start = firstContent,
                    end = exclusiveEnd,
                    text = text.substring(firstContent, exclusiveEnd),
                )
            }
            start = end
            end = iterator.next()
        }

        return result
    }
}

