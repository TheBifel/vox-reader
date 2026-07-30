package com.bifel.voxreader.domain.speech

data class BufferedPhrase(
    val utteranceId: String,
    val phraseIndex: Int,
)

class PhrasePlaybackBuffer(
    val capacity: Int,
) {
    private val entries = linkedMapOf<String, BufferedPhrase>()

    init {
        require(capacity > 0)
    }

    val size: Int
        get() = entries.size

    val hasCapacity: Boolean
        get() = size < capacity

    val isEmpty: Boolean
        get() = entries.isEmpty()

    val lastPhraseIndex: Int?
        get() = entries.values.lastOrNull()?.phraseIndex

    fun add(entry: BufferedPhrase) {
        require(hasCapacity) { "The speech look-ahead buffer is full." }
        require(entry.utteranceId !in entries) { "Duplicate utterance ID." }
        require(entries.values.none { it.phraseIndex == entry.phraseIndex }) {
            "Phrase ${entry.phraseIndex} is already buffered."
        }
        entries[entry.utteranceId] = entry
    }

    operator fun get(utteranceId: String): BufferedPhrase? =
        entries[utteranceId]

    fun remove(utteranceId: String): BufferedPhrase? =
        entries.remove(utteranceId)

    fun containsPhrase(phraseIndex: Int): Boolean =
        entries.values.any { it.phraseIndex == phraseIndex }

    fun clear() {
        entries.clear()
    }
}
