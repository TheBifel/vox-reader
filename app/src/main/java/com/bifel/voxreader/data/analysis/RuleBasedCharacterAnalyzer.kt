package com.bifel.voxreader.data.analysis

import com.bifel.voxreader.domain.analysis.CharacterAnalyzer
import com.bifel.voxreader.domain.analysis.CharacterAnalysisRequest
import com.bifel.voxreader.domain.model.CharacterAnalysis
import com.bifel.voxreader.domain.model.CharacterAnalysisSource
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NARRATOR_ID
import com.bifel.voxreader.domain.model.NarratorProfile
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.model.VoiceGroup
import com.bifel.voxreader.domain.text.SentenceSegmenter
import java.util.Locale

class RuleBasedCharacterAnalyzer : CharacterAnalyzer {

    override suspend fun analyze(request: CharacterAnalysisRequest): CharacterAnalysis {
        val text = request.text
        val characters = linkedMapOf<String, CharacterProfile>()
        characters[NARRATOR_ID] = NarratorProfile
        request.knownCharacters.forEach { characters[it.id] = it }
        if (request.existingPhrases.isNotEmpty()) {
            return analyzeFocused(request, characters)
        }

        val recentSpeakers = ArrayDeque<String>()
        var openDialogueSpeaker: String? = null
        val attributed = SentenceSegmenter.segment(text).map { phrase ->
            val speaker = openDialogueSpeaker ?: if (!phrase.isDialogueIn(text)) {
                NARRATOR_ID
            } else {
                val context = text.paragraphContextAround(phrase)
                val namedSpeaker = findNamedSpeakerAround(text, phrase)
                when {
                    namedSpeaker != null -> {
                        val profile = findOrCreateCharacter(
                            candidate = namedSpeaker,
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
            }

            openDialogueSpeaker = if (text.isInsideDialogueAt(phrase.end)) speaker else null
            phrase.copy(speakerId = speaker)
        }

        return CharacterAnalysis(
            phrases = attributed.reindex(),
            characters = characters.values.toList(),
            analyzedPhraseRange = attributed.indices,
            source = CharacterAnalysisSource.RULES,
        )
    }

    private fun analyzeFocused(
        request: CharacterAnalysisRequest,
        characters: MutableMap<String, CharacterProfile>,
    ): CharacterAnalysis {
        val phrases = request.existingPhrases.toMutableList()
        val focusIndex = phrases
            .indexOfFirst { it.end > request.focusPosition }
            .takeIf { it >= 0 }
            ?: phrases.lastIndex
        val phrase = phrases[focusIndex]
        val previousPhrase = phrases.getOrNull(focusIndex - 1)
        val previousSpeaker = previousPhrase
            ?.speakerId
            ?.takeIf { it != NARRATOR_ID }
        val recentSpeakers = ArrayDeque<String>()
        phrases
            .subList((focusIndex - RECENT_SPEAKER_PHRASES).coerceAtLeast(0), focusIndex)
            .asReversed()
            .map(SpeechPhrase::speakerId)
            .filter { it != NARRATOR_ID }
            .distinct()
            .forEach(recentSpeakers::addLast)
        val precedingTagSpeaker = previousPhrase
            ?.takeIf {
                phrase.isDialogueIn(request.text) &&
                    request.text.substring(it.end, phrase.start).isBlank()
            }
            ?.let { tag ->
                findNamedSpeaker(tag.text)
                    ?.let { findOrCreateCharacter(it, characters).id }
                    ?: when {
                        containsMaleAttribution(tag.text) ->
                            recentSpeakers.firstOrNull {
                                characters[it]?.voiceGroup == VoiceGroup.MALE
                            }

                        containsFemaleAttribution(tag.text) ->
                            recentSpeakers.firstOrNull {
                                characters[it]?.voiceGroup == VoiceGroup.FEMALE
                            }

                        else -> null
                    }
            }

        val speaker = when {
            previousSpeaker != null &&
                phrase.isDialogueIn(request.text) &&
                (
                    request.text.isInsideDialogueAt(phrase.start) ||
                        (
                            request.text.isSameParagraph(previousPhrase.end, phrase.start) &&
                                findNamedSpeaker(phrase.text) == null
                            ) ||
                        (
                            previousPhrase.containsDialogue() &&
                                findNamedSpeaker(previousPhrase.text) != null &&
                                request.text.substring(previousPhrase.end, phrase.start).isBlank() &&
                                findNamedSpeaker(phrase.text) == null
                            )
                    ) ->
                previousSpeaker

            precedingTagSpeaker != null && findNamedSpeaker(phrase.text) == null ->
                precedingTagSpeaker

            !phrase.isDialogueIn(request.text) -> NARRATOR_ID
            else -> attributeDialogue(
                text = request.text,
                phrase = phrase,
                previousPhrase = previousPhrase,
                characters = characters,
                recentSpeakers = recentSpeakers,
            )
        }
        phrases[focusIndex] = phrase.copy(speakerId = speaker)
        return CharacterAnalysis(
            phrases = phrases,
            characters = characters.values.toList(),
            analyzedPhraseRange = focusIndex..focusIndex,
            source = CharacterAnalysisSource.RULES,
        )
    }

    private fun attributeDialogue(
        text: String,
        phrase: SpeechPhrase,
        previousPhrase: SpeechPhrase?,
        characters: MutableMap<String, CharacterProfile>,
        recentSpeakers: ArrayDeque<String>,
    ): String {
        val context = text.paragraphContextAround(phrase)
        val namedSpeaker = findNamedSpeakerAround(text, phrase)
        return when {
            namedSpeaker != null -> findOrCreateCharacter(
                candidate = namedSpeaker,
                characters = characters,
            ).also { rememberSpeaker(recentSpeakers, it.id) }.id

            containsMaleAttribution(context) ->
                recentSpeakers.firstOrNull {
                    characters[it]?.voiceGroup == VoiceGroup.MALE
                }

            containsFemaleAttribution(context) ->
                recentSpeakers.firstOrNull {
                    characters[it]?.voiceGroup == VoiceGroup.FEMALE
                }

            previousPhrase != null ->
                actionBeatSpeaker(
                    text = text,
                    phrase = phrase,
                    previousPhrase = previousPhrase,
                    characters = characters,
                    recentSpeakers = recentSpeakers,
                ) ?: alternatingSpeaker(recentSpeakers)

            else -> alternatingSpeaker(recentSpeakers)
        } ?: NARRATOR_ID
    }

    private fun actionBeatSpeaker(
        text: String,
        phrase: SpeechPhrase,
        previousPhrase: SpeechPhrase,
        characters: Map<String, CharacterProfile>,
        recentSpeakers: ArrayDeque<String>,
    ): String? {
        if (previousPhrase.isDialogueIn(text) ||
            !text.isSameParagraph(previousPhrase.end, phrase.start)
        ) {
            return null
        }
        if (silentListenerAction.containsMatchIn(previousPhrase.text)) {
            return recentSpeakers.firstOrNull()
        }

        characters.values
            .asSequence()
            .filterNot { it.id == NARRATOR_ID }
            .flatMap { profile ->
                profile.aliases
                    .plus(profile.displayName)
                    .asSequence()
                    .mapNotNull { alias ->
                        previousPhrase.text.lastNameIndex(alias)
                            .takeIf { it >= 0 }
                            ?.let { index -> profile to index }
                    }
            }
            .maxByOrNull { it.second }
            ?.first
            ?.let { return it.id }

        val pronoun = subjectPronoun.findAll(previousPhrase.text).lastOrNull()?.value
            ?: return null
        val group = if (pronoun.equals("he", ignoreCase = true)) {
            VoiceGroup.MALE
        } else {
            VoiceGroup.FEMALE
        }
        return recentSpeakers.firstOrNull { characters[it]?.voiceGroup == group }
    }

    private fun String.lastNameIndex(name: String): Int {
        if (name.length < 2) return -1
        return Regex(
            """(?i)(?<![\p{L}\p{N}])${Regex.escape(name)}(?![\p{L}\p{N}])""",
        ).findAll(this).lastOrNull()?.range?.first ?: -1
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

    private fun findNamedSpeakerAround(
        text: String,
        phrase: SpeechPhrase,
    ): String? {
        findNamedSpeaker(phrase.text)?.let { return it }
        val paragraphStart = text.lastIndexOf(
            "\n\n",
            (phrase.start - 1).coerceAtLeast(0),
        ).let { if (it < 0) 0 else it + 2 }
        val paragraphEnd = text.indexOf("\n\n", phrase.end.coerceAtMost(text.length))
            .let { if (it < 0) text.length else it }
        val after = text.substring(
            phrase.end,
            minOf(paragraphEnd, phrase.end + CONTEXT_RADIUS),
        )
        findNamedSpeaker(after)?.let { return it }
        val before = text.substring(
            maxOf(paragraphStart, phrase.start - CONTEXT_RADIUS),
            phrase.start,
        )
        return findNamedSpeaker(before)
    }

    private fun findOrCreateCharacter(
        candidate: String,
        characters: MutableMap<String, CharacterProfile>,
    ): CharacterProfile {
        val normalizedCandidate = normalizeName(candidate)
        characters.values.firstOrNull { profile ->
            profile.aliases.any { normalizeName(it) == normalizedCandidate } ||
                normalizeName(profile.displayName) == normalizedCandidate
        }?.let { return it }

        val voiceGroup = inferVoiceGroup(candidate)
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

    private fun inferVoiceGroup(name: String): VoiceGroup {
        val normalized = name.lowercase(Locale.ROOT)
        return when {
            normalized.startsWith("mr ") ||
                normalized.startsWith("sir ") ||
                normalized.startsWith("lord ") -> VoiceGroup.MALE

            normalized.startsWith("mrs ") ||
                normalized.startsWith("ms ") ||
                normalized.startsWith("miss ") ||
                normalized.startsWith("lady ") -> VoiceGroup.FEMALE

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

    private fun SpeechPhrase.isDialogueIn(bookText: String): Boolean =
        containsDialogue() || bookText.isInsideDialogueAt(start)

    private fun String.isInsideDialogueAt(position: Int): Boolean {
        val safePosition = position.coerceIn(0, length)
        val paragraphStart = lastIndexOf("\n\n", (safePosition - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 2 }
        val paragraphPrefix = substring(paragraphStart, safePosition)
        val straightOpen = paragraphPrefix.count { it == '"' } % 2 == 1
        val curlyOpen = paragraphPrefix.count { it == '\u201c' } >
            paragraphPrefix.count { it == '\u201d' }
        val guillemetOpen = paragraphPrefix.count { it == '\u00ab' } >
            paragraphPrefix.count { it == '\u00bb' }
        return straightOpen || curlyOpen || guillemetOpen
    }

    private fun String.isSameParagraph(
        previousEnd: Int,
        currentStart: Int,
    ): Boolean {
        if (previousEnd !in 0..length || currentStart !in previousEnd..length) return false
        return "\n\n" !in substring(previousEnd, currentStart)
    }

    private fun String.paragraphContextAround(phrase: SpeechPhrase): String {
        val paragraphStart = lastIndexOf("\n\n", (phrase.start - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 2 }
        val paragraphEnd = indexOf("\n\n", phrase.end.coerceAtMost(length))
            .let { if (it < 0) length else it }
        val contextStart = maxOf(paragraphStart, phrase.start - CONTEXT_RADIUS)
        val contextEnd = minOf(paragraphEnd, phrase.end + CONTEXT_RADIUS)
        return substring(contextStart, contextEnd)
    }

    private fun List<SpeechPhrase>.reindex(): List<SpeechPhrase> =
        mapIndexed { index, phrase -> phrase.copy(index = index) }

    private companion object {
        const val CONTEXT_RADIUS = 110
        const val RECENT_SPEAKER_PHRASES = 12

        val dialogueMarks = setOf('"', '“', '”', '„', '«', '»')
        val speechVerbs =
            """said|asked|replied|answered|whispered|murmured|shouted|cried|called|added|continued|remarked|exclaimed|observed"""
        val namePattern =
            """(?<name>(?:(?:Mr|Mrs|Ms|Miss|Sir|Lady|Lord|Dr)\.?\s+)?[\p{Lu}][\p{L}'-]+(?:\s+[\p{Lu}][\p{L}'-]+)?)"""

        val attributionPatterns = listOf(
            Regex("""\b$namePattern\s+(?i:$speechVerbs)\b"""),
            Regex("""\b(?i:$speechVerbs)\s+$namePattern\b"""),
        )

        val subjectPronoun = Regex("""\b(?:he|she)\b""", RegexOption.IGNORE_CASE)
        val silentListenerAction = Regex(
            """\b(?:shook\s+(?:his|her|their)\s+head|nodded|shrugged)\b""",
            RegexOption.IGNORE_CASE,
        )
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
