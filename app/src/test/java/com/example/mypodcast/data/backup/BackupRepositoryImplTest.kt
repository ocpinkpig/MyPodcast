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

    @Test
    fun import_intoEmptyDatabase_restoresEverything() = runTest {
        seedLibrary()
        val backup = repository.createBackup()

        db.clearAllTables()
        val result = repository.import(backup)

        // Checked against the episodes table directly: "downloaded" carries no
        // user-state flags and its download row is deliberately not restored
        // (it is reported via downloadsToRestore instead), so it would never
        // surface through getAllWithUserState().
        assertEquals(
            setOf("fav", "downloaded"),
            db.episodeDao().getByGuids(listOf("fav", "downloaded")).map { it.guid }.toSet()
        )
        assertEquals(listOf(1L), db.subscriptionDao().getAll().map { it.podcastId })
        assertEquals("Show 1", db.podcastDao().getById(1L)?.title)
        assertEquals(listOf("fav"), db.queueDao().getQueueItemsOrdered().map { it.episodeGuid })
        assertEquals(1, db.savedMomentDao().getAll().size)
        assertEquals(listOf("downloaded"), result.downloadsToRestore)
        assertEquals(listOf(1L), result.feedTargets.map { it.podcastId })
        assertEquals(listOf("https://feed/1.xml"), result.feedTargets.map { it.feedUrl })
    }

    @Test
    fun import_mergesUserStateWithoutClobberingFresherLocalData() = runTest {
        seedLibrary()
        val backup = repository.createBackup()

        // Local state moved on after the backup was taken.
        db.episodeDao().updateFavorite("fav", false)
        db.episodeDao().updateProgress("fav", 9_000L, true)
        repository.import(backup)

        val merged = db.episodeDao().getByGuid("fav")!!
        assertTrue(merged.isFavorite)
        assertTrue(merged.isPlayed)
        assertEquals(9_000L, merged.playbackPosition)
    }

    @Test
    fun import_appendsQueueItemsWithoutDuplicates() = runTest {
        seedLibrary()
        db.episodeDao().upsertAll(listOf(episode("local-queued", 1L)))
        val backup = repository.createBackup()

        db.queueDao().clear()
        db.queueDao().insert(QueueItemEntity("local-queued", 0))
        repository.import(backup)

        val queue = db.queueDao().getQueueItemsOrdered()
        assertEquals(listOf("local-queued", "fav"), queue.map { it.episodeGuid })
        assertEquals(listOf(0, 1), queue.map { it.position })
    }

    @Test
    fun import_isIdempotentForMoments() = runTest {
        seedLibrary()
        val backup = repository.createBackup()

        repository.import(backup)
        repository.import(backup)

        assertEquals(1, db.savedMomentDao().getAll().size)
    }

    @Test
    fun import_skipsAlreadyDownloadedEpisodes() = runTest {
        seedLibrary()
        val backup = repository.createBackup()

        val result = repository.import(backup)

        assertEquals(emptyList<String>(), result.downloadsToRestore)
    }

    @Test
    fun import_dropsEpisodesWithUnknownPodcast() = runTest {
        seedLibrary()
        val backup = repository.createBackup()
        val corrupted = backup.copy(
            episodes = backup.episodes + backup.episodes.first().copy(guid = "orphan", podcastId = 999L),
            podcasts = backup.podcasts
        )

        db.clearAllTables()
        repository.import(corrupted)

        assertEquals(null, db.episodeDao().getByGuid("orphan"))
    }
}
