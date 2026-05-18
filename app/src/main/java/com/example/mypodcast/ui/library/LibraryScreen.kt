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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.ui.components.EpisodeListItem
import com.example.mypodcast.ui.components.PodcastCard
import com.example.mypodcast.ui.components.PodcastCardGridDefaults

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

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Library search coming later. */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search library")
                    }
                    IconButton(onClick = { /* More library options coming later. */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More library options")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White.copy(alpha = 0.68f)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = state.selectedTab.ordinal,
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
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(PodcastCardGridDefaults.ColumnCount),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(PodcastCardGridDefaults.ContentPadding),
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
