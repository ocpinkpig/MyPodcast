package com.example.mypodcast.data.remote.transcript

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class PlainTextTranscriptParserTest {

    @Test
    fun producesOneUnsyncedCuePerParagraph() {
        val text = """
            First paragraph line one.
            First paragraph line two.

            Second paragraph.
        """.trimIndent()

        val transcript = PlainTextTranscriptParser.parse(text)

        assertFalse(transcript.isSynced)
        assertEquals(2, transcript.cues.size)
        assertEquals("First paragraph line one. First paragraph line two.", transcript.cues[0].text)
        assertEquals("Second paragraph.", transcript.cues[1].text)
        assertEquals(0L, transcript.cues[0].startMs)
    }

    @Test
    fun collapsesMultipleBlankLinesBetweenParagraphs() {
        val text = "One.\n\n\n\nTwo."

        val transcript = PlainTextTranscriptParser.parse(text)

        assertEquals(2, transcript.cues.size)
        assertEquals("One.", transcript.cues[0].text)
        assertEquals("Two.", transcript.cues[1].text)
    }

    @Test
    fun returnsEmptyForBlankInput() {
        assertTrue(PlainTextTranscriptParser.parse("   \n  ").cues.isEmpty())
    }
}
