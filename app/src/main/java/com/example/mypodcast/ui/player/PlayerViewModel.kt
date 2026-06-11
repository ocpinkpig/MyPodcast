package com.example.mypodcast.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.model.Transcript
import com.example.mypodcast.domain.repository.PlayerRepository
import com.example.mypodcast.domain.repository.SavedMomentRepository
import com.example.mypodcast.domain.transcription.TranscriptionMonitor
import com.example.mypodcast.domain.usecase.episode.GetTranscriptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val getTranscript: GetTranscriptUseCase,
    private val savedMomentRepository: SavedMomentRepository,
    private val transcriptionMonitor: TranscriptionMonitor
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerRepository.playerState

    private val _transcriptState = MutableStateFlow<TranscriptUiState>(TranscriptUiState.Empty)
    val transcriptState: StateFlow<TranscriptUiState> = _transcriptState.asStateFlow()

    private var transcriptJob: Job? = null
    private var loadedTranscriptGuid: String? = null

    /**
     * Loads the transcript for [episode], reusing the result once loaded for the
     * same episode. Episodes without a transcript URL show the on-device
     * generated transcript (possibly partial) and follow the live session while
     * the episode plays.
     */
    fun loadTranscript(episode: Episode?) {
        if (episode == null) return
        if (episode.guid == loadedTranscriptGuid &&
            _transcriptState.value !is TranscriptUiState.Error
        ) return

        loadedTranscriptGuid = episode.guid
        transcriptJob?.cancel()
        _transcriptState.value = TranscriptUiState.Loading

        transcriptJob = viewModelScope.launch {
            if (!episode.transcriptUrl.isNullOrBlank()) {
                _transcriptState.value = getTranscript(episode).fold(
                    onSuccess = { transcript ->
                        if (transcript.cues.isEmpty()) TranscriptUiState.Empty
                        else TranscriptUiState.Loaded(transcript)
                    },
                    onFailure = { TranscriptUiState.Error(it.message ?: "Couldn't load transcript") }
                )
                return@launch
            }

            // No publisher transcript: show what's been generated so far, then
            // follow the live on-device session for this episode.
            val stored = getTranscript(episode).getOrNull()
            _transcriptState.value = stored
                ?.takeIf { it.cues.isNotEmpty() }
                ?.let { TranscriptUiState.Loaded(it) }
                ?: TranscriptUiState.Empty

            transcriptionMonitor.live
                .filter { it != null && it.episodeGuid == episode.guid }
                .collect { live ->
                    if (live!!.cues.isNotEmpty()) {
                        _transcriptState.value = TranscriptUiState.Loaded(
                            Transcript(
                                cues = live.cues,
                                isSynced = true,
                                transcribedUpToMs = live.transcribedUpToMs
                                    .takeIf { !live.isComplete }
                            )
                        )
                    }
                }
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

    fun observeHasSavedMoments(episodeGuid: String): Flow<Boolean> =
        savedMomentRepository.observeHasSavedMoments(episodeGuid)

    fun saveMoment(episodeGuid: String? = null) {
        val state = playerRepository.playerState.value
        val episode = selectedEpisode(episodeGuid) ?: return
        val positionMs = if (state.previewEpisode?.guid == episode.guid) {
            episode.playbackPosition
        } else {
            state.positionMs
        }
        val durationMs = state.durationMs.takeIf { it > 0L }
            ?: episode.durationSeconds.takeIf { it > 0 }?.times(1000L)
            ?: 0L
        val clipStartMs = (positionMs - MOMENT_LEAD_IN_MS).coerceAtLeast(0L)
        val clipEndMs = if (durationMs > 0L) {
            (positionMs + MOMENT_FOLLOW_UP_MS).coerceAtMost(durationMs)
        } else {
            positionMs + MOMENT_FOLLOW_UP_MS
        }
        val transcriptText = (_transcriptState.value as? TranscriptUiState.Loaded)
            ?.transcript
            ?.excerptAt(positionMs)

        viewModelScope.launch {
            savedMomentRepository.saveMoment(
                episode = episode,
                positionMs = positionMs,
                clipStartMs = clipStartMs,
                clipEndMs = clipEndMs,
                transcriptText = transcriptText
            )
        }
    }

    private fun selectedEpisode(episodeGuid: String?): Episode? {
        val state = playerRepository.playerState.value
        return state.previewEpisode
            ?.takeIf { episodeGuid == null || it.guid == episodeGuid }
            ?: state.episode
                ?.takeIf { episodeGuid == null || it.guid == episodeGuid }
    }

    private fun Transcript.excerptAt(positionMs: Long): String? {
        if (!isSynced) return null
        val cue = cues.firstOrNull { cue ->
            positionMs >= cue.startMs && (cue.endMs <= cue.startMs || positionMs <= cue.endMs)
        } ?: cues.minByOrNull { cue -> kotlin.math.abs(cue.startMs - positionMs) }

        return cue?.text
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.take(MAX_TRANSCRIPT_EXCERPT_CHARS)
    }

    private companion object {
        const val MOMENT_LEAD_IN_MS = 15_000L
        const val MOMENT_FOLLOW_UP_MS = 45_000L
        const val MAX_TRANSCRIPT_EXCERPT_CHARS = 240
    }
}
