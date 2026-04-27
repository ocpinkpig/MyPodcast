package com.example.mypodcast.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.ui.components.EpisodeListItem
import com.example.mypodcast.ui.components.PodcastCard

@Composable
fun LibraryScreen(
    onPodcastClick: (Long) -> Unit,
    onEpisodePlay: (String) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Episode?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = state.selectedTab.ordinal) {
            Tab(
                selected = state.selectedTab == LibraryTab.SUBSCRIPTIONS,
                onClick = { viewModel.selectTab(LibraryTab.SUBSCRIPTIONS) },
                text = { Text("Subscriptions") }
            )
            Tab(
                selected = state.selectedTab == LibraryTab.DOWNLOADS,
                onClick = { viewModel.selectTab(LibraryTab.DOWNLOADS) },
                text = { Text("Downloads") }
            )
        }

        when (state.selectedTab) {
            LibraryTab.SUBSCRIPTIONS -> {
                if (state.subscriptions.isEmpty()) {
                    Text(
                        "No subscriptions yet. Find podcasts to follow!",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.subscriptions, key = { it.id }) { podcast ->
                            PodcastCard(
                                podcast = podcast,
                                onClick = { onPodcastClick(podcast.id) }
                            )
                        }
                    }
                }
            }
            LibraryTab.DOWNLOADS -> {
                if (state.downloads.isEmpty()) {
                    Text(
                        "No downloaded episodes yet.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.downloads, key = { it.guid }) { episode ->
                            EpisodeListItem(
                                episode = episode,
                                onPlayClick = {
                                    viewModel.playEpisode(episode)
                                    onEpisodePlay(episode.guid)
                                },
                                showDeleteIcon = true,
                                onDeleteDownloadClick = { pendingDelete = episode }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { episode ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete download?") },
            text = {
                Text("\"${episode.title}\" will be removed from your device. You can re-download it later.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDownload(episode)
                        pendingDelete = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}
