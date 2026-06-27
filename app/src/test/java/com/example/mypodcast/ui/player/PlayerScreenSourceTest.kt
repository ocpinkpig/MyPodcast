package com.example.mypodcast.ui.player

import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.io.File

class PlayerScreenSourceTest {
    @Test
    fun addToQueueClick_showsAnimatedFeedback() {
        val source = File("src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt").readText()

        assertTrue(source.contains("AnimatedVisibility"))
        assertTrue(source.contains("\"Added to queue\""))
        assertTrue(source.contains("PlayerFeedback.Queue"))
    }

    @Test
    fun showNotesTimestamps_areClickableAndStartPlayback() {
        val source = File("src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt").readText()

        assertTrue(source.contains("findTimestampLinks"))
        assertTrue(source.contains("LinkAnnotation.Clickable"))
        assertTrue(source.contains("onSeekAndPlay"))
        assertTrue(source.contains("viewModel::seekToAndPlay"))
    }
}
