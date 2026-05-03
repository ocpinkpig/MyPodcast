package com.example.mypodcast.ui.player

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.repository.PlayerRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test

class PlayerViewModelTest {
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
        val viewModel = PlayerViewModel(repository)

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
        val viewModel = PlayerViewModel(repository)

        viewModel.playPause("current")

        assertEquals(null, repository.playedEpisode)
        assertEquals(true, repository.paused)
    }

    @Test
    fun toggleFavorite_favoritesCurrentEpisode() {
        val currentEpisode = episode("current", isFavorite = false)
        val repository = FakePlayerRepository(PlayerState(episode = currentEpisode))
        val viewModel = PlayerViewModel(repository)

        viewModel.toggleFavorite()

        assertEquals("current", repository.favoriteGuid)
        assertEquals(true, repository.favoriteValue)
    }

    @Test
    fun toggleFavorite_unfavoritesCurrentEpisode() {
        val currentEpisode = episode("current", isFavorite = true)
        val repository = FakePlayerRepository(PlayerState(episode = currentEpisode))
        val viewModel = PlayerViewModel(repository)

        viewModel.toggleFavorite()

        assertEquals("current", repository.favoriteGuid)
        assertEquals(false, repository.favoriteValue)
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

private class FakePlayerRepository(initialState: PlayerState) : PlayerRepository {
    override val playerState: StateFlow<PlayerState> = MutableStateFlow(initialState)
    var playedEpisode: Episode? = null
    var paused = false
    var favoriteGuid: String? = null
    var favoriteValue: Boolean? = null

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
    override fun enqueue(episode: Episode) = Unit
    override fun enqueueNext(episode: Episode) = Unit
    override fun removeFromQueue(guid: String) = Unit
    override fun clearQueue() = Unit
    override fun skipToQueueItem(guid: String) = Unit
    override fun moveQueueItem(fromIndex: Int, toIndex: Int) = Unit
    override fun release() = Unit
}
