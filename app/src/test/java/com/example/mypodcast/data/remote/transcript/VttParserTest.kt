package com.example.mypodcast.data.remote.transcript

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class VttParserTest {

    @Test
    fun parsesCuesWithTimestampsAndText() {
        val vtt = """
            WEBVTT

            00:00:01.000 --> 00:00:04.000
            Hello and welcome.

            00:00:04.500 --> 00:00:07.250
            This is the second line.
        """.trimIndent()

        val transcript = VttParser.parse(vtt)

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
    fun joinsMultiLineCueTextWithSpaces() {
        val vtt = """
            WEBVTT

            00:00:00.000 --> 00:00:02.000
            First part
            second part
        """.trimIndent()

        val transcript = VttParser.parse(vtt)

        assertEquals(1, transcript.cues.size)
        assertEquals("First part second part", transcript.cues[0].text)
    }

    @Test
    fun supportsShortMinuteSecondTimestamps() {
        val vtt = """
            WEBVTT

            01:02.500 --> 01:05.000
            Short form timestamp.
        """.trimIndent()

        val transcript = VttParser.parse(vtt)

        assertEquals(1, transcript.cues.size)
        assertEquals(62_500L, transcript.cues[0].startMs)
        assertEquals(65_000L, transcript.cues[0].endMs)
    }

    @Test
    fun ignoresHeaderNoteAndCueIdentifierLines() {
        val vtt = """
            WEBVTT - Some title

            NOTE this is a comment

            cue-1
            00:00:01.000 --> 00:00:02.000
            Only spoken text.
        """.trimIndent()

        val transcript = VttParser.parse(vtt)

        assertEquals(1, transcript.cues.size)
        assertEquals("Only spoken text.", transcript.cues[0].text)
    }

    @Test
    fun returnsEmptyForBlankInput() {
        val transcript = VttParser.parse("")

        assertTrue(transcript.cues.isEmpty())
    }
}
