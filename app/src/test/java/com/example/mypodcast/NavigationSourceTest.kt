package com.example.mypodcast

import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.io.File

class NavigationSourceTest {
    @Test
    fun navDisplay_disablesDefaultCrossfadeTransitions() {
        val source = File("src/main/java/com/example/mypodcast/Navigation.kt").readText()

        assertTrue(source.contains("transitionSpec = { immediateSceneTransition() }"))
        assertTrue(source.contains("popTransitionSpec = { immediateSceneTransition() }"))
        assertTrue(source.contains("EnterTransition.None"))
        assertTrue(source.contains("ExitTransition.None"))
    }
}
