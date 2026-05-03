package com.example.mypodcast.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.repository.EpisodeRepository
import com.example.mypodcast.domain.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainScreenViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    episodeRepository: EpisodeRepository
) : ViewModel() {
    val playerState: StateFlow<PlayerState> = playerRepository.playerState

    val favoriteEpisodes: StateFlow<List<Episode>> =
        episodeRepository.observeFavoriteEpisodes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historyEpisodes: StateFlow<List<Episode>> =
        episodeRepository.observeHistoryEpisodes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun togglePlayPause() {
        if (playerState.value.isPlaying) playerRepository.pause()
        else playerRepository.resume()
    }

    fun removeFromQueue(guid: String) = playerRepository.removeFromQueue(guid)
    fun moveQueueItem(fromIndex: Int, toIndex: Int) =
        playerRepository.moveQueueItem(fromIndex, toIndex)
    fun clearQueue() = playerRepository.clearQueue()
    fun skipToQueueItem(guid: String) = playerRepository.skipToQueueItem(guid)
    fun playEpisode(episode: Episode) = playerRepository.play(episode)
    fun prepareEpisode(episode: Episode) = playerRepository.prepare(episode)
    fun unfavorite(guid: String) = playerRepository.setFavorite(guid, false)
}
