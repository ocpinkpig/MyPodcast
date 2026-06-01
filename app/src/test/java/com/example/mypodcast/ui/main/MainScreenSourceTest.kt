package com.example.mypodcast.ui.main

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.io.File

class MainScreenSourceTest {
    @Test
    fun rootScaffold_usesConfigurableInsetsSoOnlyNestedTopBarsOptOut() {
        val source = File("src/main/java/com/example/mypodcast/ui/main/MainScreen.kt").readText()
        val navigationSource = File("src/main/java/com/example/mypodcast/Navigation.kt").readText()

        assertTrue(source.contains("contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets"))
        assertTrue(source.contains("contentWindowInsets = contentWindowInsets"))
        assertTrue(navigationSource.contains("contentWindowInsets = WindowInsets(0)"))
    }

    @Test
    fun libraryNav_usesDistinctShelfIconInsteadOfMusicQueueMotif() {
        val source = File("src/main/java/com/example/mypodcast/ui/main/MainScreen.kt").readText()
        val iconSource = File("src/main/java/com/example/mypodcast/ui/main/LibraryShelfIcon.kt").readText()

        assertTrue(source.contains("icon = LibraryShelfIcon"))
        assertTrue(source.contains("iconOffsetY = (-1.5).dp"))
        assertTrue(iconSource.contains("name = \"LibraryShelf\""))
        assertFalse(source.contains("LibraryMusic"))
    }
}
