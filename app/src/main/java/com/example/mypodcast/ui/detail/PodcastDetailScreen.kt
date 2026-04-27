package com.example.mypodcast.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.mypodcast.ui.components.EpisodeListItem
import com.example.mypodcast.ui.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDetailScreen(
    podcastId: Long,
    onEpisodePlay: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PodcastDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(podcastId) { viewModel.load(podcastId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.podcast?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading && state.podcast == null -> LoadingIndicator(Modifier.padding(padding))
            state.error != null && state.podcast == null -> Text(
                text = "Error: ${state.error}",
                modifier = Modifier.padding(padding).padding(16.dp)
            )
            else -> {
                val podcast = state.podcast ?: return@Scaffold
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = podcast.artworkUrl,
                                contentDescription = podcast.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(podcast.title, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                podcast.artistName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.toggleSubscription(podcastId) },
                                colors = if (state.isSubscribed)
                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                else ButtonDefaults.buttonColors()
                            ) {
                                Icon(
                                    imageVector = if (state.isSubscribed) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Text(if (state.isSubscribed) "Subscribed" else "Subscribe")
                            }
                            podcast.description?.let { desc ->
                                Spacer(Modifier.height(12.dp))
                                Text(desc, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${state.episodes.size} episodes",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        HorizontalDivider()
                    }
                    items(state.episodes, key = { it.guid }) { episode ->
                        EpisodeListItem(
                            episode = episode,
                            onPlayClick = {
                                viewModel.playEpisode(episode)
                                onEpisodePlay(episode.guid)
                            },
                            isDownloaded = episode.guid in state.downloadedGuids,
                            downloadState = state.downloadStates[episode.guid],
                            onDownloadClick = { viewModel.downloadEpisode(episode) },
                            onDeleteDownloadClick = { viewModel.deleteDownload(episode) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
