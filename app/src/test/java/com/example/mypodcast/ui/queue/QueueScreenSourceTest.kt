package com.example.mypodcast.ui.queue

import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.io.File

class QueueScreenSourceTest {
    @Test
    fun queueScreen_usesDarkPodcastQueueLayout() {
        val source = File("src/main/java/com/example/mypodcast/ui/queue/QueueScreen.kt").readText()

        assertTrue(source.contains("Color.Black"))
        assertTrue(source.contains("\"QUEUE\""))
        assertTrue(source.contains("\"FAVORITES\""))
        assertTrue(source.contains("\"HISTORY\""))
        assertTrue(source.contains("AsyncImage"))
        assertTrue(source.contains("DragHandle"))
        assertTrue(source.contains("CircleShape"))
        assertTrue(source.contains("formatPublishedDate"))
        assertTrue(source.contains("formatDuration"))
    }

    @Test
    fun queueEpisodeRows_matchPodcastEpisodeRowTypographyScale() {
        val source = File("src/main/java/com/example/mypodcast/ui/queue/QueueScreen.kt").readText()

        assertTrue(source.contains("style = MaterialTheme.typography.bodyMedium"))
        assertTrue(source.contains("style = MaterialTheme.typography.labelSmall"))
        assertTrue(source.contains(".padding(horizontal = 12.dp, vertical = 14.dp)"))
        assertTrue(source.contains(".size(40.dp)"))
        assertTrue(source.contains("BorderStroke(1.5.dp"))
    }

    @Test
    fun queueTopBar_matchesPlayerNavigationShape() {
        val queueSource = File("src/main/java/com/example/mypodcast/ui/queue/QueueScreen.kt").readText()
        val navigationSource = File("src/main/java/com/example/mypodcast/Navigation.kt").readText()

        assertTrue(queueSource.contains("onBack: () -> Unit"))
        assertTrue(queueSource.contains("navigationIcon"))
        assertTrue(queueSource.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue(queueSource.contains("contentDescription = \"Back\""))
        assertTrue(navigationSource.contains("QueueScreen(onBack ="))
    }
}
