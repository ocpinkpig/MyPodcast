package com.example.mypodcast.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.mypodcast.domain.model.DownloadState
import com.example.mypodcast.domain.model.Episode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EpisodeListItem(
    episode: Episode,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDownloaded: Boolean = false,
    showDeleteIcon: Boolean = false,
    downloadState: DownloadState? = null,
    onDownloadClick: (() -> Unit)? = null,
    onCancelDownloadClick: (() -> Unit)? = null,
    onDeleteDownloadClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        episode.artworkUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (episode.isPlayed) {
                    Icon(
                        Icons.Filled.Done,
                        contentDescription = "Played",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = formatDate(episode.publishedAt) + " · " + formatDuration(episode.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (onDownloadClick != null || isDownloaded || showDeleteIcon) {
            DownloadButton(
                isDownloaded = isDownloaded,
                showDeleteIcon = showDeleteIcon,
                downloadState = downloadState,
                onDownloadClick = onDownloadClick,
                onCancelDownloadClick = onCancelDownloadClick,
                onDeleteDownloadClick = onDeleteDownloadClick
            )
        }

        IconButton(onClick = onPlayClick) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
        }
    }
}

@Composable
private fun DownloadButton(
    isDownloaded: Boolean,
    showDeleteIcon: Boolean,
    downloadState: DownloadState?,
    onDownloadClick: (() -> Unit)?,
    onCancelDownloadClick: (() -> Unit)?,
    onDeleteDownloadClick: (() -> Unit)?
) {
    when {
        showDeleteIcon -> {
            IconButton(onClick = { onDeleteDownloadClick?.invoke() }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete download",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        isDownloaded -> {
            IconButton(onClick = { onDeleteDownloadClick?.invoke() }) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Downloaded — tap to delete",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        downloadState is DownloadState.Downloading -> {
            IconButton(onClick = { onCancelDownloadClick?.invoke() }) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { downloadState.progressPercent / 100f },
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp
                    )
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel download",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        else -> {
            IconButton(onClick = { onDownloadClick?.invoke() }) {
                Icon(Icons.Default.Download, contentDescription = "Download")
            }
        }
    }
}

private fun formatDate(epochMs: Long): String {
    if (epochMs == 0L) return ""
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMs))
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
