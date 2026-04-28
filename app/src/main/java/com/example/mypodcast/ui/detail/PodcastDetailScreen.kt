package com.example.mypodcast.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.remember
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
import androidx.core.text.HtmlCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.mypodcast.ui.components.LoadingIndicator
import com.example.mypodcast.ui.components.PodcastEpisodeRow
import com.example.mypodcast.ui.player.MiniPlayerBar

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
        bottomBar = { MiniPlayerBar(onOpenPlayer = onEpisodePlay) },
        // Let the hero artwork extend behind the status bar — the hero
        // applies its own statusBarsPadding to the action row.
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        when {
            state.isLoading && state.podcast == null -> {
                LoadingIndicator(Modifier.padding(padding))
            }
            state.error != null && state.podcast == null -> {
                Text(
                    text = "Error: ${state.error}",
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                )
            }
            else -> {
                val podcast = state.podcast ?: return@Scaffold
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    item {
                        PodcastHero(
                            artworkUrl = podcast.artworkUrl,
                            title = podcast.title,
                            artistName = podcast.artistName,
                            description = podcast.description,
                            isSubscribed = state.isSubscribed,
                            onBack = onBack,
                            onSubscribeToggle = { viewModel.toggleSubscription(podcastId) }
                        )
                    }
                    item {
                        SubscribeBar(
                            episodeCount = state.episodes.size,
                            isSubscribed = state.isSubscribed,
                            onSubscribeToggle = { viewModel.toggleSubscription(podcastId) }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
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
}

@Composable
private fun PodcastHero(
    artworkUrl: String?,
    title: String,
    artistName: String,
    description: String?,
    isSubscribed: Boolean,
    onBack: () -> Unit,
    onSubscribeToggle: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

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
                    IconButton(onClick = { /* filter placeholder */ }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Filter",
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

                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    pageSpacing = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) { page ->
                    when (page) {
                        0 -> AsyncImage(
                            model = artworkUrl,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )
                        else -> ChannelInfoCard(
                            title = title,
                            artistName = artistName,
                            description = description
                        )
                    }
                }

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
                    selected = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SubscribeBar(
    episodeCount: Int,
    isSubscribed: Boolean,
    onSubscribeToggle: () -> Unit
) {
    val purple = Color(0xFF8B5CF6)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (episodeCount == 1) "1 episode" else "$episodeCount episodes",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = onSubscribeToggle,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSubscribed) purple.copy(alpha = 0.15f) else purple,
                contentColor = if (isSubscribed) purple else Color.White
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
        ) {
            if (isSubscribed) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(6.dp))
            }
            Text(
                text = if (isSubscribed) "Subscribed" else "Subscribe",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ChannelInfoCard(
    title: String,
    artistName: String,
    description: String?
) {
    val cleaned = remember(description) {
        description
            ?.takeIf { it.isNotBlank() }
            ?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim() }
            ?.takeIf { it.isNotEmpty() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (artistName.isNotBlank()) {
                Text(
                    text = artistName,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = cleaned ?: "No description available.",
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodySmall
            )
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
