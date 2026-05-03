package com.example.mypodcast.ui.detail

import junit.framework.TestCase.assertFalse
import org.junit.Test
import java.io.File

class PodcastDetailScreenSourceTest {
    @Test
    fun episodeRows_doNotExposeQueueActions() {
        val source = File("src/main/java/com/example/mypodcast/ui/detail/PodcastDetailScreen.kt").readText()

        assertFalse(source.contains("\"Play next\""))
        assertFalse(source.contains("\"Add to queue\""))
    }
}
