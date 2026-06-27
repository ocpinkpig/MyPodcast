package com.example.mypodcast.data.repository

import androidx.room.Room
import com.example.mypodcast.data.local.AppDatabase
import com.example.mypodcast.data.local.entity.EpisodeEntity
import com.example.mypodcast.data.local.entity.PodcastEntity
import com.example.mypodcast.data.remote.rss.RssParser
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeRepositoryImplTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: EpisodeRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        repository = EpisodeRepositoryImpl(
            rssParser = RssParser(OkHttpClient()),
            episodeDao = db.episodeDao(),
            podcastDao = db.podcastDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun observeTopShows_mapsPodcastAndStatsFromDaoRows() = runTest {
        db.podcastDao().upsert(
            PodcastEntity(
                id = 1L,
                title = "Top Podcast",
                artworkUrl = "https://example.com/art.jpg",
                artistName = "Host",
                feedUrl = "https://example.com/feed.xml",
                description = "Description",
                genres = "Technology,News",
                episodeCount = 10
            )
        )
        db.episodeDao().upsertAll(
            listOf(
                episode("first", lastPlayedAt = 1_000L),
                episode("second", lastPlayedAt = 5_000L)
            )
        )

        val topShows = repository.observeTopShows(limit = 10).first()

        assertEquals(1, topShows.size)
        assertEquals("Top Podcast", topShows.single().podcast.title)
        assertEquals(listOf("Technology", "News"), topShows.single().podcast.genres)
        assertEquals(2, topShows.single().playedEpisodeCount)
        assertEquals(5_000L, topShows.single().latestPlayedAt)
    }

    private fun episode(
        guid: String,
        lastPlayedAt: Long
    ) = EpisodeEntity(
        guid = guid,
        podcastId = 1L,
        title = "Episode $guid",
        description = null,
        audioUrl = "https://example.com/$guid.mp3",
        artworkUrl = null,
        publishedAt = 0L,
        durationSeconds = 60,
        fileSizeBytes = 1_024L,
        lastPlayedAt = lastPlayedAt
    )
}
