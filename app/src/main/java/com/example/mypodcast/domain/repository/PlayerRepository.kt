package com.example.mypodcast.domain.repository

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState
import kotlinx.coroutines.flow.StateFlow

interface PlayerRepository {
    val playerState: StateFlow<PlayerState>
    fun play(episode: Episode)
    fun prepare(episode: Episode)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun skipForward(seconds: Int = 15)
    fun skipBack(seconds: Int = 15)
    fun setPlaybackSpeed(speed: Float)
    fun setSleepTimer(minutes: Int)
    fun cancelSleepTimer()
    fun release()
}
