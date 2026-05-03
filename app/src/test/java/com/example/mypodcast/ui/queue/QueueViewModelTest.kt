package com.example.mypodcast.ui.queue

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.repository.EpisodeRepository
import com.example.mypodcast.domain.repository.PlayerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueViewModelTest {
    @Test
    fun moveQueueItem_delegates() = runTest {
        val playerRepo = FakePlayerRepository()
        val vm = QueueViewModel(playerRepo, FakeEpisodeRepository())

        vm.moveQueueItem(0, 1)

        assertEquals(0, playerRepo.from)
        assertEquals(1, playerRepo.to)
    }
}

private class FakePlayerRepository : PlayerRepository {
    override val playerState = MutableStateFlow(PlayerState())
    var from = -1
    var to = -1
    override fun play(episode: Episode) = Unit
    override fun prepare(episode: Episode) = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun skipForward(seconds: Int) = Unit
    override fun skipBack(seconds: Int) = Unit
    override fun setPlaybackSpeed(speed: Float) = Unit
    override fun setSleepTimer(minutes: Int) = Unit
    override fun cancelSleepTimer() = Unit
    override fun setFavorite(guid: String, isFavorite: Boolean) = Unit
    override fun enqueue(episode: Episode) = Unit
    override fun enqueueNext(episode: Episode) = Unit
    override fun removeFromQueue(guid: String) = Unit
    override fun clearQueue() = Unit
    override fun skipToQueueItem(guid: String) = Unit
    override fun moveQueueItem(fromIndex: Int, toIndex: Int) { from = fromIndex; to = toIndex }
    override fun release() = Unit
}

private class FakeEpisodeRepository : EpisodeRepository {
    override suspend fun fetchEpisodesForPodcast(podcastId: Long, feedUrl: String): List<Episode> = emptyList()
    override fun observeEpisodesForPodcast(podcastId: Long): Flow<List<Episode>> = flowOf(emptyList())
    override suspend fun getEpisode(guid: String): Episode? = null
    override suspend fun updateProgress(guid: String, positionMs: Long, isPlayed: Boolean) = Unit
    override suspend fun updateFavorite(guid: String, isFavorite: Boolean) = Unit
    override fun observeFavorites(): Flow<List<Episode>> = flowOf(emptyList())
    override fun observePlayedHistory(): Flow<List<Episode>> = flowOf(emptyList())
}
