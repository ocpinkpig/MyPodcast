package com.example.mypodcast.data.backup

import androidx.room.Room
import com.example.mypodcast.data.local.AppDatabase
import com.example.mypodcast.data.local.entity.DownloadedEpisodeEntity
import com.example.mypodcast.data.local.entity.EpisodeEntity
import com.example.mypodcast.data.local.entity.PodcastEntity
import com.example.mypodcast.data.local.entity.QueueItemEntity
import com.example.mypodcast.data.local.entity.SavedMomentEntity
import com.example.mypodcast.data.local.entity.SubscriptionEntity
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
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
class BackupRepositoryImplTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: BackupRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        repository = BackupRepositoryImpl(
            db = db,
            podcastDao = db.podcastDao(),
            episodeDao = db.episodeDao(),
            subscriptionDao = db.subscriptionDao(),
            downloadedEpisodeDao = db.downloadedEpisodeDao(),
            queueDao = db.queueDao(),
            savedMomentDao = db.savedMomentDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun podcast(id: Long) = PodcastEntity(
        id = id,
        title = "Show $id",
        artworkUrl = "https://art/$id.png",
        artistName = "Artist",
        feedUrl = "https://feed/$id.xml",
        description = null,
        genres = "Tech",
        episodeCount = 10
    )

    private fun episode(
        guid: String,
        podcastId: Long,
        isFavorite: Boolean = false,
        playbackPosition: Long = 0L
    ) = EpisodeEntity(
        guid = guid,
        podcastId = podcastId,
        title = "Episode $guid",
        description = null,
        audioUrl = "https://audio/$guid.mp3",
        artworkUrl = null,
        publishedAt = 1_000L,
        durationSeconds = 60,
        fileSizeBytes = 1_024L,
        playbackPosition = playbackPosition,
        isFavorite = isFavorite
    )

    private suspend fun seedLibrary() {
        db.podcastDao().upsertAll(listOf(podcast(1L), podcast(2L), podcast(3L)))
        db.subscriptionDao().subscribe(SubscriptionEntity(podcastId = 1L, subscribedAt = 10L))
        db.episodeDao().upsertAll(
            listOf(
                episode("fav", 1L, isFavorite = true),
                episode("downloaded", 2L),
                episode("plain", 3L)
            )
        )
        db.downloadedEpisodeDao().insert(
            DownloadedEpisodeEntity(
                episodeGuid = "downloaded",
                podcastId = 2L,
                localFilePath = "/files/episodes/downloaded.mp3",
                downloadedAt = 20L,
                fileSizeBytes = 1_024L
            )
        )
        db.queueDao().insert(QueueItemEntity("fav", 0))
        db.savedMomentDao().insert(
            SavedMomentEntity(
                episodeGuid = "fav",
                positionMs = 500L,
                clipStartMs = 0L,
                clipEndMs = 1_000L,
                transcriptText = null,
                createdAt = 30L
            )
        )
    }

    @Test
    fun createBackup_capturesUserStateAndReferencedPodcasts() = runTest {
        seedLibrary()

        val backup = repository.createBackup()

        assertEquals(com.example.mypodcast.domain.model.backup.LibraryBackup.FORMAT_VERSION, backup.formatVersion)
        assertTrue(backup.exportedAt > 0L)
        assertEquals(setOf("fav", "downloaded"), backup.episodes.map { it.guid }.toSet())
        assertEquals(setOf(1L, 2L), backup.podcasts.map { it.id }.toSet())
        assertEquals(listOf(1L), backup.subscriptions.map { it.podcastId })
        assertEquals(listOf("downloaded"), backup.downloads.map { it.episodeGuid })
        assertEquals(listOf("fav"), backup.queue.map { it.episodeGuid })
        assertEquals(listOf("fav"), backup.moments.map { it.episodeGuid })
    }

    @Test
    fun summarize_countsSectionsAndSkipsAlreadyDownloaded() = runTest {
        seedLibrary()
        val backup = repository.createBackup()

        val summary = repository.summarize(backup)

        assertEquals(1, summary.shows)
        assertEquals(1, summary.favorites)
        assertEquals(1, summary.moments)
        assertEquals(1, summary.queueItems)
        assertEquals(0, summary.downloadsToRestore)
    }

    @Test
    fun summarize_countsDownloadsMissingLocally() = runTest {
        seedLibrary()
        val backup = repository.createBackup()
        db.downloadedEpisodeDao().deleteByGuid("downloaded")

        val summary = repository.summarize(backup)

        assertEquals(1, summary.downloadsToRestore)
    }
}
