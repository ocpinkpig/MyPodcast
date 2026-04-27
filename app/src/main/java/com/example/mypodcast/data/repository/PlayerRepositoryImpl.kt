package com.example.mypodcast.data.repository

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.repository.PlayerRepository
import com.example.mypodcast.media.PlayerController
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class PlayerRepositoryImpl @Inject constructor(
    private val playerController: PlayerController
) : PlayerRepository {

    override val playerState: StateFlow<PlayerState> = playerController.playerState

    override fun play(episode: Episode) = playerController.playEpisode(episode)
    override fun pause() = playerController.pause()
    override fun resume() = playerController.resume()
    override fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    override fun skipForward(seconds: Int) = playerController.skipForward(seconds)
    override fun skipBack(seconds: Int) = playerController.skipBack(seconds)
    override fun setPlaybackSpeed(speed: Float) = playerController.setPlaybackSpeed(speed)
    override fun setSleepTimer(minutes: Int) = playerController.setSleepTimer(minutes)
    override fun cancelSleepTimer() = playerController.cancelSleepTimer()
    override fun release() = playerController.release()
}
