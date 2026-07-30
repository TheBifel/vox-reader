package com.bifel.voxreader.data.analysis

import android.util.Log
import com.bifel.voxreader.domain.analysis.CharacterAnalysisRequest
import com.bifel.voxreader.domain.analysis.CharacterAnalyzer
import com.bifel.voxreader.domain.analysis.SpeakerScorer
import com.bifel.voxreader.domain.model.CharacterAnalysis
import com.bifel.voxreader.domain.model.CharacterAnalysisSource
import com.bifel.voxreader.domain.model.CharacterProfile
import com.bifel.voxreader.domain.model.NARRATOR_ID
import com.bifel.voxreader.domain.model.NarratorProfile
import com.bifel.voxreader.domain.model.SpeechPhrase
import com.bifel.voxreader.domain.model.VoiceGroup
import java.util.Locale

/**
 * Keeps deterministic attribution in charge of explicit dialogue and asks a compact
 * classifier only to rank dynamic character candidates for ambiguous dialogue.
 */
class TinyBertCharacterAnalyzer(
    private val fallback: CharacterAnalyzer,
    private val scorer: SpeakerScorer,
) : CharacterAnalyzer {

    override suspend fun analyze(request: CharacterAnalysisRequest): CharacterAnalysis {
        val baseline = fallback.analyze(request)
        if (baseline.phrases.isEmpty()) return baseline

        val focusIndex = baseline.phrases
            .indexOfFirst { it.end > request.focusPosition }
            .takeIf { it >= 0 }
            ?: baseline.phrases.lastIndex
        val phrases = preservePreviousAttribution(
            baseline = baseline.phrases,
            existing = request.existingPhrases,
            ranges = request.preservedPhraseRanges,
        ).toMutableList()
        val targetPhrase = phrases[focusIndex]
        val analyzedRange = focusIndex..focusIndex
        if (!targetPhrase.isDialogueIn(request.text)) {
            return baseline.copy(
                phrases = phrases,
                analyzedPhraseRange = analyzedRange,
                source = CharacterAnalysisSource.RULES,
            )
        }

        val characters = linkedMapOf<String, CharacterProfile>()
        characters[NARRATOR_ID] = NarratorProfile
        baseline.characters.forEach { characters[it.id] = it }
        request.knownCharacters.forEach { characters[it.id] = it }

        val previousSpeaker = phrases
            .getOrNull(focusIndex - 1)
            ?.speakerId
            ?.takeIf { it != NARRATOR_ID }
        if (previousSpeaker != null &&
            (
                request.text.isInsideDialogueAt(targetPhrase.start) ||
                    (
                        request.text.isSameParagraph(
                            phrases[focusIndex - 1].end,
                            targetPhrase.start,
                        ) &&
                            request.text.explicitSpeakerFor(targetPhrase, characters.values) == null
                        )
                )
        ) {
            phrases[focusIndex] = targetPhrase.copy(speakerId = previousSpeaker)
            return CharacterAnalysis(
                phrases = phrases,
                characters = characters.values.toList(),
                analyzedPhraseRange = analyzedRange,
                source = CharacterAnalysisSource.RULES,
            )
        }

        val explicitProfile = request.text.explicitSpeakerFor(targetPhrase, characters.values)
        if (explicitProfile != null) {
            val grounded = explicitProfile.withGroundedVoice(
                request.text.genderHintFor(explicitProfile.displayName, targetPhrase),
                characters.values,
            )
            characters[grounded.id] = grounded
            phrases[focusIndex] = targetPhrase.copy(speakerId = grounded.id)
            logInfo(
                "Resolved phrase ${targetPhrase.index} as ${grounded.displayName} " +
                    "(${grounded.voiceGroup}) from an explicit dialogue tag",
            )
            return CharacterAnalysis(
                phrases = phrases,
                characters = characters.values.toList(),
                analyzedPhraseRange = analyzedRange,
                source = CharacterAnalysisSource.RULES,
            )
        }

        val deterministicProfile = characters[targetPhrase.speakerId]
            ?.takeIf { it.id != NARRATOR_ID }
        if (deterministicProfile != null &&
            phrases.hasAdjacentDialogueTag(
                focusIndex = focusIndex,
                bookText = request.text,
                profile = deterministicProfile,
            )
        ) {
            return CharacterAnalysis(
                phrases = phrases,
                characters = characters.values.toList(),
                analyzedPhraseRange = analyzedRange,
                source = CharacterAnalysisSource.RULES,
            )
        }

        val scene = buildScene(phrases, focusIndex)
        val candidates = candidateProfiles(
            scene = scene,
            focusIndex = focusIndex,
            phrases = phrases,
            characters = characters,
        )
        if (candidates.size < 2) {
            return baseline.copy(
                phrases = phrases,
                characters = characters.values.toList(),
                analyzedPhraseRange = analyzedRange,
                source = CharacterAnalysisSource.RULES,
            )
        }

        val scores = runCatching {
            scorer.scoreCandidates(
                scene = scene,
                candidates = candidates.map(CharacterProfile::displayName),
            )
        }.getOrElse { error ->
            logWarning("TinyBERT attribution failed; using deterministic rules", error)
            return baseline.copy(
                phrases = phrases,
                characters = characters.values.toList(),
                analyzedPhraseRange = analyzedRange,
                source = CharacterAnalysisSource.RULES,
            )
        }
        if (scores.size != candidates.size) {
            logWarning(
                "TinyBERT returned ${scores.size} scores for ${candidates.size} candidates",
                null,
            )
            return baseline.copy(
                phrases = phrases,
                characters = characters.values.toList(),
                analyzedPhraseRange = analyzedRange,
                source = CharacterAnalysisSource.RULES,
            )
        }

        val ranked = candidates
            .zip(scores)
            .sortedByDescending { it.second }
        val winner = ranked.first()
        val runnerUpScore = ranked.getOrNull(1)?.second ?: 0f
        val accepted = winner.second >= MINIMUM_SCORE &&
            winner.second - runnerUpScore >= MINIMUM_MARGIN
        if (!accepted) {
            logInfo(
                "TinyBERT kept the rule result for phrase ${targetPhrase.index}; " +
                    "best=${winner.first.displayName}:${winner.second.formatScore()}, " +
                    "margin=${(winner.second - runnerUpScore).formatScore()}",
            )
            return baseline.copy(
                phrases = phrases,
                characters = characters.values.toList(),
                analyzedPhraseRange = analyzedRange,
                source = CharacterAnalysisSource.RULES,
            )
        }

        val groundedWinner = winner.first.withGroundedVoice(
            request.text.genderHintFor(winner.first.displayName, targetPhrase),
            characters.values,
        )
        characters[groundedWinner.id] = groundedWinner
        phrases[focusIndex] = targetPhrase.copy(speakerId = groundedWinner.id)
        logInfo(
            "TinyBERT resolved phrase ${targetPhrase.index} as ${groundedWinner.displayName}; " +
                "score=${winner.second.formatScore()}, margin=" +
                (winner.second - runnerUpScore).formatScore(),
        )
        return CharacterAnalysis(
            phrases = phrases,
            characters = characters.values.toList(),
            analyzedPhraseRange = analyzedRange,
            source = CharacterAnalysisSource.TINY_BERT,
        )
    }

    private fun preservePreviousAttribution(
        baseline: List<SpeechPhrase>,
        existing: List<SpeechPhrase>,
        ranges: List<IntRange>,
    ): List<SpeechPhrase> {
        if (existing.size != baseline.size || ranges.isEmpty()) return baseline
        val preserved = baseline.toMutableList()
        ranges.forEach { range ->
            range.forEach { index ->
                existing.getOrNull(index)?.let { preserved[index] = it }
            }
        }
        return preserved
    }

    private fun buildScene(
        phrases: List<SpeechPhrase>,
        focusIndex: Int,
    ): String {
        val start = (focusIndex - CONTEXT_PHRASES_BEFORE).coerceAtLeast(0)
        val end = (focusIndex + CONTEXT_PHRASES_AFTER).coerceAtMost(phrases.lastIndex)
        return (start..end).joinToString(" ") { index ->
            val cleanText = phrases[index].text
                .replace(Regex("""\s+"""), " ")
                .trim()
                .take(MAX_PHRASE_CHARACTERS)
            if (index == focusIndex) "Target dialogue: $cleanText" else cleanText
        }
    }

    private fun candidateProfiles(
        scene: String,
        focusIndex: Int,
        phrases: List<SpeechPhrase>,
        characters: LinkedHashMap<String, CharacterProfile>,
    ): List<CharacterProfile> {
        val ids = linkedSetOf<String>()
        phrases.getOrNull(focusIndex)?.speakerId
            ?.takeIf { it != NARRATOR_ID }
            ?.let(ids::add)
        phrases
            .subList((focusIndex - RECENT_SPEAKER_PHRASES).coerceAtLeast(0), focusIndex)
            .asReversed()
            .map(SpeechPhrase::speakerId)
            .filter { it != NARRATOR_ID }
            .forEach(ids::add)
        characters.values
            .filterNot { it.id == NARRATOR_ID }
            .filter { profile ->
                profile.aliases.any { alias -> scene.containsName(alias) } ||
                    scene.containsName(profile.displayName)
            }
            .forEach { ids.add(it.id) }
        characters.values
            .filterNot { it.id == NARRATOR_ID }
            .forEach { ids.add(it.id) }

        return ids
            .mapNotNull(characters::get)
            .distinctBy(CharacterProfile::id)
            .take(MAX_CANDIDATES)
    }

    private fun String.containsName(name: String): Boolean {
        if (name.length < 2) return false
        return Regex(
            """(?i)(?<![\p{L}\p{N}])${Regex.escape(name)}(?![\p{L}\p{N}])""",
        ).containsMatchIn(this)
    }

    private fun List<SpeechPhrase>.hasAdjacentDialogueTag(
        focusIndex: Int,
        bookText: String,
        profile: CharacterProfile,
    ): Boolean =
        listOfNotNull(
            getOrNull(focusIndex - 1),
            getOrNull(focusIndex + 1),
        ).any { adjacent ->
            val target = getOrNull(focusIndex) ?: return@any false
            val gapStart = minOf(target.end, adjacent.end)
            val gapEnd = maxOf(target.start, adjacent.start)
            if (gapStart !in 0..bookText.length ||
                gapEnd !in gapStart..bookText.length ||
                !bookText.substring(gapStart, gapEnd).isBlank()
            ) {
                return@any false
            }
            adjacent.text.isDialogueTagFor(profile)
        }

    private fun String.isDialogueTagFor(profile: CharacterProfile): Boolean {
        val names = profile.aliases + profile.displayName
        if (names.any { name ->
                val escaped = Regex.escape(name)
                Regex(
                    """(?i)(?:\b$escaped\s+(?:$speechVerbs)\b|""" +
                        """\b(?:$speechVerbs)\s+$escaped\b)""",
                ).containsMatchIn(this)
            }
        ) {
            return true
        }
        val pronouns = when (profile.voiceGroup) {
            VoiceGroup.MALE -> "he|him"
            VoiceGroup.FEMALE -> "she|her"
            else -> return false
        }
        return Regex(
            """(?i)(?:\b(?:$pronouns)\s+(?:$speechVerbs)\b|""" +
                """\b(?:$speechVerbs)\s+(?:$pronouns)\b)""",
        ).containsMatchIn(this)
    }

    private fun String.explicitSpeakerFor(
        phrase: SpeechPhrase,
        characters: Collection<CharacterProfile>,
    ): CharacterProfile? {
        val contextStart = (phrase.start - DIALOGUE_TAG_CONTEXT_BEFORE).coerceAtLeast(0)
        val contextEnd = (phrase.end + DIALOGUE_TAG_CONTEXT_AFTER).coerceAtMost(length)
        val context = substring(contextStart, contextEnd)
        return characters
            .asSequence()
            .filterNot { it.id == NARRATOR_ID }
            .firstOrNull { profile ->
                profile.aliases.plus(profile.displayName).any { name ->
                    val escapedName = Regex.escape(name)
                    Regex(
                        """(?i)(?:\b$escapedName\s+(?:$speechVerbs)\b|""" +
                            """\b(?:$speechVerbs)\s+$escapedName\b)""",
                    ).findAll(context).any { match ->
                        val absoluteStart = contextStart + match.range.first
                        val absoluteEnd = contextStart + match.range.last + 1
                        when {
                            absoluteStart < phrase.end && absoluteEnd > phrase.start -> true
                            absoluteEnd <= phrase.start -> {
                                val between = substring(absoluteEnd, phrase.start)
                                between.length <= MAX_TAG_DISTANCE_BEFORE &&
                                    "\n\n" !in between
                            }
                            absoluteStart >= phrase.end -> {
                                val between = substring(phrase.end, absoluteStart)
                                between.length <= MAX_TAG_DISTANCE_AFTER &&
                                    "\n\n" !in between
                            }
                            else -> false
                        }
                    }
                }
            }
    }

    private fun String.genderHintFor(
        characterName: String,
        phrase: SpeechPhrase,
    ): VoiceGroup? =
        bookWideGenderHint(characterName) ?: nearbyGenderHint(phrase)

    private fun String.nearbyGenderHint(phrase: SpeechPhrase): VoiceGroup? {
        val afterEnd = (phrase.end + GENDER_HINT_CONTEXT_CHARACTERS).coerceAtMost(length)
        val after = substring(phrase.end.coerceIn(0, afterEnd), afterEnd)
        val following = genderPronounPattern.find(after)
        if (following != null && following.range.first <= MAX_GENDER_HINT_DISTANCE) {
            return following.value.toVoiceGroup()
        }

        val contextStart = (phrase.start - GENDER_HINT_CONTEXT_CHARACTERS).coerceAtLeast(0)
        val before = substring(contextStart, phrase.start.coerceIn(contextStart, length))
        val nearest = genderPronounPattern.findAll(before).lastOrNull() ?: return null
        val distance = before.length - nearest.range.last - 1
        if (distance > MAX_GENDER_HINT_DISTANCE) return null
        return nearest.value.toVoiceGroup()
    }

    private fun String.toVoiceGroup(): VoiceGroup =
        if (equals("he", ignoreCase = true)) VoiceGroup.MALE else VoiceGroup.FEMALE

    private fun String.bookWideGenderHint(characterName: String): VoiceGroup? {
        val normalizedName = characterName.trim()
        if (normalizedName.length < 2) return null
        val namePattern = Regex(
            """(?i)(?<![\p{L}\p{N}])${Regex.escape(normalizedName)}(?![\p{L}\p{N}])""",
        )
        var maleEvidence = 0
        var femaleEvidence = 0
        namePattern.findAll(this)
            .take(MAX_BOOK_WIDE_NAME_OCCURRENCES)
            .forEach { occurrence ->
                val paragraphStart = lastIndexOf("\n\n", occurrence.range.first)
                    .let { if (it < 0) 0 else it + 2 }
                val paragraphEnd = indexOf("\n\n", occurrence.range.last + 1)
                    .let { if (it < 0) length else it }
                val windowStart = maxOf(
                    paragraphStart,
                    occurrence.range.first - BOOK_WIDE_EVIDENCE_RADIUS,
                )
                val windowEnd = minOf(
                    paragraphEnd,
                    occurrence.range.last + 1 + BOOK_WIDE_EVIDENCE_RADIUS,
                )
                genderEvidencePattern.findAll(substring(windowStart, windowEnd))
                    .forEach { pronoun ->
                        when (pronoun.value.lowercase(Locale.ROOT)) {
                            "he", "him", "his", "himself" -> maleEvidence++
                            "she", "her", "hers", "herself" -> femaleEvidence++
                        }
                    }
            }

        val winningEvidence = maxOf(maleEvidence, femaleEvidence)
        val losingEvidence = minOf(maleEvidence, femaleEvidence)
        if (winningEvidence < MIN_BOOK_WIDE_EVIDENCE ||
            winningEvidence < losingEvidence + MIN_BOOK_WIDE_MARGIN ||
            winningEvidence < (losingEvidence * BOOK_WIDE_DOMINANCE_RATIO)
        ) {
            return null
        }
        return if (maleEvidence > femaleEvidence) VoiceGroup.MALE else VoiceGroup.FEMALE
    }

    private fun CharacterProfile.withGroundedVoice(
        hint: VoiceGroup?,
        allCharacters: Collection<CharacterProfile>,
    ): CharacterProfile {
        val group = hint?.takeIf { it == VoiceGroup.MALE || it == VoiceGroup.FEMALE }
            ?: return this
        if (voiceGroup == group) return this
        val groupCount = allCharacters.count { it.id != id && it.voiceGroup == group }
        val slot = when (group) {
            VoiceGroup.MALE -> 1 + (groupCount % 2)
            VoiceGroup.FEMALE -> 3 + (groupCount % 2)
            else -> voiceSlot
        }
        return copy(
            voiceGroup = group,
            voiceSlot = slot,
            pitch = when (group) {
                VoiceGroup.MALE -> if (slot % 2 == 0) 0.78f else 0.9f
                VoiceGroup.FEMALE -> if (slot % 2 == 0) 1.22f else 1.1f
                else -> pitch
            },
            speechRate = if (group == VoiceGroup.MALE && slot % 2 == 0) 0.95f else 1f,
        )
    }

    private fun SpeechPhrase.containsDialogue(): Boolean =
        text.any { it in dialogueMarks }

    private fun SpeechPhrase.isDialogueIn(bookText: String): Boolean =
        containsDialogue() || bookText.isInsideDialogueAt(start)

    private fun String.isInsideDialogueAt(position: Int): Boolean {
        val safePosition = position.coerceIn(0, length)
        val paragraphStart = lastIndexOf("\n\n", (safePosition - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 2 }
        val prefix = substring(paragraphStart, safePosition)
        return prefix.count { it == '"' } % 2 == 1 ||
            prefix.count { it == '\u201c' } > prefix.count { it == '\u201d' } ||
            prefix.count { it == '\u00ab' } > prefix.count { it == '\u00bb' }
    }

    private fun String.isSameParagraph(previousEnd: Int, currentStart: Int): Boolean {
        if (previousEnd !in 0..length || currentStart !in previousEnd..length) return false
        return "\n\n" !in substring(previousEnd, currentStart)
    }

    private fun Float.formatScore(): String = String.format(Locale.ROOT, "%.3f", this)

    private fun logInfo(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private fun logWarning(message: String, error: Throwable?) {
        runCatching {
            if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error)
        }
    }

    private companion object {
        const val TAG = "VoxReaderTinyBERT"
        const val CONTEXT_PHRASES_BEFORE = 3
        const val CONTEXT_PHRASES_AFTER = 2
        const val MAX_PHRASE_CHARACTERS = 220
        const val RECENT_SPEAKER_PHRASES = 16
        const val MAX_CANDIDATES = 6
        const val MINIMUM_SCORE = 0.72f
        const val MINIMUM_MARGIN = 0.12f
        const val DIALOGUE_TAG_CONTEXT_BEFORE = 60
        const val DIALOGUE_TAG_CONTEXT_AFTER = 120
        const val MAX_TAG_DISTANCE_BEFORE = 24
        const val MAX_TAG_DISTANCE_AFTER = 48
        const val GENDER_HINT_CONTEXT_CHARACTERS = 180
        const val MAX_GENDER_HINT_DISTANCE = 80
        const val BOOK_WIDE_EVIDENCE_RADIUS = 140
        const val MAX_BOOK_WIDE_NAME_OCCURRENCES = 80
        const val MIN_BOOK_WIDE_EVIDENCE = 4
        const val MIN_BOOK_WIDE_MARGIN = 3
        const val BOOK_WIDE_DOMINANCE_RATIO = 1.35

        val dialogueMarks = setOf('"', '\u201c', '\u201d', '\u201e', '\u00ab', '\u00bb')
        val genderPronounPattern = Regex("""\b(?:he|she)\b""", RegexOption.IGNORE_CASE)
        val genderEvidencePattern = Regex(
            """\b(?:he|him|his|himself|she|her|hers|herself)\b""",
            RegexOption.IGNORE_CASE,
        )
        const val speechVerbs =
            "said|asked|replied|answered|whispered|murmured|shouted|cried|" +
                "called|added|continued|remarked|exclaimed|observed"
    }
}
