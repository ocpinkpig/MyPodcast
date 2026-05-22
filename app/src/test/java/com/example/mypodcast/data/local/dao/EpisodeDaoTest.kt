package com.example.mypodcast.data.local.dao

import androidx.room.Room
import com.example.mypodcast.data.local.AppDatabase
import com.example.mypodcast.data.local.entity.EpisodeEntity
import com.example.mypodcast.data.local.entity.PodcastEntity
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
class EpisodeDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var episodeDao: EpisodeDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        episodeDao = db.episodeDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun getLastPlayback_returnsMostRecentPlayback() = runTest {
        db.podcastDao().upsert(podcast())
        episodeDao.upsertAll(
            listOf(
                episode("older", lastPlayedAt = 1_000L),
                episode("latest", lastPlayedAt = 2_000L),
                episode("never-played", lastPlayedAt = 0L)
            )
        )

        val restored = episodeDao.getLastPlayback()

        assertEquals("latest", restored?.guid)
    }

    @Test
    fun getLastPlayback_returnsPlayedEpisodeWhenItWasLatest() = runTest {
        db.podcastDao().upsert(podcast())
        episodeDao.upsertAll(
            listOf(
                episode("older", lastPlayedAt = 1_000L),
                episode("played", lastPlayedAt = 3_000L, isPlayed = true)
            )
        )

        val latest = episodeDao.getLastPlayback()

        assertEquals("played", latest?.guid)
    }

    private fun podcast() = PodcastEntity(
        id = 1L,
        title = "Podcast",
        artworkUrl = "",
        artistName = "Host",
        feedUrl = "https://example.com/feed.xml",
        description = null,
        genres = "",
        episodeCount = 4
    )

    private fun episode(
        guid: String,
        lastPlayedAt: Long,
        isPlayed: Boolean = false
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
        lastPlayedAt = lastPlayedAt,
        isPlayed = isPlayed
    )
}
