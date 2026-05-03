package com.example.mypodcast.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.example.mypodcast.HomeNavKey
import com.example.mypodcast.LibraryNavKey
import com.example.mypodcast.PlayerNavKey
import com.example.mypodcast.QueueNavKey
import com.example.mypodcast.SearchNavKey
import com.example.mypodcast.ui.player.MiniPlayerBar

@Composable
fun MainScreen(
    backStack: MutableList<NavKey>,
    onNavigate: (NavKey) -> Unit,
    content: @Composable () -> Unit
) {
    val currentRoot = backStack.firstOrNull { it is HomeNavKey || it is SearchNavKey || it is LibraryNavKey || it is QueueNavKey }

    Scaffold(
        bottomBar = {
            Column {
                MiniPlayerBar(onOpenPlayer = { guid -> onNavigate(PlayerNavKey(guid)) }, onOpenQueue = { onNavigate(QueueNavKey) })
                CompactBottomNavigationBar(
                    items = listOf(
                        CompactBottomNavItem(
                            icon = Icons.Default.Home,
                            contentDescription = "Home",
                            selected = currentRoot is HomeNavKey,
                            onClick = {
                                backStack.clear()
                                backStack.add(HomeNavKey)
                            }
                        ),
                        CompactBottomNavItem(
                            icon = Icons.Default.Search,
                            contentDescription = "Search",
                            selected = currentRoot is SearchNavKey,
                            onClick = {
                                backStack.clear()
                                backStack.add(SearchNavKey)
                            }
                        ),
                        CompactBottomNavItem(
                            icon = Icons.Default.LibraryMusic,
                            contentDescription = "Library",
                            selected = currentRoot is LibraryNavKey,
                            onClick = {
                                backStack.clear()
                                backStack.add(LibraryNavKey)
                            }
                        ),
                        CompactBottomNavItem(
                            icon = Icons.Default.QueueMusic,
                            contentDescription = "Queue",
                            selected = currentRoot is QueueNavKey,
                            onClick = {
                                backStack.clear()
                                backStack.add(QueueNavKey)
                            }
                        )
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            content()
        }
    }
}

private data class CompactBottomNavItem(
    val icon: ImageVector,
    val contentDescription: String,
    val selected: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun CompactBottomNavigationBar(items: List<CompactBottomNavItem>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .selectable(
                                selected = item.selected,
                                role = Role.Tab,
                                onClick = item.onClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.contentDescription,
                            modifier = Modifier.size(22.dp),
                            tint = if (item.selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
