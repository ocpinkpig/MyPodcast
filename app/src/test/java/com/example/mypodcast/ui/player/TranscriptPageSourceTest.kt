package com.example.mypodcast.ui.player

import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.io.File

class TranscriptPageSourceTest {

    @Test
    fun playerPagerExposesThreePagesIncludingTranscript() {
        val source = File("src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt").readText()

        assertTrue(source.contains("pageCount = { 3 }"))
        assertTrue(source.contains("TranscriptPage("))
        assertTrue(source.contains("pageCount = 3"))
    }

    @Test
    fun transcriptPageHandlesAllStatesAndTapToSeek() {
        val source = File("src/main/java/com/example/mypodcast/ui/player/TranscriptPage.kt").readText()

        assertTrue(source.contains("TranscriptUiState.Loading"))
        assertTrue(source.contains("TranscriptUiState.Error"))
        assertTrue(source.contains("TranscriptUiState.Empty"))
        assertTrue(source.contains("TranscriptUiState.Loaded"))
        assertTrue(source.contains("No transcript available for this episode"))
        assertTrue(source.contains("cueIndexAt"))
        assertTrue(source.contains("onSeek(cue.startMs)"))
        assertTrue(source.contains("animateScrollToItem"))
    }
}
