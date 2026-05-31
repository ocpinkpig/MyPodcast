package com.example.mypodcast.data.remote.transcript

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test

class PodcastJsonParserTest {

    @Test
    fun parsesSegmentsWithSecondsConvertedToMillis() {
        val json = """
            {
              "version": "1.0.0",
              "segments": [
                { "speaker": "Alice", "startTime": 0.0, "endTime": 4.5, "body": "Hello and welcome." },
                { "speaker": "Bob", "startTime": 4.5, "endTime": 7.25, "body": "Thanks for having me." }
              ]
            }
        """.trimIndent()

        val transcript = PodcastJsonParser.parse(json)

        assertTrue(transcript.isSynced)
        assertEquals(2, transcript.cues.size)
        assertEquals(0L, transcript.cues[0].startMs)
        assertEquals(4_500L, transcript.cues[0].endMs)
        assertEquals("Hello and welcome.", transcript.cues[0].text)
        assertEquals("Alice", transcript.cues[0].speaker)
        assertEquals(4_500L, transcript.cues[1].startMs)
        assertEquals(7_250L, transcript.cues[1].endMs)
        assertEquals("Bob", transcript.cues[1].speaker)
    }

    @Test
    fun toleratesMissingSpeakerAndEndTime() {
        val json = """
            { "segments": [ { "startTime": 1.0, "body": "No speaker here." } ] }
        """.trimIndent()

        val transcript = PodcastJsonParser.parse(json)

        assertEquals(1, transcript.cues.size)
        assertEquals(1_000L, transcript.cues[0].startMs)
        assertEquals(0L, transcript.cues[0].endMs)
        assertNull(transcript.cues[0].speaker)
        assertEquals("No speaker here.", transcript.cues[0].text)
    }

    @Test
    fun skipsSegmentsWithBlankBody() {
        val json = """
            { "segments": [
              { "startTime": 0.0, "body": "" },
              { "startTime": 1.0, "body": "Real text." }
            ] }
        """.trimIndent()

        val transcript = PodcastJsonParser.parse(json)

        assertEquals(1, transcript.cues.size)
        assertEquals("Real text.", transcript.cues[0].text)
    }

    @Test
    fun returnsEmptyForMalformedJson() {
        assertTrue(PodcastJsonParser.parse("not json").cues.isEmpty())
    }
}
