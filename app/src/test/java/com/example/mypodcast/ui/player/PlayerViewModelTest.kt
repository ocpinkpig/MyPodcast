package com.example.mypodcast.ui.player

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.model.SavedMoment
import com.example.mypodcast.domain.repository.PlayerRepository
import com.example.mypodcast.domain.repository.SavedMomentRepository
import com.example.mypodcast.data.transcription.FakeTranscriptionLibraryRepository
import com.example.mypodcast.domain.transcription.LiveTranscription
import com.example.mypodcast.domain.transcription.TranscriptionMonitor
import com.example.mypodcast.domain.usecase.episode.GetTranscriptUseCase
import com.example.mypodcast.ui.library.MainDispatcherRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun playPause_playsPreviewEpisodeWithoutPausingCurrentEpisode() {
        val currentEpisode = episode("current")
        val previewEpisode = episode("preview")
        val repository = FakePlayerRepository(
            PlayerState(
                episode = currentEpisode,
                previewEpisode = previewEpisode,
                isPlaying = true
            )
        )
        val viewModel = PlayerViewModel(
            repository,
            GetTranscriptUseCase(FakeTranscriptRepository()),
            PlayerViewFakeSavedMomentRepository(),
            FakeTranscriptionMonitor(),
            FakeTranscriptionLibraryRepository()
        )

        viewModel.playPause("preview")

        assertEquals(previewEpisode, repository.playedEpisode)
        assertFalse(repository.paused)
    }

    @Test
    fun playPause_ignoresStalePreviewForCurrentEpisodeScreen() {
        val currentEpisode = episode("current")
        val previewEpisode = episode("preview")
        val repository = FakePlayerRepository(
            PlayerState(
                episode = currentEpisode,
                previewEpisode = previewEpisode,
                isPlaying = true
            )
        )
        val viewModel = PlayerViewModel(
            repository,
            GetTranscriptUseCase(FakeTranscriptRepository()),
            PlayerViewFakeSavedMomentRepository(),
            FakeTranscriptionMonitor(),
            FakeTranscriptionLibraryRepository()
        )

        viewModel.playPause("current")

        assertEquals(null, repository.playedEpisode)
        assertEquals(true, repository.paused)
    }

    @Test
    fun toggleFavorite_favoritesCurrentEpisode() {
        val currentEpisode = episode("current", isFavorite = false)
        val repository = FakePlayerRepository(PlayerState(episode = currentEpisode))
        val viewModel = PlayerViewModel(
            repository,
            GetTranscriptUseCase(FakeTranscriptRepository()),
            PlayerViewFakeSavedMomentRepository(),
            FakeTranscriptionMonitor(),
            FakeTranscriptionLibraryRepository()
        )

        viewModel.toggleFavorite()

        assertEquals("current", repository.favoriteGuid)
        assertEquals(true, repository.favoriteValue)
    }

    @Test
    fun toggleFavorite_unfavoritesCurrentEpisode() {
        val currentEpisode = episode("current", isFavorite = true)
        val repository = FakePlayerRepository(PlayerState(episode = currentEpisode))
        val viewModel = PlayerViewModel(
            repository,
            GetTranscriptUseCase(FakeTranscriptRepository()),
            PlayerViewFakeSavedMomentRepository(),
            FakeTranscriptionMonitor(),
            FakeTranscriptionLibraryRepository()
        )

        viewModel.toggleFavorite()

        assertEquals("current", repository.favoriteGuid)
        assertEquals(false, repository.favoriteValue)
    }

    @Test
    fun addToQueue_enqueuesCurrentEpisode() {
        val currentEpisode = episode("current")
        val repository = FakePlayerRepository(PlayerState(episode = currentEpisode))
        val viewModel = PlayerViewModel(
            repository,
            GetTranscriptUseCase(FakeTranscriptRepository()),
            PlayerViewFakeSavedMomentRepository(),
            FakeTranscriptionMonitor(),
            FakeTranscriptionLibraryRepository()
        )

        viewModel.addToQueue()

        assertEquals(currentEpisode, repository.enqueuedEpisode)
    }

    @Test
    fun saveMoment_savesCurrentEpisodePositionWithReplayWindow() = runTest {
        val currentEpisode = episode("current").copy(durationSeconds = 300)
        val repository = FakePlayerRepository(
            PlayerState(
                episode = currentEpisode,
                positionMs = 20_000L,
                durationMs = 300_000L
            )
        )
        val savedMoments = PlayerViewFakeSavedMomentRepository()
        val viewModel = PlayerViewModel(
            repository,
            GetTranscriptUseCase(FakeTranscriptRepository()),
            savedMoments,
            FakeTranscriptionMonitor(),
            FakeTranscriptionLibraryRepository()
        )

        viewModel.saveMoment()
        advanceUntilIdle()

        assertEquals(currentEpisode, savedMoments.savedEpisode)
        assertEquals(20_000L, savedMoments.savedPositionMs)
        assertEquals(5_000L, savedMoments.savedClipStartMs)
        assertEquals(65_000L, savedMoments.savedClipEndMs)
    }

    private fun episode(guid: String) = Episode(
        guid = guid,
        podcastId = 1L,
        title = "Episode $guid",
        description = null,
        audioUrl = "https://example.com/$guid.mp3",
        artworkUrl = null,
        publishedAt = 0L,
        durationSeconds = 60,
        fileSizeBytes = 1_024L
    )

    private fun episode(guid: String, isFavorite: Boolean) =
        episode(guid).copy(isFavorite = isFavorite)
}

internal class FakeTranscriptionMonitor(
    initial: LiveTranscription? = null
) : TranscriptionMonitor {
    val state = MutableStateFlow(initial)
    var refreshCount = 0
        private set

    override val live: StateFlow<LiveTranscription?> get() = state
    override fun refresh() { refreshCount++ }
}

internal class FakePlayerRepository(initialState: PlayerState) : PlayerRepository {
    override val playerState: StateFlow<PlayerState> = MutableStateFlow(initialState)
    var playedEpisode: Episode? = null
    var paused = false
    var favoriteGuid: String? = null
    var favoriteValue: Boolean? = null
    var enqueuedEpisode: Episode? = null

    override fun play(episode: Episode) {
        playedEpisode = episode
    }

    override fun prepare(episode: Episode) = Unit

    override fun pause() {
        paused = true
    }

    override fun resume() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun skipForward(seconds: Int) = Unit
    override fun skipBack(seconds: Int) = Unit
    override fun setPlaybackSpeed(speed: Float) = Unit
    override fun setSleepTimer(minutes: Int) = Unit
    override fun cancelSleepTimer() = Unit
    override fun setFavorite(guid: String, isFavorite: Boolean) {
        favoriteGuid = guid
        favoriteValue = isFavorite
    }
    override fun enqueue(episode: Episode) {
        enqueuedEpisode = episode
    }
    override fun enqueueNext(episode: Episode) = Unit
    override fun removeFromQueue(guid: String) = Unit
    override fun clearQueue() = Unit
    override fun skipToQueueItem(guid: String) = Unit
    override fun moveQueueItem(fromIndex: Int, toIndex: Int) = Unit
    override fun release() = Unit
}

private class PlayerViewFakeSavedMomentRepository : SavedMomentRepository {
    var savedEpisode: Episode? = null
    var savedPositionMs: Long? = null
    var savedClipStartMs: Long? = null
    var savedClipEndMs: Long? = null

    override fun observeSavedMoments(): Flow<List<SavedMoment>> = flowOf(emptyList())
    override fun observeHasSavedMoments(episodeGuid: String): Flow<Boolean> = flowOf(false)

    override suspend fun saveMoment(
        episode: Episode,
        positionMs: Long,
        clipStartMs: Long,
        clipEndMs: Long,
        transcriptText: String?
    ) {
        savedEpisode = episode
        savedPositionMs = positionMs
        savedClipStartMs = clipStartMs
        savedClipEndMs = clipEndMs
    }

    override suspend fun deleteMoment(id: Long) = Unit
}
