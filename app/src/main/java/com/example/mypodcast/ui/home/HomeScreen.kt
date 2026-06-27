package com.example.mypodcast.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.model.TopShow
import com.example.mypodcast.ui.components.LoadingIndicator
import com.example.mypodcast.ui.components.PodcastCard
import com.example.mypodcast.ui.components.PodcastCardGridDefaults
import com.example.mypodcast.ui.player.LocalMiniPlayerInset

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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LocalMiniPlayerInset.current)
    ) {
        val hasTopShows = state.topShows.isNotEmpty()
        if (hasTopShows) {
            item(key = "top_shows") {
                TopShowsCarousel(
                    topShows = state.topShows,
                    onPodcastClick = onPodcastClick,
                    topPadding = 16.dp
                )
            }
        }

        state.featuredByCategory.entries.forEachIndexed { index, (category, podcasts) ->
            if (podcasts.isEmpty()) return@forEachIndexed

            item(key = "category_$category") {
                PodcastCarousel(
                    title = category,
                    podcasts = podcasts,
                    onPodcastClick = onPodcastClick,
                    topPadding = if (!hasTopShows && index == 0) 16.dp else 8.dp
                )
            }
        }
    }
}

@Composable
private fun TopShowsCarousel(
    topShows: List<TopShow>,
    onPodcastClick: (Long) -> Unit,
    topPadding: Dp
) {
    val playedCountByPodcastId = topShows.associate { it.podcast.id to it.playedEpisodeCount }
    PodcastCarousel(
        title = "Your Top Shows",
        podcasts = topShows.map { it.podcast },
        onPodcastClick = onPodcastClick,
        topPadding = topPadding,
        metadataText = { podcast ->
            formatPlayedEpisodeCount(playedCountByPodcastId[podcast.id] ?: 0)
        }
    )
}

@Composable
private fun PodcastCarousel(
    title: String,
    podcasts: List<Podcast>,
    onPodcastClick: (Long) -> Unit,
    topPadding: Dp,
    metadataText: (Podcast) -> String = { it.artistName }
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = topPadding, bottom = 4.dp)
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val subscriptionGridCardWidth = PodcastCardGridDefaults.cardWidthFor(maxWidth)

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(podcasts, key = { it.id }) { podcast ->
                PodcastCard(
                    podcast = podcast,
                    onClick = { onPodcastClick(podcast.id) },
                    metadataText = metadataText(podcast),
                    modifier = Modifier.width(subscriptionGridCardWidth)
                )
            }
        }
    }
}

internal fun formatPlayedEpisodeCount(count: Int): String =
    if (count == 1) "1 played" else "$count played"
