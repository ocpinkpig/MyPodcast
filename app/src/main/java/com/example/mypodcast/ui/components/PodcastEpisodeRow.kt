package com.example.mypodcast.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mypodcast.domain.model.DownloadState
import com.example.mypodcast.domain.model.Episode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PodcastEpisodeRow(
    episode: Episode,
    isDownloaded: Boolean,
    downloadState: DownloadState?,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onDeleteDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isNew = remember(episode.publishedAt, episode.playbackPosition, episode.isPlayed) {
        episode.publishedAt > 0 &&
            (System.currentTimeMillis() - episode.publishedAt) < SEVEN_DAYS_MS &&
            episode.playbackPosition < ONE_MINUTE_MS &&
            !episode.isPlayed
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DateColumn(
            epochMs = episode.publishedAt,
            modifier = Modifier.width(52.dp)
        )

        Spacer(Modifier.width(8.dp))

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top
        ) {
            if (isNew) {
                NewBadge()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        ActionColumn(
            isDownloaded = isDownloaded,
            downloadState = downloadState,
            episode = episode,
            onDownloadClick = onDownloadClick,
            onCancelDownloadClick = onCancelDownloadClick,
            onDeleteDownloadClick = onDeleteDownloadClick,
            onPlayClick = onPlayClick
        )
    }
}

@Composable
private fun DateColumn(epochMs: Long, modifier: Modifier = Modifier) {
    val (month, day) = remember(epochMs) {
        if (epochMs > 0) {
            val date = Date(epochMs)
            SimpleDateFormat("MMM", Locale.getDefault()).format(date) to
                SimpleDateFormat("d", Locale.getDefault()).format(date)
        } else {
            "" to ""
        }
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = month,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = day,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NewBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFFF6A00))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "New",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ActionColumn(
    isDownloaded: Boolean,
    downloadState: DownloadState?,
    episode: Episode,
    onDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onDeleteDownloadClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val secondaryLabel = remember(episode.durationSeconds, episode.fileSizeBytes, isDownloaded) {
        if (isDownloaded && episode.fileSizeBytes > 0) {
            formatFileSize(episode.fileSizeBytes)
        } else {
            formatDurationCompact(episode.durationSeconds)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            isDownloaded -> CircleIconButton(
                icon = Icons.Default.PlayArrow,
                contentDescription = "Play downloaded episode",
                onClick = onPlayClick,
                onLongClick = onDeleteDownloadClick,
                tint = MaterialTheme.colorScheme.primary
            )
            downloadState is DownloadState.Downloading -> Box(
                modifier = Modifier.size(40.dp).clickable(onClick = onCancelDownloadClick),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { downloadState.progressPercent / 100f },
                    modifier = Modifier.size(34.dp),
                    strokeWidth = 2.dp
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel download",
                    modifier = Modifier.size(14.dp)
                )
            }
            else -> CircleIconButton(
                icon = Icons.Default.Download,
                contentDescription = "Download episode",
                onClick = onDownloadClick
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = secondaryLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = tint
        )
    }
}

private fun formatDurationCompact(seconds: Int): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}min"
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return "${mb.toInt()} MB"
}

private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
private const val ONE_MINUTE_MS = 60_000L
