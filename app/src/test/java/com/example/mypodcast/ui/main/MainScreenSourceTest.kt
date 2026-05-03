package com.example.mypodcast.ui.main

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
}
