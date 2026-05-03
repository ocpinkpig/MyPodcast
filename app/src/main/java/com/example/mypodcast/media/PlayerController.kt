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
import com.example.mypodcast.data.local.dao.QueueDao
import com.example.mypodcast.data.local.entity.QueueItemEntity
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.repository.EpisodeRepository
import dagger.Lazy
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
    private val sleepTimerManager: SleepTimerManager,
    private val episodeRepository: Lazy<EpisodeRepository>,
    private val queueDao: Lazy<QueueDao>
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
    private var ticksSinceLastSave = 0

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _playerState.update { it.copy(
                    isBuffering = state == Player.STATE_BUFFERING,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                )}
                if (state == Player.STATE_READY) startPositionUpdates()
                if (state == Player.STATE_ENDED) {
                    stopPositionUpdates()
                    persistEnded()
                    playNextInQueue()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                    persistCurrent()
                }
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

        scope.launch(Dispatchers.IO) {
            hydrateQueue()
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
        // Persist outgoing episode's position before swapping.
        persistCurrent()

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
            val ep = currentEpisode
            if (ep != null && ep.isPlayed) {
                currentEpisode = ep.copy(isPlayed = false, playbackPosition = 0L)
                _playerState.update { it.copy(episode = currentEpisode, positionMs = 0L, error = null) }
                persistProgress(ep.guid, positionMs = 0L, isPlayed = false)
            } else {
                _playerState.update { it.copy(positionMs = 0L, error = null) }
            }
        }
        exoPlayer.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _playerState.update { it.copy(positionMs = positionMs) }
        // A non-zero seek on a played episode means the user is re-listening:
        // clear isPlayed so the checkmark goes away.
        val episode = currentEpisode ?: return
        val clearPlayed = episode.isPlayed && positionMs > 0L
        if (clearPlayed) {
            currentEpisode = episode.copy(isPlayed = false, playbackPosition = positionMs)
            _playerState.update { it.copy(episode = currentEpisode) }
        }
        persistProgress(episode.guid, positionMs, isPlayed = if (clearPlayed) false else episode.isPlayed)
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


    fun enqueue(episode: Episode) {
        val queue = _playerState.value.queue.toMutableList()
        queue.removeAll { it.guid == episode.guid }
        queue.add(episode)
        _playerState.update { it.copy(queue = queue) }
        persistQueue(queue)
    }

    fun enqueueNext(episode: Episode) {
        val queue = _playerState.value.queue.toMutableList()
        queue.removeAll { it.guid == episode.guid }
        queue.add(0, episode)
        _playerState.update { it.copy(queue = queue) }
        persistQueue(queue)
    }

    fun removeFromQueue(guid: String) {
        val updated = _playerState.value.queue.filterNot { it.guid == guid }
        _playerState.update { it.copy(queue = updated) }
        persistQueue(updated)
    }

    fun clearQueue() {
        _playerState.update { it.copy(queue = emptyList()) }
        scope.launch(Dispatchers.IO) { queueDao.get().clear() }
    }

    fun skipToQueueItem(guid: String) {
        val queue = _playerState.value.queue.toMutableList()
        val index = queue.indexOfFirst { it.guid == guid }
        if (index < 0) return
        val episode = queue.removeAt(index)
        _playerState.update { it.copy(queue = queue) }
        persistQueue(queue)
        loadEpisode(episode, autoPlay = true)
    }


    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val queue = _playerState.value.queue.toMutableList()
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) return
        val item = queue.removeAt(fromIndex)
        queue.add(toIndex, item)
        _playerState.update { it.copy(queue = queue) }
        persistQueue(queue)
    }

    fun setFavorite(guid: String, isFavorite: Boolean) {
        val updatedCurrent = currentEpisode
            ?.takeIf { it.guid == guid }
            ?.copy(isFavorite = isFavorite)
        if (updatedCurrent != null) currentEpisode = updatedCurrent

        _playerState.update { state ->
            state.copy(
                episode = state.episode?.let { if (it.guid == guid) it.copy(isFavorite = isFavorite) else it },
                previewEpisode = state.previewEpisode?.let { if (it.guid == guid) it.copy(isFavorite = isFavorite) else it }
            )
        }

        scope.launch(Dispatchers.IO) {
            episodeRepository.get().updateFavorite(guid, isFavorite)
        }
    }

    fun release() {
        // Snapshot state synchronously before releasing ExoPlayer so the
        // IO coroutine reads a valid position even after exoPlayer.release().
        val episodeSnapshot = currentEpisode
        val positionSnapshot = exoPlayer.currentPosition
        positionJob?.cancel()
        exoPlayer.release()
        if (episodeSnapshot != null && !episodeSnapshot.isPlayed && positionSnapshot >= MIN_PERSIST_POSITION_MS) {
            CoroutineScope(Dispatchers.IO).launch {
                episodeRepository.get().updateProgress(episodeSnapshot.guid, positionSnapshot, episodeSnapshot.isPlayed)
            }
        }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        ticksSinceLastSave = 0
        positionJob = scope.launch {
            while (true) {
                _playerState.update { it.copy(
                    positionMs = exoPlayer.currentPosition,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                )}
                ticksSinceLastSave++
                if (ticksSinceLastSave >= SAVE_EVERY_N_TICKS) {
                    ticksSinceLastSave = 0
                    persistCurrent()
                }
                delay(500L)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        ticksSinceLastSave = 0
    }

    private fun startPlaybackService() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, PlaybackService::class.java)
        )
    }

    private fun persistCurrent() {
        val episode = currentEpisode ?: return
        if (episode.isPlayed) return  // persistEnded() already wrote the final state
        val positionMs = exoPlayer.currentPosition
        if (positionMs < MIN_PERSIST_POSITION_MS) return
        persistProgress(episode.guid, positionMs, episode.isPlayed)
    }

    private fun persistEnded() {
        val episode = currentEpisode ?: return
        currentEpisode = episode.copy(playbackPosition = 0L, isPlayed = true)
        _playerState.update { it.copy(episode = currentEpisode, positionMs = 0L) }
        persistProgress(episode.guid, positionMs = 0L, isPlayed = true)
    }

    private fun playNextInQueue() {
        val queue = _playerState.value.queue.toMutableList()
        val next = queue.firstOrNull() ?: return
        queue.removeAt(0)
        _playerState.update { it.copy(queue = queue) }
        persistQueue(queue)
        loadEpisode(next, autoPlay = true)
    }

    private suspend fun hydrateQueue() {
        val ordered = queueDao.get().getQueueItemsOrdered()
        val episodes = ordered.mapNotNull { item -> episodeRepository.get().getEpisode(item.episodeGuid) }
        _playerState.update { it.copy(queue = episodes) }
        if (episodes.size != ordered.size) {
            persistQueue(episodes)
        }
    }

    private fun persistQueue(queue: List<Episode>) {
        scope.launch(Dispatchers.IO) {
            queueDao.get().replaceOrdered(queue.map { it.guid })
        }
    }

    private fun persistProgress(guid: String, positionMs: Long, isPlayed: Boolean) {
        scope.launch(Dispatchers.IO) {
            episodeRepository.get().updateProgress(guid, positionMs, isPlayed)
        }
    }

    companion object {
        // Save every 10 * 500 ms = 5 s while playing.
        private const val SAVE_EVERY_N_TICKS = 10
        // Avoid stomping a fresh seek-to-0 with a stale earlier value.
        private const val MIN_PERSIST_POSITION_MS = 1_000L
    }
}
