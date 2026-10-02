package com.example.mypodcast.data.repository

import androidx.room.Room
import com.example.mypodcast.data.local.AppDatabase
import com.example.mypodcast.data.local.entity.DownloadedEpisodeEntity
import com.example.mypodcast.data.local.entity.EpisodeEntity
import com.example.mypodcast.data.local.entity.PodcastEntity
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryRepositoryImplTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: LibraryRepositoryImpl
    private val episodesDir get() = File(RuntimeEnvironment.getApplication().filesDir, "episodes")

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
            downloadedEpisodeDao = db.downloadedEpisodeDao(),
            generatedTranscriptStore = com.example.mypodcast.data.transcription.GeneratedTranscriptStore(
                RuntimeEnvironment.getApplication().filesDir
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
        episodesDir.deleteRecursively()
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

    @Test
    fun observeDownloadedEpisodes_keepsFeedUrlSoPlayerPicksTheSource() = runTest {
        db.podcastDao().upsert(podcast())
        db.episodeDao().upsertAll(listOf(episode(guid = "ep", publishedAt = 1_000L)))
        db.downloadedEpisodeDao().insert(download(guid = "ep", downloadedAt = 10_000L))

        val downloads = repository.observeDownloadedEpisodes().first()

        assertEquals("https://example.com/ep.mp3", downloads.single().audioUrl)
    }

    @Test
    fun cleanupOrphanedFiles_deletesOldUnrecordedFilesIncludingNestedOnes() = runTest {
        val orphan = oldFile("orphan.mp3")
        val nestedOrphan = oldFile("https:/host.example/orphan.mp3.mp3")
        val abandonedPart = oldFile("crashed.mp3.part")

        repository.cleanupOrphanedFiles()

        assertFalse(orphan.exists())
        assertFalse(nestedOrphan.exists())
        assertFalse(abandonedPart.exists())
    }

    @Test
    fun cleanupOrphanedFiles_keepsRecordedFilesAndDownloadsInFlight() = runTest {
        val recorded = oldFile("https:/host.example/kept.mp3.mp3")
        db.downloadedEpisodeDao().insert(
            DownloadedEpisodeEntity(
                episodeGuid = "https://host.example/kept.mp3",
                podcastId = 1L,
                localFilePath = recorded.absolutePath,
                fileSizeBytes = 5L
            )
        )
        // Still being written, and renamed but not yet recorded.
        val partial = newFile("downloading.mp3.part")
        val finishedUnrecorded = newFile("just-finished.mp3")

        repository.cleanupOrphanedFiles()

        assertTrue(recorded.exists())
        assertTrue(partial.exists())
        assertTrue(finishedUnrecorded.exists())
    }

    @Test
    fun cleanupOrphanedFiles_removesOldEmptyDirectories() = runTest {
        val emptyDir = File(episodesDir, "https:/host.example").apply { mkdirs() }
        emptyDir.setLastModified(twoHoursAgo())

        repository.cleanupOrphanedFiles()

        assertFalse(emptyDir.exists())
    }

    private fun newFile(relativePath: String) = File(episodesDir, relativePath).apply {
        parentFile?.mkdirs()
        writeText("audio")
    }

    private fun oldFile(relativePath: String) = newFile(relativePath).apply {
        setLastModified(twoHoursAgo())
    }

    private fun twoHoursAgo() = System.currentTimeMillis() - 2 * 60 * 60 * 1000L

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
