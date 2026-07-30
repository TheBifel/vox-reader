package com.bifel.voxreader.data.analysis

import com.bifel.voxreader.domain.analysis.CharacterAnalyzer
import com.bifel.voxreader.domain.model.CharacterAnalysis
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NARRATOR_ID
import com.bifel.voxreader.domain.model.NarratorProfile
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.model.VoiceGroup
import com.bifel.voxreader.domain.text.SentenceSegmenter
import java.util.Locale

class RuleBasedCharacterAnalyzer : CharacterAnalyzer {

    override suspend fun analyze(
        text: String,
        knownCharacters: List<CharacterProfile>,
    ): CharacterAnalysis {
        val characters = linkedMapOf<String, CharacterProfile>()
        characters[NARRATOR_ID] = NarratorProfile
        knownCharacters.forEach { characters[it.id] = it }

        val recentSpeakers = ArrayDeque<String>()
        val attributed = SentenceSegmenter.segment(text).map { phrase ->
            if (!phrase.containsDialogue()) {
                return@map phrase.copy(speakerId = NARRATOR_ID)
            }

            val before = text.substring(
                (phrase.start - CONTEXT_RADIUS).coerceAtLeast(0),
                phrase.start,
            )
            val after = text.substring(
                phrase.end,
                (phrase.end + CONTEXT_RADIUS).coerceAtMost(text.length),
            )
            val context = "$before ${phrase.text} $after"
            val namedSpeaker = findNamedSpeaker(phrase.text)
                ?: findNamedSpeaker(after)
                ?: findNamedSpeaker(before)
            val speaker = when {
                namedSpeaker != null -> {
                    val profile = findOrCreateCharacter(
                        candidate = namedSpeaker,
                        context = context,
                        characters = characters,
                    )
                    rememberSpeaker(recentSpeakers, profile.id)
                    profile.id
                }

                containsMaleAttribution(context) ->
                    recentSpeakers.firstOrNull {
                        characters[it]?.voiceGroup == VoiceGroup.MALE
                    }

                containsFemaleAttribution(context) ->
                    recentSpeakers.firstOrNull {
                        characters[it]?.voiceGroup == VoiceGroup.FEMALE
                    }

                else -> alternatingSpeaker(recentSpeakers)
            } ?: NARRATOR_ID

            phrase.copy(speakerId = speaker)
        }

        return CharacterAnalysis(
            phrases = attributed.reindex(),
            characters = characters.values.toList(),
        )
    }

    private fun findNamedSpeaker(context: String): String? {
        for (pattern in attributionPatterns) {
            val candidate = pattern.find(context)?.groups?.get("name")?.value?.trim()
            if (!candidate.isNullOrBlank() && candidate.lowercase(Locale.ROOT) !in ignoredNames) {
                return candidate
            }
        }
        return null
    }

    private fun findOrCreateCharacter(
        candidate: String,
        context: String,
        characters: MutableMap<String, CharacterProfile>,
    ): CharacterProfile {
        val normalizedCandidate = normalizeName(candidate)
        characters.values.firstOrNull { profile ->
            profile.aliases.any { normalizeName(it) == normalizedCandidate } ||
                normalizeName(profile.displayName) == normalizedCandidate
        }?.let { return it }

        val voiceGroup = inferVoiceGroup(candidate, context)
        val groupCount = characters.values.count { it.voiceGroup == voiceGroup }
        val voiceSlot = when (voiceGroup) {
            VoiceGroup.NARRATOR -> 0
            VoiceGroup.MALE -> 1 + (groupCount % 2)
            VoiceGroup.FEMALE -> 3 + (groupCount % 2)
            VoiceGroup.NEUTRAL -> 1 + (groupCount % 4)
        }
        val pitch = when (voiceGroup) {
            VoiceGroup.NARRATOR -> 1f
            VoiceGroup.MALE -> if (voiceSlot % 2 == 0) 0.78f else 0.9f
            VoiceGroup.FEMALE -> if (voiceSlot % 2 == 0) 1.22f else 1.1f
            VoiceGroup.NEUTRAL -> listOf(0.88f, 0.98f, 1.08f, 1.18f)[groupCount % 4]
        }

        val id = normalizedCandidate.replace(' ', '_')
        return CharacterProfile(
            id = id,
            displayName = candidate.trim(),
            aliases = setOf(candidate.trim()),
            voiceGroup = voiceGroup,
            voiceSlot = voiceSlot,
            pitch = pitch,
            speechRate = if (voiceGroup == VoiceGroup.MALE && voiceSlot % 2 == 0) 0.95f else 1f,
        ).also { characters[id] = it }
    }

    private fun inferVoiceGroup(name: String, context: String): VoiceGroup {
        val normalized = name.lowercase(Locale.ROOT)
        return when {
            normalized.startsWith("mr ") ||
                normalized.startsWith("sir ") ||
                normalized.startsWith("lord ") ||
                malePronoun.containsMatchIn(context) -> VoiceGroup.MALE

            normalized.startsWith("mrs ") ||
                normalized.startsWith("ms ") ||
                normalized.startsWith("miss ") ||
                normalized.startsWith("lady ") ||
                femalePronoun.containsMatchIn(context) -> VoiceGroup.FEMALE

            else -> VoiceGroup.NEUTRAL
        }
    }

    private fun containsMaleAttribution(context: String): Boolean =
        maleAttribution.containsMatchIn(context)

    private fun containsFemaleAttribution(context: String): Boolean =
        femaleAttribution.containsMatchIn(context)

    private fun alternatingSpeaker(recent: ArrayDeque<String>): String? =
        when (recent.size) {
            0 -> null
            1 -> recent.first()
            else -> recent.elementAt(1)
        }

    private fun rememberSpeaker(recent: ArrayDeque<String>, speakerId: String) {
        recent.remove(speakerId)
        recent.addFirst(speakerId)
        while (recent.size > 4) recent.removeLast()
    }

    private fun normalizeName(value: String): String =
        value
            .lowercase(Locale.ROOT)
            .replace(Regex("""[^\p{L}\p{N}\s'-]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun SpeechPhrase.containsDialogue(): Boolean =
        text.any { it in dialogueMarks }

    private fun List<SpeechPhrase>.reindex(): List<SpeechPhrase> =
        mapIndexed { index, phrase -> phrase.copy(index = index) }

    private companion object {
        const val CONTEXT_RADIUS = 110

        val dialogueMarks = setOf('"', '“', '”', '„', '«', '»')
        val speechVerbs =
            """said|asked|replied|answered|whispered|murmured|shouted|cried|called|added|continued|remarked|exclaimed|observed"""
        val namePattern =
            """(?<name>(?:(?:Mr|Mrs|Ms|Miss|Sir|Lady|Lord|Dr)\.?\s+)?[\p{Lu}][\p{L}'-]+(?:\s+[\p{Lu}][\p{L}'-]+)?)"""

        val attributionPatterns = listOf(
            Regex("""\b$namePattern\s+(?:$speechVerbs)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:$speechVerbs)\s+$namePattern\b""", RegexOption.IGNORE_CASE),
        )

        val malePronoun = Regex("""\b(?:he|him|his)\b""", RegexOption.IGNORE_CASE)
        val femalePronoun = Regex("""\b(?:she|her|hers)\b""", RegexOption.IGNORE_CASE)
        val maleAttribution =
            Regex("""\b(?:he|him)\s+(?:$speechVerbs)\b|\b(?:$speechVerbs)\s+he\b""", RegexOption.IGNORE_CASE)
        val femaleAttribution =
            Regex("""\b(?:she|her)\s+(?:$speechVerbs)\b|\b(?:$speechVerbs)\s+she\b""", RegexOption.IGNORE_CASE)

        val ignoredNames = setOf(
            "he",
            "she",
            "they",
            "i",
            "we",
            "the",
            "a",
            "an",
        )
    }
}
