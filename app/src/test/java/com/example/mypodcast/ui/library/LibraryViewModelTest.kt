package com.example.mypodcast.ui.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.repository.EpisodeRepository
import com.example.mypodcast.domain.repository.LibraryRepository
import com.example.mypodcast.domain.repository.PlayerRepository
import com.example.mypodcast.domain.usecase.episode.DownloadEpisodeUseCase
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
import org.junit.runner.RunWith
import org.junit.runner.Description
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import okhttp3.OkHttpClient

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun newEpisodeCounts_areExposedFromRepositoryFlow() = runTest {
        val podcast = podcast(id = 1L)
        val libraryRepository = FakeLibraryRepository(listOf(podcast))
        val episodeRepository = FakeEpisodeRepository(
            existingEpisodes = mapOf(1L to listOf(episode("existing", 1L))),
            fetchedEpisodes = emptyMap(),
            newEpisodeCounts = mapOf(1L to 2)
        )
        val viewModel = LibraryViewModel(
            getLibrary = GetLibraryUseCase(libraryRepository),
            libraryRepository = libraryRepository,
            episodeRepository = episodeRepository,
            downloadEpisodeUseCase = downloadEpisodeUseCase(libraryRepository),
            playerRepository = FakePlayerRepository()
        )
        advanceUntilIdle()

        assertEquals(mapOf(1L to 2), viewModel.uiState.value.newEpisodeCounts)
    }

    @Test
    fun searchResults_includeMatchingSubscriptionsAndDownloads() = runTest {
        val podcast = podcast(id = 1L).copy(title = "Daily Tech", artistName = "Studio")
        val download = episode("daily-download", 2L).copy(title = "Daily Download")
        val libraryRepository = FakeLibraryRepository(
            subscriptions = listOf(podcast),
            downloads = listOf(download)
        )
        val viewModel = LibraryViewModel(
            getLibrary = GetLibraryUseCase(libraryRepository),
            libraryRepository = libraryRepository,
            episodeRepository = FakeEpisodeRepository(
                existingEpisodes = emptyMap(),
                fetchedEpisodes = emptyMap()
            ),
            downloadEpisodeUseCase = downloadEpisodeUseCase(libraryRepository),
            playerRepository = FakePlayerRepository()
        )
        advanceUntilIdle()

        viewModel.openSearch()
        viewModel.onSearchQueryChange("daily")

        assertEquals(
            listOf(
                LibrarySearchResult.Subscription(podcast),
                LibrarySearchResult.Download(download)
            ),
            viewModel.uiState.value.searchResults
        )
    }

    @Test
    fun closeSearch_clearsQueryAndResults() = runTest {
        val podcast = podcast(id = 1L).copy(title = "Daily Tech")
        val libraryRepository = FakeLibraryRepository(listOf(podcast))
        val viewModel = LibraryViewModel(
            getLibrary = GetLibraryUseCase(libraryRepository),
            libraryRepository = libraryRepository,
            episodeRepository = FakeEpisodeRepository(
                existingEpisodes = emptyMap(),
                fetchedEpisodes = emptyMap()
            ),
            downloadEpisodeUseCase = downloadEpisodeUseCase(libraryRepository),
            playerRepository = FakePlayerRepository()
        )
        advanceUntilIdle()

        viewModel.openSearch()
        viewModel.onSearchQueryChange("daily")
        viewModel.closeSearch()

        assertEquals(false, viewModel.uiState.value.isSearchActive)
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals(emptyList<LibrarySearchResult>(), viewModel.uiState.value.searchResults)
    }

    @Test
    fun searchResults_includeNonDownloadedEpisodesFromSubscribedPodcasts() = runTest {
        val podcast = podcast(id = 1L).copy(title = "Daily Tech")
        val episode = episode("ai-news", 1L).copy(title = "AI News Roundup")
        val libraryRepository = FakeLibraryRepository(subscriptions = listOf(podcast))
        val viewModel = LibraryViewModel(
            getLibrary = GetLibraryUseCase(libraryRepository),
            libraryRepository = libraryRepository,
            episodeRepository = FakeEpisodeRepository(
                existingEpisodes = mapOf(1L to listOf(episode)),
                fetchedEpisodes = emptyMap()
            ),
            downloadEpisodeUseCase = downloadEpisodeUseCase(libraryRepository),
            playerRepository = FakePlayerRepository()
        )
        advanceUntilIdle()

        viewModel.openSearch()
        viewModel.onSearchQueryChange("roundup")

        assertEquals(
            listOf(LibrarySearchResult.Download(episode)),
            viewModel.uiState.value.searchResults
        )
        assertEquals(emptySet<String>(), viewModel.uiState.value.downloadedGuids)
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

    private fun downloadEpisodeUseCase(libraryRepository: LibraryRepository): DownloadEpisodeUseCase =
        DownloadEpisodeUseCase(
            context = ApplicationProvider.getApplicationContext<Context>(),
            okHttpClient = OkHttpClient(),
            libraryRepository = libraryRepository
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
    subscriptions: List<Podcast>,
    downloads: List<Episode> = emptyList()
) : LibraryRepository {
    private val subscriptionsFlow = MutableStateFlow(subscriptions)
    private val downloadsFlow = MutableStateFlow(downloads)

    override suspend fun subscribe(podcastId: Long) = Unit
    override suspend fun unsubscribe(podcastId: Long) = Unit
    override fun observeSubscriptions(): Flow<List<Podcast>> = subscriptionsFlow
    override fun observeIsSubscribed(podcastId: Long): Flow<Boolean> = flowOf(false)
    override fun observeDownloadedEpisodes(): Flow<List<Episode>> = downloadsFlow
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
    private val fetchedEpisodes: Map<Long, List<Episode>>,
    private val newEpisodeCounts: Map<Long, Int> = emptyMap()
) : EpisodeRepository {
    override suspend fun fetchEpisodesForPodcast(podcastId: Long, feedUrl: String): List<Episode> =
        fetchedEpisodes[podcastId].orEmpty()

    override fun observeEpisodesForPodcast(podcastId: Long): Flow<List<Episode>> =
        flowOf(existingEpisodes[podcastId].orEmpty())

    override suspend fun getEpisode(guid: String): Episode? = null
    override suspend fun getLastUnfinishedPlayback(): Episode? = null
    override suspend fun updateProgress(guid: String, positionMs: Long, isPlayed: Boolean) = Unit
    override suspend fun updateFavorite(guid: String, isFavorite: Boolean) = Unit
    override fun observeFavoriteEpisodes(): Flow<List<Episode>> = flowOf(emptyList())
    override fun observeHistoryEpisodes(): Flow<List<Episode>> = flowOf(emptyList())
    override suspend fun touchLastPlayed(guid: String, ts: Long) = Unit
    override fun observeNewEpisodeCounts(threshold: Long): Flow<Map<Long, Int>> =
        flowOf(newEpisodeCounts)
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
