package com.example.mypodcast.ui.queue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    viewModel: MainScreenViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val current = playerState.episode
    val queue = playerState.queue

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
            QueueTabs()

            if (current == null && queue.isEmpty()) {
                EmptyQueue(Modifier.weight(1f))
                return@Column
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                current?.let { episode ->
                    item(key = "current-${episode.guid}") {
                        QueueEpisodeRow(
                            episode = episode,
                            status = "Playing",
                            onPlay = { viewModel.togglePlayPause() },
                            onRemove = null
                        )
                        HorizontalDivider(color = QueueDivider)
                    }
                }

                items(queue, key = { it.guid }) { episode ->
                    QueueEpisodeRow(
                        episode = episode,
                        status = null,
                        onPlay = { viewModel.skipToQueueItem(episode.guid) },
                        onRemove = { viewModel.removeFromQueue(episode.guid) }
                    )
                    HorizontalDivider(color = QueueDivider)
                }

                if (queue.isNotEmpty()) {
                    item {
                        TextButton(
                            onClick = viewModel::clearQueue,
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
    }
}

@Composable
private fun QueueTabs() {
    val tabs = listOf("QUEUE", "FAVORITES", "HISTORY")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            tabs.forEachIndexed { index, label ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = label,
                        color = if (index == 0) QueuePurple else QueuePurple.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .height(3.dp)
                            .fillMaxWidth()
                            .background(if (index == 0) QueuePurple else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(color = QueueDivider)
    }
}

@Composable
private fun QueueEpisodeRow(
    episode: Episode,
    status: String?,
    onPlay: () -> Unit,
    onRemove: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DragHandle()
        Spacer(Modifier.width(8.dp))
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
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(40.dp)
                    .border(BorderStroke(1.5.dp, QueuePurple.copy(alpha = 0.7f)), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = if (status == "Playing") "Resume playing" else "Play episode",
                    tint = QueuePurple,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove from queue",
                        tint = QueueTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun DragHandle() {
    Column(
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Your queue is empty",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Episodes you add will appear here.",
                color = QueueSecondary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

private fun metadataText(episode: Episode, status: String?): String {
    val date = formatPublishedDate(episode.publishedAt)
    val duration = formatDuration(episode.durationSeconds)
    val played = if (episode.isPlayed) "Played" else status

    return listOfNotNull(date, played, duration)
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
