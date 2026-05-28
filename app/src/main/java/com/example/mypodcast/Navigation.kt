package com.example.mypodcast

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.WindowInsets
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.mypodcast.ui.detail.PodcastDetailScreen
import com.example.mypodcast.ui.home.HomeScreen
import com.example.mypodcast.ui.library.LibraryScreen
import com.example.mypodcast.ui.main.MainScreen
import com.example.mypodcast.ui.player.PlayerScreen
import com.example.mypodcast.ui.queue.QueueScreen
import com.example.mypodcast.ui.search.SearchScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(HomeNavKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = { immediateSceneTransition() },
        popTransitionSpec = { immediateSceneTransition() },
        entryProvider = entryProvider {
            entry<HomeNavKey> {
                MainScreen(
                    backStack = backStack,
                    onNavigate = { key -> backStack.add(key) }
                ) {
                    HomeScreen(
                        onPodcastClick = { id -> backStack.add(PodcastDetailNavKey(id)) }
                    )
                }
            }
            entry<SearchNavKey> {
                MainScreen(
                    backStack = backStack,
                    onNavigate = { key -> backStack.add(key) }
                ) {
                    SearchScreen(
                        onPodcastClick = { id -> backStack.add(PodcastDetailNavKey(id)) }
                    )
                }
            }
            entry<LibraryNavKey> {
                MainScreen(
                    backStack = backStack,
                    onNavigate = { key -> backStack.add(key) },
                    contentWindowInsets = WindowInsets(0)
                ) {
                    LibraryScreen(
                        onBack = { backStack.navigateBackToHome() },
                        onPodcastClick = { id -> backStack.add(PodcastDetailNavKey(id)) },
                        onEpisodePlay = { guid -> backStack.add(PlayerNavKey(guid)) }
                    )
                }
            }
            entry<QueueNavKey> {
                MainScreen(
                    backStack = backStack,
                    onNavigate = { key -> backStack.add(key) },
                    contentWindowInsets = WindowInsets(0)
                ) {
                    QueueScreen(
                        onBack = { backStack.navigateBackToHome() },
                        onEpisodeClick = { guid -> backStack.add(PlayerNavKey(guid)) }
                    )
                }
            }
            entry<PodcastDetailNavKey> { key ->
                PodcastDetailScreen(
                    podcastId = key.podcastId,
                    onEpisodePlay = { guid -> backStack.add(PlayerNavKey(guid)) },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<PlayerNavKey> { key ->
                PlayerScreen(
                    episodeGuid = key.episodeGuid,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}

private fun immediateSceneTransition(): ContentTransform =
    ContentTransform(
        targetContentEnter = EnterTransition.None,
        initialContentExit = ExitTransition.None,
        sizeTransform = null
    )

fun MutableList<NavKey>.navigateTo(key: NavKey) {
    removeAll { it is HomeNavKey || it is SearchNavKey || it is LibraryNavKey || it is QueueNavKey }
    add(0, key)
}

private fun MutableList<NavKey>.navigateBackToHome() {
    if (size > 1) {
        removeLastOrNull()
    } else {
        clear()
        add(HomeNavKey)
    }
}
