package com.example.mypodcast.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mypodcast.data.remote.transcript.cueIndexAt
import com.example.mypodcast.domain.model.TranscriptCue

/**
 * The Player's transcript page. Shows a tap-to-seek, auto-scrolling transcript
 * when timestamps are available, plain selectable text otherwise, and a
 * forward-looking empty state when the episode's feed carries no transcript.
 */
@Composable
internal fun TranscriptPage(
    transcriptState: TranscriptUiState,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            when (transcriptState) {
                is TranscriptUiState.Loading -> CenteredTranscriptStatus {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Loading transcript…", style = MaterialTheme.typography.bodyMedium)
                }

                is TranscriptUiState.Error -> CenteredTranscriptStatus {
                    Text(
                        text = "Couldn't load the transcript.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRetry) { Text("Retry") }
                }

                is TranscriptUiState.Empty -> CenteredTranscriptStatus {
                    Text(
                        text = "No transcript available for this episode",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Transcripts appear here when the podcast provides them. " +
                            "For downloaded episodes, a transcript is generated on this " +
                            "device as you listen.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                is TranscriptUiState.Loaded -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        if (transcriptState.transcript.isSynced) {
                            SyncedTranscript(
                                cues = transcriptState.transcript.cues,
                                positionMs = positionMs,
                                onSeek = onSeek
                            )
                        } else {
                            PlainTranscript(cues = transcriptState.transcript.cues)
                        }
                    }
                    transcriptState.transcript.transcribedUpToMs?.let { upToMs ->
                        Text(
                            text = "Transcribing as you listen · up to ${formatTranscribedUpTo(upToMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }

        CompactPlaybackControls(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            onPlayPause = onPlayPause,
            onSkipBack = onSkipBack,
            onSkipForward = onSkipForward,
            modifier = Modifier.padding(vertical = 10.dp)
        )
    }
}

@Composable
private fun SyncedTranscript(
    cues: List<TranscriptCue>,
    positionMs: Long,
    onSeek: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    // positionMs is a plain value (not Compose State), so key the computation on
    // it directly — the binary search is cheap and only visible rows recompose.
    val currentIndex = remember(cues, positionMs) { cueIndexAt(cues, positionMs) }

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        itemsIndexed(cues, key = { index, _ -> index }) { index, cue ->
            CueRow(
                cue = cue,
                isActive = index == currentIndex,
                onClick = remember(cue.startMs) { { onSeek(cue.startMs) } }
            )
        }
    }
}

@Composable
private fun CueRow(
    cue: TranscriptCue,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        cue.speaker?.let { speaker ->
            Text(
                text = speaker,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = cue.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isActive) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun PlainTranscript(cues: List<TranscriptCue>) {
    val text = remember(cues) { cues.joinToString("\n\n") { it.text } }
    SelectionContainer(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTranscribedUpTo(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun CenteredTranscriptStatus(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            content()
        }
    }
}
