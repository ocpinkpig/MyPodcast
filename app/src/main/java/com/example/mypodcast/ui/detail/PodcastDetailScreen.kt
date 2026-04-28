package com.example.mypodcast.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.mypodcast.ui.components.LoadingIndicator
import com.example.mypodcast.ui.components.PodcastEpisodeRow

@Composable
fun PodcastDetailScreen(
    podcastId: Long,
    onEpisodePlay: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PodcastDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(podcastId) { viewModel.load(podcastId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.isLoading && state.podcast == null -> {
            Scaffold { padding -> LoadingIndicator(Modifier.padding(padding)) }
        }
        state.error != null && state.podcast == null -> {
            Scaffold { padding ->
                Text(
                    text = "Error: ${state.error}",
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                )
            }
        }
        else -> {
            val podcast = state.podcast ?: return
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    PodcastHero(
                        artworkUrl = podcast.artworkUrl,
                        title = podcast.title,
                        artistName = podcast.artistName,
                        isSubscribed = state.isSubscribed,
                        onBack = onBack,
                        onSubscribeToggle = { viewModel.toggleSubscription(podcastId) }
                    )
                }
                items(state.episodes, key = { it.guid }) { episode ->
                    PodcastEpisodeRow(
                        episode = episode,
                        isDownloaded = episode.guid in state.downloadedGuids,
                        downloadState = state.downloadStates[episode.guid],
                        onPlayClick = {
                            viewModel.playEpisode(episode)
                            onEpisodePlay(episode.guid)
                        },
                        onDownloadClick = { viewModel.downloadEpisode(episode) },
                        onCancelDownloadClick = { viewModel.cancelDownload(episode.guid) },
                        onDeleteDownloadClick = { viewModel.deleteDownload(episode) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PodcastHero(
    artworkUrl: String?,
    title: String,
    artistName: String,
    isSubscribed: Boolean,
    onBack: () -> Unit,
    onSubscribeToggle: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .blur(40.dp)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.18f))
        )

        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onSubscribeToggle) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = if (isSubscribed) "Unsubscribe" else "Subscribe",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { /* share placeholder */ }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { /* more placeholder */ }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                AsyncImage(
                    model = artworkUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(280.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )

                if (artistName.isNotBlank()) {
                    Text(
                        text = artistName,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                PageIndicator(
                    total = 2,
                    selected = 0,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun PageIndicator(total: Int, selected: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(total) { i ->
            val active = i == selected
            Box(
                modifier = Modifier
                    .size(if (active) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) Color.White else Color.White.copy(alpha = 0.5f)
                    )
            )
        }
    }
}
