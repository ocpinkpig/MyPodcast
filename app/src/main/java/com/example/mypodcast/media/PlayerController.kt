package com.example.mypodcast.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sleepTimerManager: SleepTimerManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            true
        )
        .build()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState

    private var currentEpisode: Episode? = null
    private var positionJob: Job? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _playerState.update { it.copy(
                    isBuffering = state == Player.STATE_BUFFERING,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                )}
                if (state == Player.STATE_READY) startPositionUpdates()
                if (state == Player.STATE_ENDED) stopPositionUpdates()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startPositionUpdates() else stopPositionUpdates()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _playerState.update { it.copy(error = error.message) }
            }
        })

        scope.launch {
            sleepTimerManager.remainingMs.collect { remaining ->
                _playerState.update { it.copy(sleepTimerRemainingMs = remaining) }
            }
        }
    }

    fun playEpisode(episode: Episode) {
        loadEpisode(episode, autoPlay = true)
    }

    fun prepareEpisode(episode: Episode) {
        // No-op when this episode is already loaded — preserves current
        // play/pause state (and position) when the user re-opens the same
        // episode from a list row.
        if (currentEpisode?.guid == episode.guid) {
            _playerState.update { it.copy(episode = episode, previewEpisode = null, error = null) }
            return
        }
        _playerState.update { it.copy(previewEpisode = episode, error = null) }
    }

    private fun loadEpisode(episode: Episode, autoPlay: Boolean) {
        currentEpisode = episode
        val mediaItem = MediaItem.Builder()
            .setUri(episode.audioUrl)
            .setMediaId(episode.guid)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setArtworkUri(episode.artworkUrl?.let { Uri.parse(it) })
                    .build()
            )
            .build()
        exoPlayer.setMediaItem(mediaItem)
        if (episode.playbackPosition > 0L) exoPlayer.seekTo(episode.playbackPosition)
        exoPlayer.prepare()
        if (autoPlay) {
            startPlaybackService()
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
        _playerState.update {
            it.copy(
                episode = episode,
                previewEpisode = null,
                positionMs = episode.playbackPosition,
                durationMs = episode.durationSeconds.takeIf { seconds -> seconds > 0 }?.times(1000L) ?: 0L,
                error = null
            )
        }
    }

    fun pause() = exoPlayer.pause()
    fun resume() {
        startPlaybackService()
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            exoPlayer.seekTo(0L)
            _playerState.update { it.copy(positionMs = 0L, error = null) }
        }
        exoPlayer.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _playerState.update { it.copy(positionMs = positionMs) }
    }

    fun skipForward(seconds: Int = 15) = seekTo((exoPlayer.currentPosition + seconds * 1000L).coerceAtMost(exoPlayer.duration))
    fun skipBack(seconds: Int = 15) = seekTo((exoPlayer.currentPosition - seconds * 1000L).coerceAtLeast(0L))

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
        _playerState.update { it.copy(speed = speed) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerManager.start(minutes) { exoPlayer.pause() }
    }

    fun cancelSleepTimer() = sleepTimerManager.cancel()

    fun release() {
        positionJob?.cancel()
        exoPlayer.release()
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (true) {
                _playerState.update { it.copy(
                    positionMs = exoPlayer.currentPosition,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                )}
                delay(500L)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
    }

    private fun startPlaybackService() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, PlaybackService::class.java)
        )
    }
}
