package com.example.mypodcast.ui.main

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.repository.PlayerRepository
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun togglePlayPause_resumesWhenPaused() = runTest {
    val repository = FakePlayerRepository(PlayerState(isPlaying = false))
    val viewModel = MainScreenViewModel(repository)

    viewModel.togglePlayPause()

    assertTrue(repository.resumed)
  }

  @Test
  fun togglePlayPause_pausesWhenPlaying() = runTest {
    val repository = FakePlayerRepository(PlayerState(isPlaying = true))
    val viewModel = MainScreenViewModel(repository)

    viewModel.togglePlayPause()

    assertTrue(repository.paused)
    assertFalse(repository.resumed)
  }
}

private class FakePlayerRepository(initialState: PlayerState) : PlayerRepository {
  override val playerState: StateFlow<PlayerState> = MutableStateFlow(initialState)
  var paused = false
  var resumed = false

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
  override fun release() = Unit
}
