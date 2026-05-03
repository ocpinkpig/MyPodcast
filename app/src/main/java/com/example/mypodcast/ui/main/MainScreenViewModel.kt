package com.example.mypodcast.ui.main

import androidx.lifecycle.ViewModel
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainScreenViewModel @Inject constructor(
    private val playerRepository: PlayerRepository
) : ViewModel() {
    val playerState: StateFlow<PlayerState> = playerRepository.playerState

    fun togglePlayPause() {
        if (playerState.value.isPlaying) playerRepository.pause()
        else playerRepository.resume()
    }

    fun removeFromQueue(guid: String) = playerRepository.removeFromQueue(guid)
    fun clearQueue() = playerRepository.clearQueue()
    fun skipToQueueItem(guid: String) = playerRepository.skipToQueueItem(guid)
}
