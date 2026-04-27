package com.example.mypodcast.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mypodcast.ui.components.EpisodeListItem
import com.example.mypodcast.ui.components.PodcastCard

@Composable
fun LibraryScreen(
    onPodcastClick: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.subscriptions, key = { it.id }) { podcast ->
                            PodcastCard(
                                podcast = podcast,
                                onClick = { onPodcastClick(podcast.id) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            HorizontalDivider()
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
                            EpisodeListItem(episode = episode, onPlayClick = {})
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
