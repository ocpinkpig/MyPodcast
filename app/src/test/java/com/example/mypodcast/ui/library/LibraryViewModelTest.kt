package com.example.mypodcast.ui.library

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.repository.EpisodeRepository
import com.example.mypodcast.domain.repository.LibraryRepository
import com.example.mypodcast.domain.repository.PlayerRepository
import com.example.mypodcast.domain.usecase.library.GetLibraryUseCase
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun refreshSubscriptions_badgesPodcastWithNewEpisodeCount() = runTest {
        val podcast = podcast(id = 1L)
        val libraryRepository = FakeLibraryRepository(listOf(podcast))
        val episodeRepository = FakeEpisodeRepository(
            existingEpisodes = mapOf(1L to listOf(episode("existing", 1L))),
            fetchedEpisodes = mapOf(
                1L to listOf(
                    episode("new-a", 1L),
                    episode("new-b", 1L),
                    episode("existing", 1L)
                )
            )
        )
        val viewModel = LibraryViewModel(
            getLibrary = GetLibraryUseCase(libraryRepository),
            libraryRepository = libraryRepository,
            episodeRepository = episodeRepository,
            playerRepository = FakePlayerRepository()
        )
        advanceUntilIdle()

        viewModel.refreshSubscriptions()
        advanceUntilIdle()

        assertEquals(mapOf(1L to 2), viewModel.uiState.value.newEpisodeCounts)
        assertEquals(false, viewModel.uiState.value.isRefreshingSubscriptions)
    }

    private fun podcast(id: Long) = Podcast(
        id = id,
        title = "Podcast $id",
        artworkUrl = "https://example.com/$id.jpg",
        artistName = "Artist",
        feedUrl = "https://example.com/$id.xml",
        description = null,
        genres = emptyList(),
        episodeCount = 0
    )

    private fun episode(guid: String, podcastId: Long) = Episode(
        guid = guid,
        podcastId = podcastId,
        title = "Episode $guid",
        description = null,
        audioUrl = "https://example.com/$guid.mp3",
        artworkUrl = null,
        publishedAt = 0L,
        durationSeconds = 60,
        fileSizeBytes = 1_024L
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeLibraryRepository(
    subscriptions: List<Podcast>
) : LibraryRepository {
    private val subscriptionsFlow = MutableStateFlow(subscriptions)

    override suspend fun subscribe(podcastId: Long) = Unit
    override suspend fun unsubscribe(podcastId: Long) = Unit
    override fun observeSubscriptions(): Flow<List<Podcast>> = subscriptionsFlow
    override fun observeIsSubscribed(podcastId: Long): Flow<Boolean> = flowOf(false)
    override fun observeDownloadedEpisodes(): Flow<List<Episode>> = flowOf(emptyList())
    override fun observeIsDownloaded(episodeGuid: String): Flow<Boolean> = flowOf(false)
    override suspend fun saveDownload(
        episodeGuid: String,
        podcastId: Long,
        localPath: String,
        sizeBytes: Long
    ) = Unit
    override suspend fun deleteDownload(episodeGuid: String) = Unit
    override suspend fun cleanupOrphanedFiles() = Unit
}

private class FakeEpisodeRepository(
    private val existingEpisodes: Map<Long, List<Episode>>,
    private val fetchedEpisodes: Map<Long, List<Episode>>
) : EpisodeRepository {
    override suspend fun fetchEpisodesForPodcast(podcastId: Long, feedUrl: String): List<Episode> =
        fetchedEpisodes[podcastId].orEmpty()

    override fun observeEpisodesForPodcast(podcastId: Long): Flow<List<Episode>> =
        flowOf(existingEpisodes[podcastId].orEmpty())

    override suspend fun getEpisode(guid: String): Episode? = null
    override suspend fun updateProgress(guid: String, positionMs: Long, isPlayed: Boolean) = Unit
    override suspend fun updateFavorite(guid: String, isFavorite: Boolean) = Unit
    override fun observeFavoriteEpisodes(): Flow<List<Episode>> = flowOf(emptyList())
    override fun observeHistoryEpisodes(): Flow<List<Episode>> = flowOf(emptyList())
    override suspend fun touchLastPlayed(guid: String, ts: Long) = Unit
}

private class FakePlayerRepository : PlayerRepository {
    override val playerState: StateFlow<com.example.mypodcast.domain.model.PlayerState> =
        MutableStateFlow(com.example.mypodcast.domain.model.PlayerState())

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
    override fun moveQueueItem(fromIndex: Int, toIndex: Int) = Unit
    override fun release() = Unit
}
