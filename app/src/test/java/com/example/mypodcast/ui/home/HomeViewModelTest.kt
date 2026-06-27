package com.example.mypodcast.ui.home

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.model.TopShow
import com.example.mypodcast.domain.repository.EpisodeRepository
import com.example.mypodcast.domain.repository.PodcastRepository
import com.example.mypodcast.domain.usecase.podcast.GetFeaturedPodcastsUseCase
import com.example.mypodcast.domain.usecase.podcast.ObserveTopShowsUseCase
import com.example.mypodcast.ui.library.MainDispatcherRule
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_exposesTopShowsFromUseCase() = runTest {
        val topShow = TopShow(
            podcast = podcast(id = 1L),
            playedEpisodeCount = 3,
            latestPlayedAt = 9_000L
        )

        val viewModel = HomeViewModel(
            getFeaturedPodcasts = GetFeaturedPodcastsUseCase(
                FakePodcastRepository(featured = mapOf("Technology" to emptyList()))
            ),
            observeTopShows = ObserveTopShowsUseCase(
                FakeEpisodeRepository(topShows = flowOf(listOf(topShow)))
            )
        )
        advanceUntilIdle()

        assertEquals(listOf(topShow), viewModel.uiState.value.topShows)
    }

    @Test
    fun topShowsFailure_doesNotFailFeaturedHomeContent() = runTest {
        val featuredPodcast = podcast(id = 2L)

        val viewModel = HomeViewModel(
            getFeaturedPodcasts = GetFeaturedPodcastsUseCase(
                FakePodcastRepository(featured = mapOf("Technology" to listOf(featuredPodcast)))
            ),
            observeTopShows = ObserveTopShowsUseCase(
                FakeEpisodeRepository(topShows = flow { error("boom") })
            )
        )
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(
            listOf(featuredPodcast),
            viewModel.uiState.value.featuredByCategory["Technology"]
        )
        assertEquals(emptyList<TopShow>(), viewModel.uiState.value.topShows)
        assertEquals(null, viewModel.uiState.value.error)
    }

    private fun podcast(id: Long) = Podcast(
        id = id,
        title = "Podcast $id",
        artworkUrl = "https://example.com/$id.jpg",
        artistName = "Artist $id",
        feedUrl = "https://example.com/$id.xml",
        description = null,
        genres = emptyList(),
        episodeCount = 0
    )
}

private class FakePodcastRepository(
    private val featured: Map<String, List<Podcast>>
) : PodcastRepository {
    override suspend fun search(query: String): List<Podcast> = emptyList()
    override suspend fun getFeatured(category: String): List<Podcast> =
        featured[category].orEmpty()

    override suspend fun getDetail(podcastId: Long): Podcast =
        error("Not needed for HomeViewModel tests")

    override fun observePodcast(podcastId: Long): Flow<Podcast?> = flowOf(null)
}

private class FakeEpisodeRepository(
    private val topShows: Flow<List<TopShow>>
) : EpisodeRepository {
    override suspend fun fetchEpisodesForPodcast(podcastId: Long, feedUrl: String): List<Episode> =
        emptyList()

    override fun observeEpisodesForPodcast(podcastId: Long): Flow<List<Episode>> =
        flowOf(emptyList())

    override suspend fun getEpisode(guid: String): Episode? = null
    override suspend fun getLastUnfinishedPlayback(): Episode? = null
    override suspend fun updateProgress(guid: String, positionMs: Long, isPlayed: Boolean) = Unit
    override suspend fun updateFavorite(guid: String, isFavorite: Boolean) = Unit
    override fun observeFavoriteEpisodes(): Flow<List<Episode>> = flowOf(emptyList())
    override fun observeHistoryEpisodes(): Flow<List<Episode>> = flowOf(emptyList())
    override suspend fun touchLastPlayed(guid: String, ts: Long) = Unit
    override fun observeNewEpisodeCounts(threshold: Long): Flow<Map<Long, Int>> =
        flowOf(emptyMap())

    override fun observeTopShows(limit: Int): Flow<List<TopShow>> = topShows
}
