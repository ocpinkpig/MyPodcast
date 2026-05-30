package com.example.mypodcast.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.mypodcast.domain.model.DownloadState
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.ui.components.EpisodeListItem
import com.example.mypodcast.ui.components.PodcastCard
import com.example.mypodcast.ui.components.PodcastCardGridDefaults
import com.example.mypodcast.ui.player.LocalMiniPlayerInset
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LibraryTabPurple = Color(0xFF9B66C6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onPodcastClick: (Long) -> Unit,
    onBack: () -> Unit = {},
    onEpisodePlay: (String) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Episode?>(null) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val tabs = remember { LibraryTab.entries.toList() }
    val pagerState = rememberPagerState(
        initialPage = state.selectedTab.ordinal,
        pageCount = { tabs.size }
    )

    LaunchedEffect(state.selectedTab) {
        if (pagerState.currentPage != state.selectedTab.ordinal) {
            pagerState.animateScrollToPage(state.selectedTab.ordinal)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> viewModel.selectTab(tabs[page]) }
    }
    LaunchedEffect(state.isSearchActive) {
        if (state.isSearchActive) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    if (state.isSearchActive) {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                            placeholder = {
                                Text(
                                    text = "Search library",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = LibraryTabPurple
                            )
                        )
                    } else {
                        Text(
                            text = "Library",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isSearchActive) {
                        IconButton(onClick = viewModel::closeSearch) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    } else {
                        IconButton(onClick = viewModel::openSearch) {
                            Icon(Icons.Default.Search, contentDescription = "Search library")
                        }
                        IconButton(onClick = { /* More library options coming later. */ }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More library options")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding)
        ) {
            if (state.isSearchActive) {
                LibrarySearchResults(
                    query = state.searchQuery,
                    results = state.searchResults,
                    downloadedGuids = state.downloadedGuids,
                    downloadStates = state.downloadStates,
                    onPodcastClick = onPodcastClick,
                    onEpisodePlay = { episode ->
                        viewModel.playEpisode(episode)
                        onEpisodePlay(episode.guid)
                    },
                    onDownloadEpisode = viewModel::downloadEpisode,
                    onCancelDownload = viewModel::cancelDownload
                )
            } else {
                PrimaryTabRow(
                    selectedTabIndex = state.selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = LibraryTabPurple
                ) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = state.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = { Text(tab.title) },
                            selectedContentColor = LibraryTabPurple,
                            unselectedContentColor = LibraryTabPurple.copy(alpha = 0.72f)
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (tabs[page]) {
                        LibraryTab.SUBSCRIPTIONS -> {
                            val pullState = rememberPullToRefreshState()
                            PullToRefreshBox(
                                isRefreshing = state.isRefreshingSubscriptions,
                                onRefresh = viewModel::refreshSubscriptions,
                                state = pullState,
                                modifier = Modifier.fillMaxSize(),
                                indicator = {
                                    PullToRefreshDefaults.Indicator(
                                        state = pullState,
                                        isRefreshing = state.isRefreshingSubscriptions,
                                        modifier = Modifier.align(Alignment.TopCenter)
                                    )
                                }
                            ) {
                                if (state.subscriptions.isEmpty()) {
                                    Text(
                                        "No subscriptions yet. Find podcasts to follow!",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(PodcastCardGridDefaults.ColumnCount),
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = PodcastCardGridDefaults.ContentPadding,
                                            top = PodcastCardGridDefaults.ContentPadding,
                                            end = PodcastCardGridDefaults.ContentPadding,
                                            bottom = PodcastCardGridDefaults.ContentPadding + LocalMiniPlayerInset.current
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(PodcastCardGridDefaults.Spacing),
                                        verticalArrangement = Arrangement.spacedBy(PodcastCardGridDefaults.Spacing)
                                    ) {
                                        items(state.subscriptions, key = { it.id }) { podcast ->
                                            PodcastCard(
                                                podcast = podcast,
                                                onClick = { onPodcastClick(podcast.id) },
                                                newEpisodeCount = state.newEpisodeCounts[podcast.id] ?: 0
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        LibraryTab.DOWNLOADS -> {
                            if (state.downloads.isEmpty()) {
                                Text(
                                    "No downloaded episodes yet.",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = LocalMiniPlayerInset.current)
                                ) {
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
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
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

@Composable
private fun LibrarySearchResults(
    query: String,
    results: List<LibrarySearchResult>,
    downloadedGuids: Set<String>,
    downloadStates: Map<String, DownloadState>,
    onPodcastClick: (Long) -> Unit,
    onEpisodePlay: (Episode) -> Unit,
    onDownloadEpisode: (Episode) -> Unit,
    onCancelDownload: (String) -> Unit
) {
    when {
        query.isBlank() -> EmptyMessage(
            modifier = Modifier.fillMaxSize(),
            title = "Search your library",
            subtitle = "Subscriptions and downloaded episodes will appear here."
        )
        results.isEmpty() -> EmptyMessage(
            modifier = Modifier.fillMaxSize(),
            title = "No results",
            subtitle = "Try another podcast or episode title."
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp + LocalMiniPlayerInset.current)
        ) {
            items(results, key = { it.key }) { result ->
                when (result) {
                    is LibrarySearchResult.Subscription -> {
                        LibrarySearchPodcastRow(
                            podcast = result.podcast,
                            onClick = { onPodcastClick(result.podcast.id) }
                        )
                    }
                    is LibrarySearchResult.Download -> {
                        LibrarySearchEpisodeRow(
                            episode = result.episode,
                            isDownloaded = result.episode.guid in downloadedGuids,
                            downloadState = downloadStates[result.episode.guid],
                            onPlay = { onEpisodePlay(result.episode) },
                            onDownload = { onDownloadEpisode(result.episode) },
                            onCancelDownload = { onCancelDownload(result.episode.guid) }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun LibrarySearchPodcastRow(
    podcast: Podcast,
    onClick: () -> Unit
) {
    LibrarySearchRow(
        artworkUrl = podcast.artworkUrl,
        fallbackText = podcast.title.firstOrNull()?.uppercase() ?: "P",
        title = podcast.title,
        metadata = listOf("Subscription", podcast.artistName)
            .filter { it.isNotBlank() }
            .joinToString(" - "),
        duration = "",
        actionContentDescription = "Open podcast",
        actionIcon = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = LibraryTabPurple,
                modifier = Modifier.size(20.dp)
            )
        },
        onAction = onClick,
        onClick = onClick
    )
}

@Composable
private fun LibrarySearchEpisodeRow(
    episode: Episode,
    isDownloaded: Boolean,
    downloadState: DownloadState?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit
) {
    val actionContentDescription: String
    val onAction: () -> Unit
    val actionIcon: @Composable () -> Unit

    when {
        isDownloaded -> {
            actionContentDescription = "Play episode"
            onAction = onPlay
            actionIcon = {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = LibraryTabPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        downloadState is DownloadState.Downloading -> {
            actionContentDescription = "Cancel download"
            onAction = onCancelDownload
            actionIcon = {
                CircularProgressIndicator(
                    progress = { downloadState.progressPercent / 100f },
                    modifier = Modifier.size(34.dp),
                    strokeWidth = 2.dp,
                    color = LibraryTabPurple
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = LibraryTabPurple,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        else -> {
            actionContentDescription = "Download episode"
            onAction = onDownload
            actionIcon = {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    tint = LibraryTabPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    LibrarySearchRow(
        artworkUrl = episode.artworkUrl,
        fallbackText = "P",
        title = episode.title,
        metadata = metadataText(episode),
        duration = formatDuration(episode.durationSeconds).ifBlank { " " },
        actionContentDescription = actionContentDescription,
        actionIcon = actionIcon,
        onAction = onAction,
        onClick = onPlay
    )
}

@Composable
private fun LibrarySearchRow(
    artworkUrl: String?,
    fallbackText: String,
    title: String,
    metadata: String,
    duration: String,
    actionContentDescription: String,
    actionIcon: @Composable () -> Unit,
    onAction: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(url = artworkUrl, fallbackText = fallbackText)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(1.5.dp, LibraryTabPurple.copy(alpha = 0.7f)), CircleShape)
                    .semantics { contentDescription = actionContentDescription }
                    .clickable(onClick = onAction),
                contentAlignment = Alignment.Center
            ) {
                actionIcon()
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = duration,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun Artwork(url: String?, fallbackText: String) {
    Surface(
        modifier = Modifier.size(64.dp),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (url.isNullOrBlank()) {
                Text(
                    text = fallbackText,
                    color = LibraryTabPurple,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(modifier: Modifier, title: String, subtitle: String) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

private fun metadataText(episode: Episode): String {
    val date = formatPublishedDate(episode.publishedAt)
    val played = if (episode.isPlayed) "Played" else null

    return listOfNotNull(date, played)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
}

private fun formatPublishedDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""

    return runCatching {
        val instant = Instant.ofEpochMilli(epochMillis)
        DateTimeFormatter
            .ofPattern("MMM d", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }.getOrDefault("")
}

private fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""

    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60

    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes.coerceAtLeast(1)}min"
    }
}
