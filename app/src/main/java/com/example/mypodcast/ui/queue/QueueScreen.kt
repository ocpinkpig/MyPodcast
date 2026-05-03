package com.example.mypodcast.ui.queue

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.ui.main.MainScreenViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val QueuePurple = Color(0xFF9B66C6)
private val QueueDivider = Color.White.copy(alpha = 0.12f)
private val QueueSecondary = Color.White.copy(alpha = 0.68f)
private val QueueTertiary = Color.White.copy(alpha = 0.46f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onBack: () -> Unit,
    onEpisodeClick: (String) -> Unit = {},
    viewModel: MainScreenViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val current = playerState.episode
    val queue = playerState.queue
    val favorites by viewModel.favoriteEpisodes.collectAsStateWithLifecycle()
    val history by viewModel.historyEpisodes.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(QueueTab.QUEUE) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Queue",
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
                    IconButton(onClick = { /* Search coming later. */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search queue")
                    }
                    IconButton(onClick = { /* Lock queue editing coming later. */ }) {
                        Icon(Icons.Default.LockOpen, contentDescription = "Queue unlocked")
                    }
                    IconButton(onClick = { /* More queue options coming later. */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More queue options")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    actionIconContentColor = QueueSecondary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
        ) {
            QueueTabs(selected = selectedTab, onSelect = { selectedTab = it })

            when (selectedTab) {
                QueueTab.QUEUE -> {
                    if (current == null && queue.isEmpty()) {
                        EmptyQueue(Modifier.weight(1f))
                        return@Column
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        current?.let { episode ->
                            QueueEpisodeRow(
                                episode = episode,
                                status = "Playing",
                                onPlay = { viewModel.togglePlayPause() },
                                onClick = { onEpisodeClick(episode.guid) }
                            )
                            HorizontalDivider(color = QueueDivider)
                        }
                        ReorderableQueueList(
                            queue = queue,
                            onMove = viewModel::moveQueueItem,
                            onPlay = { guid -> viewModel.skipToQueueItem(guid) },
                            onRemove = { guid -> viewModel.removeFromQueue(guid) },
                            onEpisodeClick = { episode ->
                                viewModel.prepareEpisode(episode)
                                onEpisodeClick(episode.guid)
                            },
                            onClearQueue = viewModel::clearQueue
                        )
                    }
                }
                QueueTab.FAVORITES -> {
                    if (favorites.isEmpty()) {
                        EmptyFavorites(Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            items(favorites, key = { "fav-${it.guid}" }) { episode ->
                                QueueEpisodeRow(
                                    episode = episode,
                                    status = null,
                                    onPlay = { viewModel.playEpisode(episode) },
                                    onClick = {
                                        viewModel.prepareEpisode(episode)
                                        onEpisodeClick(episode.guid)
                                    },
                                    showDragHandle = false
                                )
                                HorizontalDivider(color = QueueDivider)
                            }
                        }
                    }
                }
                QueueTab.HISTORY -> {
                    if (history.isEmpty()) {
                        EmptyHistory(Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            items(history, key = { "hist-${it.guid}" }) { episode ->
                                QueueEpisodeRow(
                                    episode = episode,
                                    status = null,
                                    onPlay = { viewModel.playEpisode(episode) },
                                    onClick = {
                                        viewModel.prepareEpisode(episode)
                                        onEpisodeClick(episode.guid)
                                    },
                                    showDragHandle = false
                                )
                                HorizontalDivider(color = QueueDivider)
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class QueueTab { QUEUE, FAVORITES, HISTORY }

@Composable
private fun QueueTabs(selected: QueueTab, onSelect: (QueueTab) -> Unit) {
    val tabs = listOf(
        "QUEUE" to QueueTab.QUEUE,
        "FAVORITES" to QueueTab.FAVORITES,
        "HISTORY" to QueueTab.HISTORY
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            tabs.forEach { (label, tab) ->
                val isSelected = tab == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) QueuePurple else QueuePurple.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .height(3.dp)
                            .fillMaxWidth()
                            .background(if (isSelected) QueuePurple else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(color = QueueDivider)
    }
}

@Composable
private fun ReorderableQueueList(
    queue: List<Episode>,
    onMove: (Int, Int) -> Unit,
    onPlay: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onClearQueue: () -> Unit
) {
    val lazyListState = rememberLazyListState()
    var localQueue by remember { mutableStateOf(queue) }
    LaunchedEffect(queue) { localQueue = queue }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val mutable = localQueue.toMutableList()
        if (from.index !in mutable.indices || to.index !in mutable.indices) return@rememberReorderableLazyListState
        mutable.add(to.index, mutable.removeAt(from.index))
        localQueue = mutable
        onMove(from.index, to.index)
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        items(localQueue, key = { it.guid }) { episode ->
            ReorderableItem(reorderableState, key = episode.guid) { _ ->
                SwipeableQueueRow(
                    episode = episode,
                    onPlay = { onPlay(episode.guid) },
                    onRemove = { onRemove(episode.guid) },
                    onClick = { onEpisodeClick(episode) },
                    dragHandleModifier = Modifier.draggableHandle()
                )
                HorizontalDivider(color = QueueDivider)
            }
        }

        if (localQueue.isNotEmpty()) {
            item(key = "clear-queue") {
                TextButton(
                    onClick = onClearQueue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text("Clear queue", color = QueuePurple, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableQueueRow(
    episode: Episode,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    dragHandleModifier: Modifier = Modifier
) {
    var pendingRemoval by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                pendingRemoval = true
            }
            false
        }
    )

    LaunchedEffect(pendingRemoval) {
        if (!pendingRemoval) {
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        backgroundContent = { SwipeRemoveBackground() }
    ) {
        QueueEpisodeRow(
            episode = episode,
            status = null,
            onPlay = onPlay,
            onClick = onClick,
            modifier = Modifier.background(Color.Black),
            dragHandleModifier = dragHandleModifier
        )
    }

    if (pendingRemoval) {
        AlertDialog(
            onDismissRequest = { pendingRemoval = false },
            containerColor = Color(0xFF1F1F1F),
            titleContentColor = Color.White,
            textContentColor = QueueSecondary,
            title = { Text("Remove from queue?") },
            text = {
                Text("\"${episode.title}\" will be removed from your queue.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoval = false
                    onRemove()
                }) {
                    Text("Remove", color = QueuePurple, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = false }) {
                    Text("Cancel", color = QueueSecondary)
                }
            }
        )
    }
}

@Composable
private fun SwipeRemoveBackground() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(Color(0xFFB00020))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Remove",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun QueueEpisodeRow(
    episode: Episode,
    status: String?,
    onPlay: () -> Unit,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = true,
    dragHandleModifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showDragHandle) {
            DragHandle(modifier = dragHandleModifier)
            Spacer(Modifier.width(8.dp))
        }
        Artwork(url = episode.artworkUrl)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = episode.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = metadataText(episode, status),
                color = QueueSecondary,
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
                    .border(BorderStroke(1.5.dp, QueuePurple.copy(alpha = 0.7f)), CircleShape)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = if (status == "Playing") "Resume playing" else "Play episode",
                    tint = QueuePurple,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = formatDuration(episode.durationSeconds).ifBlank { " " },
                color = QueueSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(4) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(QueueTertiary)
                )
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(QueueTertiary)
                )
            }
        }
    }
}

@Composable
private fun Artwork(url: String?) {
    Surface(
        modifier = Modifier.size(64.dp),
        shape = RoundedCornerShape(4.dp),
        color = Color.White.copy(alpha = 0.08f)
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
                    text = "P",
                    color = QueuePurple,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmptyQueue(modifier: Modifier = Modifier) {
    EmptyMessage(
        modifier = modifier,
        title = "Your queue is empty",
        subtitle = "Episodes you add will appear here."
    )
}

@Composable
private fun EmptyFavorites(modifier: Modifier = Modifier) {
    EmptyMessage(
        modifier = modifier,
        title = "No favorite episodes",
        subtitle = "Tap the heart on an episode to favorite it."
    )
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    EmptyMessage(
        modifier = modifier,
        title = "No history yet",
        subtitle = "Episodes you finish will appear here."
    )
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
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                color = QueueSecondary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

private fun metadataText(episode: Episode, status: String?): String {
    val date = formatPublishedDate(episode.publishedAt)
    val played = if (episode.isPlayed) "Played" else status

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
