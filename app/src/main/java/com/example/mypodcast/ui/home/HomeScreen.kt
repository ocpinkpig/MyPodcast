package com.example.mypodcast.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mypodcast.ui.components.LoadingIndicator
import com.example.mypodcast.ui.components.PodcastCard

@Composable
fun HomeScreen(
    onPodcastClick: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.isLoading -> LoadingIndicator()
        state.error != null -> Text(
            text = "Failed to load: ${state.error}",
            modifier = Modifier.padding(16.dp)
        )
        else -> HomeContent(state = state, onPodcastClick = onPodcastClick)
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onPodcastClick: (Long) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        state.featuredByCategory.entries.forEachIndexed { index, (category, podcasts) ->
            if (podcasts.isEmpty()) return@forEachIndexed

            item(key = "header_$category") {
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = if (index == 0) 16.dp else 8.dp, bottom = 4.dp)
                )
            }

            item(key = "row_$category") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(podcasts, key = { it.id }) { podcast ->
                        PodcastCard(
                            podcast = podcast,
                            onClick = { onPodcastClick(podcast.id) },
                            modifier = Modifier.width(140.dp)
                        )
                    }
                }
            }
        }
    }
}
