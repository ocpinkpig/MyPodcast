package com.example.mypodcast.ui.main

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.repository.EpisodeRepository
import com.example.mypodcast.domain.repository.PlayerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun queueActions_delegateToRepository() = runTest {
    val repository = FakePlayerRepository(PlayerState())
    val viewModel = MainScreenViewModel(repository, FakeEpisodeRepository())

    viewModel.removeFromQueue("g1")
    viewModel.skipToQueueItem("g2")
    viewModel.clearQueue()

    assertTrue(repository.removedGuid == "g1")
    assertTrue(repository.skippedGuid == "g2")
    assertTrue(repository.cleared)
  }

  @Test
  fun togglePlayPause_resumesWhenPaused() = runTest {
    val repository = FakePlayerRepository(PlayerState(isPlaying = false))
    val viewModel = MainScreenViewModel(repository, FakeEpisodeRepository())

    viewModel.togglePlayPause()

    assertTrue(repository.resumed)
  }

  @Test
  fun togglePlayPause_pausesWhenPlaying() = runTest {
    val repository = FakePlayerRepository(PlayerState(isPlaying = true))
    val viewModel = MainScreenViewModel(repository, FakeEpisodeRepository())

    viewModel.togglePlayPause()

    assertTrue(repository.paused)
    assertFalse(repository.resumed)
  }
}

private class FakePlayerRepository(initialState: PlayerState) : PlayerRepository {
  override val playerState: StateFlow<PlayerState> = MutableStateFlow(initialState)
  var paused = false
  var resumed = false
  var removedGuid: String? = null
  var skippedGuid: String? = null
  var cleared = false

  override fun play(episode: Episode) = Unit
  override fun prepare(episode: Episode) = Unit
  override fun pause() {
    paused = true
  }
  override fun resume() {
    resumed = true
  }
  override fun seekTo(positionMs: Long) = Unit
  override fun skipForward(seconds: Int) = Unit
  override fun skipBack(seconds: Int) = Unit
  override fun setPlaybackSpeed(speed: Float) = Unit
  override fun setSleepTimer(minutes: Int) = Unit
  override fun cancelSleepTimer() = Unit
  override fun setFavorite(guid: String, isFavorite: Boolean) = Unit
  override fun enqueue(episode: Episode) = Unit
  override fun enqueueNext(episode: Episode) = Unit
  override fun removeFromQueue(guid: String) { removedGuid = guid }
  override fun clearQueue() { cleared = true }
  override fun skipToQueueItem(guid: String) { skippedGuid = guid }
  override fun moveQueueItem(fromIndex: Int, toIndex: Int) = Unit
  override fun release() = Unit
}

private class FakeEpisodeRepository : EpisodeRepository {
  override suspend fun fetchEpisodesForPodcast(podcastId: Long, feedUrl: String): List<Episode> = emptyList()
  override fun observeEpisodesForPodcast(podcastId: Long): Flow<List<Episode>> = flowOf(emptyList())
  override suspend fun getEpisode(guid: String): Episode? = null
  override suspend fun updateProgress(guid: String, positionMs: Long, isPlayed: Boolean) = Unit
  override suspend fun updateFavorite(guid: String, isFavorite: Boolean) = Unit
  override fun observeFavoriteEpisodes(): Flow<List<Episode>> = flowOf(emptyList())
}
