package com.example.mypodcast.data.repository

import androidx.room.Room
import com.example.mypodcast.data.local.AppDatabase
import com.example.mypodcast.data.local.entity.DownloadedEpisodeEntity
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
class LibraryRepositoryImplTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: LibraryRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        repository = LibraryRepositoryImpl(
            context = RuntimeEnvironment.getApplication(),
            subscriptionDao = db.subscriptionDao(),
            podcastDao = db.podcastDao(),
            episodeDao = db.episodeDao(),
            downloadedEpisodeDao = db.downloadedEpisodeDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun observeDownloadedEpisodes_sortsByEpisodePublishDateDescending() = runTest {
        db.podcastDao().upsert(podcast())
        db.episodeDao().upsertAll(
            listOf(
                episode(guid = "older", publishedAt = 1_000L),
                episode(guid = "newer", publishedAt = 3_000L),
                episode(guid = "middle", publishedAt = 2_000L)
            )
        )
        db.downloadedEpisodeDao().insert(download(guid = "older", downloadedAt = 30_000L))
        db.downloadedEpisodeDao().insert(download(guid = "newer", downloadedAt = 10_000L))
        db.downloadedEpisodeDao().insert(download(guid = "middle", downloadedAt = 20_000L))

        val downloads = repository.observeDownloadedEpisodes().first()

        assertEquals(listOf("newer", "middle", "older"), downloads.map { it.guid })
    }

    private fun podcast() = PodcastEntity(
        id = 1L,
        title = "Podcast",
        artworkUrl = "https://example.com/artwork.jpg",
        artistName = "Host",
        feedUrl = "https://example.com/feed.xml",
        description = null,
        genres = "",
        episodeCount = 3
    )

    private fun episode(guid: String, publishedAt: Long) = EpisodeEntity(
        guid = guid,
        podcastId = 1L,
        title = "Episode $guid",
        description = null,
        audioUrl = "https://example.com/$guid.mp3",
        artworkUrl = null,
        publishedAt = publishedAt,
        durationSeconds = 60,
        fileSizeBytes = 1_024L
    )

    private fun download(guid: String, downloadedAt: Long) = DownloadedEpisodeEntity(
        episodeGuid = guid,
        podcastId = 1L,
        localFilePath = "/tmp/$guid.mp3",
        downloadedAt = downloadedAt,
        fileSizeBytes = 1_024L
    )
}
