package com.example.mypodcast.data.remote.transcript

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class SrtParserTest {

    @Test
    fun parsesNumberedCuesWithCommaMillis() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,000
            Hello and welcome.

            2
            00:00:04,500 --> 00:00:07,250
            This is the second line.
        """.trimIndent()

        val transcript = SrtParser.parse(srt)

        assertTrue(transcript.isSynced)
        assertEquals(2, transcript.cues.size)
        assertEquals(1_000L, transcript.cues[0].startMs)
        assertEquals(4_000L, transcript.cues[0].endMs)
        assertEquals("Hello and welcome.", transcript.cues[0].text)
        assertEquals(4_500L, transcript.cues[1].startMs)
        assertEquals(7_250L, transcript.cues[1].endMs)
        assertEquals("This is the second line.", transcript.cues[1].text)
    }

    @Test
    fun joinsMultiLineCueText() {
        val srt = """
            1
            00:00:00,000 --> 00:00:02,000
            First part
            second part
        """.trimIndent()

        val transcript = SrtParser.parse(srt)

        assertEquals(1, transcript.cues.size)
        assertEquals("First part second part", transcript.cues[0].text)
    }

    @Test
    fun returnsEmptyForBlankInput() {
        assertTrue(SrtParser.parse("").cues.isEmpty())
    }
}
