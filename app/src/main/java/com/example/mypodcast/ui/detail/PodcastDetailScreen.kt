package com.example.mypodcast.ui.detail

import android.content.Intent
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.LocalOverscrollFactory
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.text.HtmlCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import com.example.mypodcast.ui.components.BlurTransformation
import com.example.mypodcast.ui.components.LoadingIndicator
import com.example.mypodcast.ui.components.PodcastEpisodeRow
import com.example.mypodcast.ui.main.MainScreenViewModel
import com.example.mypodcast.ui.player.BackdropRecorder
import com.example.mypodcast.ui.player.LocalMiniPlayerInset
import com.example.mypodcast.ui.player.MiniPlayerBar

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

    val playerViewModel: MainScreenViewModel = hiltViewModel()
    // Subscribe ONLY to the narrow "is an episode loaded" flag — the full
    // playerState ticks every 500ms during playback and would recompose
    // this entire scaffold (and the wrapping BackdropRecorder, which then
    // re-records the LazyColumn into a GraphicsLayer) twice per second.
    val hasEpisode by playerViewModel.hasEpisode.collectAsStateWithLifecycle()
    val miniPlayerInset = if (hasEpisode) 64.dp else 0.dp

    CompositionLocalProvider(LocalMiniPlayerInset provides miniPlayerInset) {
    Scaffold(
        // Let the hero artwork extend behind the status bar — the hero
        // applies its own statusBarsPadding to the action row.
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        BackdropRecorder(
            enabled = hasEpisode,
            modifier = Modifier.fillMaxSize()
        ) {
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
                val podcast = state.podcast
                if (podcast != null) {
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    state = pullState,
                    modifier = Modifier.padding(padding),
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullState,
                            isRefreshing = state.isRefreshing,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .zIndex(1f)
                        )
                    }
                ) {
                    CompositionLocalProvider(LocalOverscrollFactory provides null) {
                    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LocalMiniPlayerInset.current)
                    ) {
                        item(key = "hero", contentType = "hero") {
                            PodcastHero(
                                artworkUrl = podcast.artworkUrl,
                                title = podcast.title,
                                artistName = podcast.artistName,
                                description = podcast.description,
                                feedUrl = podcast.feedUrl,
                                isSubscribed = state.isSubscribed,
                                onBack = onBack,
                                onSubscribeToggle = { viewModel.toggleSubscription(podcastId) }
                            )
                        }
                        item(key = "subscribe-bar", contentType = "subscribe-bar") {
                            SubscribeBar(
                                episodeCount = state.episodes.size,
                                isSubscribed = state.isSubscribed,
                                onSubscribeToggle = { viewModel.toggleSubscription(podcastId) }
                            )
                            HorizontalDivider(color = dividerColor)
                        }
                        items(
                            state.episodes,
                            key = { it.guid },
                            contentType = { "episode" }
                        ) { episode ->
                            // Stable per-episode callbacks so PodcastEpisodeRow can
                            // skip recomposition when the screen state changes (e.g.,
                            // download map updates) but this row's inputs are equal.
                            val onPlayClick = remember(episode) {
                                {
                                    viewModel.playEpisode(episode)
                                    onEpisodePlay(episode.guid)
                                }
                            }
                            val onDownloadClick = remember(episode) {
                                { viewModel.downloadEpisode(episode) }
                            }
                            val onCancelDownloadClick = remember(episode.guid) {
                                { viewModel.cancelDownload(episode.guid) }
                            }
                            val onDeleteDownloadClick = remember(episode) {
                                { viewModel.deleteDownload(episode) }
                            }
                            PodcastEpisodeRow(
                                episode = episode,
                                isDownloaded = episode.guid in state.downloadedGuids,
                                downloadState = state.downloadStates[episode.guid],
                                onPlayClick = onPlayClick,
                                onDownloadClick = onDownloadClick,
                                onCancelDownloadClick = onCancelDownloadClick,
                                onDeleteDownloadClick = onDeleteDownloadClick,
                                bottomDividerColor = dividerColor
                            )
                        }
                    }
                    }
                }
                }
            }
        }
        }
            MiniPlayerBar(
                onOpenPlayer = onEpisodePlay,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
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
    feedUrl: String,
    isSubscribed: Boolean,
    onBack: () -> Unit,
    onSubscribeToggle: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val context = LocalContext.current

    // Cache the blurred-backdrop ImageRequest so it isn't rebuilt every recompose.
    val backdropRequest = remember(artworkUrl, context) {
        ImageRequest.Builder(context)
            .data(artworkUrl)
            .transformations(BlurTransformation())
            .build()
    }
    // Folds the 0.18 black tint into the AsyncImage via colorFilter instead of
    // a separate overlay Box — one fewer composable + draw command per frame.
    val backdropTint = remember {
        ColorFilter.tint(Color.Black.copy(alpha = 0.18f), BlendMode.SrcAtop)
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = backdropRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = backdropTint,
            modifier = Modifier.matchParentSize()
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
                    IconButton(onClick = {
                        val summary = description
                            ?.let {
                                HtmlCompat.fromHtml(
                                    it,
                                    HtmlCompat.FROM_HTML_MODE_COMPACT
                                ).toString()
                            }
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                        val shareText = buildString {
                            append(title)
                            append(" — ")
                            append(feedUrl)
                            if (summary != null) {
                                append("\n\n")
                                append(summary)
                            }
                        }
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, title)
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share podcast",
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
