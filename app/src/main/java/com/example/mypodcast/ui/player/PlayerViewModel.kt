package com.example.mypodcast.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.repository.PlayerRepository
import com.example.mypodcast.domain.usecase.episode.GetTranscriptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val getTranscript: GetTranscriptUseCase
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerRepository.playerState

    private val _transcriptState = MutableStateFlow<TranscriptUiState>(TranscriptUiState.Empty)
    val transcriptState: StateFlow<TranscriptUiState> = _transcriptState.asStateFlow()

    private var transcriptJob: Job? = null
    private var loadedTranscriptGuid: String? = null

    /**
     * Loads the transcript for [episode], reusing the result once loaded for the
     * same episode. Episodes without a transcript URL resolve to [TranscriptUiState.Empty]
     * — the surface a future on-device transcriber can fill.
     */
    fun loadTranscript(episode: Episode?) {
        if (episode == null) return
        if (episode.guid == loadedTranscriptGuid &&
            _transcriptState.value !is TranscriptUiState.Error
        ) return

        loadedTranscriptGuid = episode.guid
        transcriptJob?.cancel()

        if (episode.transcriptUrl.isNullOrBlank()) {
            _transcriptState.value = TranscriptUiState.Empty
            return
        }

        _transcriptState.value = TranscriptUiState.Loading
        transcriptJob = viewModelScope.launch {
            _transcriptState.value = getTranscript(episode).fold(
                onSuccess = { transcript ->
                    if (transcript.cues.isEmpty()) TranscriptUiState.Empty
                    else TranscriptUiState.Loaded(transcript)
                },
                onFailure = { TranscriptUiState.Error(it.message ?: "Couldn't load transcript") }
            )
        }
    }

    /** Forces a reload, used by the transcript page's retry action. */
    fun retryTranscript(episode: Episode?) {
        loadedTranscriptGuid = null
        loadTranscript(episode)
    }

    fun playPause(episodeGuid: String? = null) {
        val state = playerRepository.playerState.value
        val previewEpisode = state.previewEpisode
        if (
            previewEpisode != null &&
            previewEpisode.guid != state.episode?.guid &&
            (episodeGuid == null || previewEpisode.guid == episodeGuid)
        ) {
            playerRepository.play(previewEpisode)
            return
        }

        if (state.isPlaying) playerRepository.pause() else playerRepository.resume()
    }

    fun seekTo(positionMs: Long) = playerRepository.seekTo(positionMs)
    fun skipForward() = playerRepository.skipForward(seconds = 30)
    fun skipBack() = playerRepository.skipBack(seconds = 30)
    fun setSpeed(speed: Float) = playerRepository.setPlaybackSpeed(speed)
    fun setSleepTimer(minutes: Int) = playerRepository.setSleepTimer(minutes)
    fun cancelSleepTimer() = playerRepository.cancelSleepTimer()

    fun toggleFavorite(episodeGuid: String? = null) {
        val episode = selectedEpisode(episodeGuid)
            ?: return

        playerRepository.setFavorite(episode.guid, !episode.isFavorite)
    }

    fun addToQueue(episodeGuid: String? = null) {
        val episode = selectedEpisode(episodeGuid) ?: return

        playerRepository.enqueue(episode)
    }

    private fun selectedEpisode(episodeGuid: String?): Episode? {
        val state = playerRepository.playerState.value
        return state.previewEpisode
            ?.takeIf { episodeGuid == null || it.guid == episodeGuid }
            ?: state.episode
                ?.takeIf { episodeGuid == null || it.guid == episodeGuid }
    }
}
