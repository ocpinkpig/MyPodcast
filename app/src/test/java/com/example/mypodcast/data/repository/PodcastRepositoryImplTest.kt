package com.example.mypodcast.data.repository

import androidx.room.Room
import com.example.mypodcast.data.local.AppDatabase
import com.example.mypodcast.data.local.entity.PodcastEntity
import com.example.mypodcast.data.remote.api.PodcastApiService
import com.example.mypodcast.data.remote.api.dto.ItunesResult
import com.example.mypodcast.data.remote.api.dto.ItunesSearchResponse
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PodcastRepositoryImplTest {
    private lateinit var db: AppDatabase
    private lateinit var api: RecordingPodcastApiService
    private lateinit var repository: PodcastRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        api = RecordingPodcastApiService()
        repository = PodcastRepositoryImpl(api, db.podcastDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun getDetail_returnsCachedPodcastWithoutNetworkLookup() = runTest {
        db.podcastDao().upsert(
            PodcastEntity(
                id = 1L,
                title = "Cached podcast",
                artworkUrl = "https://example.com/cached.jpg",
                artistName = "Cached artist",
                feedUrl = "https://example.com/cached.xml",
                description = "Cached description",
                genres = "Technology,News",
                episodeCount = 42
            )
        )

        val podcast = repository.getDetail(1L)

        assertEquals("Cached podcast", podcast.title)
        assertEquals("Cached description", podcast.description)
        assertEquals(0, api.lookupCalls)
    }

    @Test
    fun getDetail_fetchesAndStoresPodcastWhenCacheMisses() = runTest {
        api.lookupResponse = ItunesSearchResponse(
            resultCount = 1,
            results = listOf(
                ItunesResult(
                    trackId = 2L,
                    trackName = "Remote podcast",
                    artworkUrl = "https://example.com/remote.jpg",
                    artistName = "Remote artist",
                    feedUrl = "https://example.com/remote.xml",
                    trackCount = 7,
                    genres = listOf("Culture"),
                    description = "Remote description"
                )
            )
        )

        val podcast = repository.getDetail(2L)

        assertEquals("Remote podcast", podcast.title)
        assertEquals(1, api.lookupCalls)
        assertEquals("Remote podcast", db.podcastDao().getById(2L)?.title)
    }
}

private class RecordingPodcastApiService : PodcastApiService {
    var lookupCalls = 0
    var lookupResponse = ItunesSearchResponse(resultCount = 0, results = emptyList())

    override suspend fun search(term: String, media: String, limit: Int): ItunesSearchResponse =
        ItunesSearchResponse(resultCount = 0, results = emptyList())

    override suspend fun lookup(id: Long): ItunesSearchResponse {
        lookupCalls += 1
        return lookupResponse
    }

    override suspend fun searchByCategory(
        category: String,
        media: String,
        entity: String,
        limit: Int
    ): ItunesSearchResponse =
        ItunesSearchResponse(resultCount = 0, results = emptyList())
}
