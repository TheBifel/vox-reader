package com.bifel.voxreader.data.analysis

import com.bifel.voxreader.domain.model.NARRATOR_ID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedCharacterAnalyzerTest {

    @Test
    fun `attributes explicit dialogue and preserves narrator sentences`() = runTest {
        val text = "\"Stay here,\" Alice said. \"I will not,\" Bob replied. The door closed."

        val result = RuleBasedCharacterAnalyzer().analyze(text, emptyList())

        assertEquals("alice", result.phrases[0].speakerId)
        assertEquals("bob", result.phrases[1].speakerId)
        assertEquals(NARRATOR_ID, result.phrases[2].speakerId)
        assertTrue(result.characters.any { it.id == "alice" })
        assertTrue(result.characters.any { it.id == "bob" })
    }
}

