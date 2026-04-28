package com.example.mypodcast.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import com.example.mypodcast.HomeNavKey
import com.example.mypodcast.LibraryNavKey
import com.example.mypodcast.PlayerNavKey
import com.example.mypodcast.SearchNavKey
import com.example.mypodcast.ui.player.MiniPlayerBar

@Composable
fun MainScreen(
    backStack: MutableList<NavKey>,
    onNavigate: (NavKey) -> Unit,
    content: @Composable () -> Unit
) {
    val currentRoot = backStack.firstOrNull { it is HomeNavKey || it is SearchNavKey || it is LibraryNavKey }

    Scaffold(
        bottomBar = {
            Column {
                MiniPlayerBar(onOpenPlayer = { guid -> onNavigate(PlayerNavKey(guid)) })
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoot is HomeNavKey,
                        onClick = {
                            backStack.clear()
                            backStack.add(HomeNavKey)
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = currentRoot is SearchNavKey,
                        onClick = {
                            backStack.clear()
                            backStack.add(SearchNavKey)
                        },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search") }
                    )
                    NavigationBarItem(
                        selected = currentRoot is LibraryNavKey,
                        onClick = {
                            backStack.clear()
                            backStack.add(LibraryNavKey)
                        },
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                        label = { Text("Library") }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            content()
        }
    }
}
