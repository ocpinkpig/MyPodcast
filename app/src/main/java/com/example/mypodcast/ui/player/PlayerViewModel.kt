package com.example.mypodcast.ui.player

import androidx.lifecycle.ViewModel
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerRepository.playerState

    fun playPause() {
        val state = playerRepository.playerState.value
        if (state.isPlaying) playerRepository.pause() else playerRepository.resume()
    }

    fun seekTo(positionMs: Long) = playerRepository.seekTo(positionMs)
    fun skipForward() = playerRepository.skipForward(seconds = 30)
    fun skipBack() = playerRepository.skipBack(seconds = 30)
    fun setSpeed(speed: Float) = playerRepository.setPlaybackSpeed(speed)
    fun setSleepTimer(minutes: Int) = playerRepository.setSleepTimer(minutes)
    fun cancelSleepTimer() = playerRepository.cancelSleepTimer()
}
