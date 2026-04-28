package com.example.mypodcast.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mypodcast.ui.main.MainScreenViewModel

/**
 * Reusable mini-player strip that hides itself when no episode is loaded.
 * Pulls player state from [MainScreenViewModel] (which is already responsible
 * for player state + play/pause toggling).
 *
 * Tapping the strip opens the full PlayerScreen via the [onOpenPlayer]
 * callback, which receives the currently-loaded episode's GUID.
 */
@Composable
fun MiniPlayerBar(
    onOpenPlayer: (episodeGuid: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val episode = playerState.episode ?: return

    MiniPlayer(
        state = playerState,
        onTap = { onOpenPlayer(episode.guid) },
        onPlayPauseClick = { viewModel.togglePlayPause() },
        modifier = modifier
    )
}
