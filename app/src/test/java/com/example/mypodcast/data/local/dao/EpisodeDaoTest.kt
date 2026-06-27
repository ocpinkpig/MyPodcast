package com.example.mypodcast.data.local.dao

import androidx.room.Room
import com.example.mypodcast.data.local.AppDatabase
import com.example.mypodcast.data.local.entity.EpisodeEntity
import com.example.mypodcast.data.local.entity.PodcastEntity
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.first
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

    @Test
    fun observeTopShows_ordersByPlayedCountThenLatestPlayback() = runTest {
        db.podcastDao().upsertAll(
            listOf(
                podcast(id = 1L, title = "Most Played"),
                podcast(id = 2L, title = "Recent Tie Winner"),
                podcast(id = 3L, title = "Older Tie")
            )
        )
        episodeDao.upsertAll(
            listOf(
                episode("one-a", podcastId = 1L, lastPlayedAt = 1_000L),
                episode("one-b", podcastId = 1L, playbackPosition = 60_000L),
                episode("one-c", podcastId = 1L, isPlayed = true),
                episode("two-a", podcastId = 2L, lastPlayedAt = 5_000L),
                episode("two-b", podcastId = 2L, lastPlayedAt = 4_000L),
                episode("three-a", podcastId = 3L, lastPlayedAt = 3_000L),
                episode("three-b", podcastId = 3L, lastPlayedAt = 2_000L),
                episode("ignored", podcastId = 3L)
            )
        )

        val topShows = episodeDao.observeTopShows(limit = 10).first()

        assertEquals(listOf(1L, 2L, 3L), topShows.map { it.id })
        assertEquals(listOf(3, 2, 2), topShows.map { it.playedEpisodeCount })
    }

    @Test
    fun observeTopShows_omitsShowsWithoutPlaybackHistory() = runTest {
        db.podcastDao().upsertAll(
            listOf(
                podcast(id = 1L, title = "Played"),
                podcast(id = 2L, title = "Never Played")
            )
        )
        episodeDao.upsertAll(
            listOf(
                episode("played", podcastId = 1L, playbackPosition = 60_000L),
                episode("never", podcastId = 2L)
            )
        )

        val topShows = episodeDao.observeTopShows(limit = 10).first()

        assertEquals(listOf(1L), topShows.map { it.id })
    }

    private fun podcast(
        id: Long = 1L,
        title: String = "Podcast"
    ) = PodcastEntity(
        id = id,
        title = title,
        artworkUrl = "",
        artistName = "Host",
        feedUrl = "https://example.com/feed.xml",
        description = null,
        genres = "",
        episodeCount = 4
    )

    private fun episode(
        guid: String,
        podcastId: Long = 1L,
        lastPlayedAt: Long = 0L,
        isPlayed: Boolean = false,
        playbackPosition: Long = 0L
    ) = EpisodeEntity(
        guid = guid,
        podcastId = podcastId,
        title = "Episode $guid",
        description = null,
        audioUrl = "https://example.com/$guid.mp3",
        artworkUrl = null,
        publishedAt = 0L,
        durationSeconds = 60,
        fileSizeBytes = 1_024L,
        playbackPosition = playbackPosition,
        lastPlayedAt = lastPlayedAt,
        isPlayed = isPlayed
    )
}
