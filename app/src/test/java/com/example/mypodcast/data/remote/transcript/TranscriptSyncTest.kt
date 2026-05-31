package com.example.mypodcast.data.remote.transcript

import com.example.mypodcast.domain.model.TranscriptCue
import junit.framework.TestCase.assertEquals
import org.junit.Test

class TranscriptSyncTest {

    private val cues = listOf(
        TranscriptCue(startMs = 0L, endMs = 2_000L, text = "one"),
        TranscriptCue(startMs = 2_000L, endMs = 4_000L, text = "two"),
        TranscriptCue(startMs = 4_000L, endMs = 6_000L, text = "three")
    )

    @Test
    fun returnsMinusOneBeforeFirstCue() {
        val early = listOf(TranscriptCue(startMs = 1_000L, endMs = 2_000L, text = "later"))
        assertEquals(-1, cueIndexAt(early, 500L))
    }

    @Test
    fun returnsActiveCueWithinRange() {
        assertEquals(0, cueIndexAt(cues, 0L))
        assertEquals(0, cueIndexAt(cues, 1_999L))
        assertEquals(1, cueIndexAt(cues, 2_000L))
        assertEquals(2, cueIndexAt(cues, 5_000L))
    }

    @Test
    fun holdsLastCuePastItsEnd() {
        assertEquals(2, cueIndexAt(cues, 10_000L))
    }

    @Test
    fun returnsMinusOneForEmptyCues() {
        assertEquals(-1, cueIndexAt(emptyList(), 1_000L))
    }
}
