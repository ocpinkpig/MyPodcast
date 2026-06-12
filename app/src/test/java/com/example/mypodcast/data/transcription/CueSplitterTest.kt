package com.example.mypodcast.data.transcription

import org.junit.Assert.assertEquals
import org.junit.Test

class CueSplitterTest {

    @Test
    fun `multi-sentence segment splits with proportional contiguous timestamps`() {
        // 27 + 26 = 53 chars over a 5300ms span -> boundary at 2700ms.
        val cues = splitIntoSentenceCues(
            text = "Hello there my good friend. The weather is nice today!",
            startMs = 0,
            endMs = 5_300
        )

        assertEquals(2, cues.size)
        assertEquals("Hello there my good friend.", cues[0].text)
        assertEquals("The weather is nice today!", cues[1].text)
        assertEquals(0L, cues[0].startMs)
        assertEquals(2_700L, cues[0].endMs)
        assertEquals(2_700L, cues[1].startMs) // contiguous
        assertEquals(5_300L, cues[1].endMs) // last cue ends exactly at segment end
    }

    @Test
    fun `single sentence passes through unchanged`() {
        val cues = splitIntoSentenceCues("Just one sentence here.", startMs = 1_000, endMs = 4_000)

        assertEquals(1, cues.size)
        assertEquals("Just one sentence here.", cues.single().text)
        assertEquals(1_000L, cues.single().startMs)
        assertEquals(4_000L, cues.single().endMs)
    }

    @Test
    fun `abbreviations do not explode into fragments`() {
        val cues = splitIntoSentenceCues(
            text = "Dr. Smith arrived at the office. He sat down immediately after.",
            startMs = 0,
            endMs = 6_000
        )

        assertEquals(2, cues.size)
        assertEquals("Dr. Smith arrived at the office.", cues[0].text)
        assertEquals("He sat down immediately after.", cues[1].text)
    }

    @Test
    fun `trailing short fragment merges into previous sentence`() {
        val cues = splitIntoSentenceCues(
            text = "This is the first full sentence of the segment. The end.",
            startMs = 0,
            endMs = 5_000
        )

        assertEquals(1, cues.size)
        assertEquals(
            "This is the first full sentence of the segment. The end.",
            cues.single().text
        )
        assertEquals(5_000L, cues.single().endMs)
    }

    @Test
    fun `question and exclamation marks are sentence boundaries`() {
        val cues = splitIntoSentenceCues(
            text = "Is this really the best approach for us? Absolutely it is the best one!",
            startMs = 0,
            endMs = 8_000
        )

        assertEquals(2, cues.size)
        assertEquals("Is this really the best approach for us?", cues[0].text)
        assertEquals("Absolutely it is the best one!", cues[1].text)
    }

    @Test
    fun `blank text yields no cues`() {
        assertEquals(emptyList<Any>(), splitIntoSentenceCues("   ", 0, 1_000))
    }
}
