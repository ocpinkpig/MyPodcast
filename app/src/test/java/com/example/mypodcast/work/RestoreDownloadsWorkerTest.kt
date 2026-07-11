package com.example.mypodcast.work

import android.content.Context
import androidx.room.Room
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.mypodcast.data.backup.RestoreQueueStore
import com.example.mypodcast.data.download.EpisodeAudioDownloader
import com.example.mypodcast.data.local.AppDatabase
import com.example.mypodcast.data.local.entity.DownloadedEpisodeEntity
import com.example.mypodcast.data.local.entity.EpisodeEntity
import com.example.mypodcast.data.local.entity.PodcastEntity
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestoreDownloadsWorkerTest {
    private lateinit var db: AppDatabase
    private lateinit var store: RestoreQueueStore
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private class FakeDownloader(
        private val context: Context,
        private val failGuids: Set<String> = emptySet()
    ) : EpisodeAudioDownloader {
        val downloaded = mutableListOf<String>()
        override suspend fun download(
            episodeGuid: String,
            audioUrl: String,
            expectedFileSizeBytes: Long,
            onProgress: (Int) -> Unit
        ): File {
            if (episodeGuid in failGuids) throw IOException("boom")
            downloaded += episodeGuid
            return File(context.filesDir, "episodes/$episodeGuid.mp3").apply {
                parentFile?.mkdirs()
                writeText("audio")
            }
        }
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RestoreQueueStore(context)
        store.clear()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedEpisodes(vararg guids: String) {
        db.podcastDao().upsert(
            PodcastEntity(
                id = 1L,
                title = "Show",
                artworkUrl = "",
                artistName = "",
                feedUrl = "https://feed.xml",
                description = null,
                genres = "",
                episodeCount = 1
            )
        )
        db.episodeDao().upsertAll(
            guids.map { guid ->
                EpisodeEntity(
                    guid = guid,
                    podcastId = 1L,
                    title = guid,
                    description = null,
                    audioUrl = "https://audio/$guid.mp3",
                    artworkUrl = null,
                    publishedAt = 1L,
                    durationSeconds = 1,
                    fileSizeBytes = 5L
                )
            }
        )
    }

    private fun buildWorker(downloader: EpisodeAudioDownloader): RestoreDownloadsWorker =
        TestListenableWorkerBuilder<RestoreDownloadsWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = RestoreDownloadsWorker(
                    appContext,
                    workerParameters,
                    store,
                    db.episodeDao(),
                    db.downloadedEpisodeDao(),
                    downloader
                )
            })
            .build() as RestoreDownloadsWorker

    @Test
    fun doWork_downloadsPendingAndRecordsRows() = runTest {
        seedEpisodes("e1", "e2")
        store.replace(listOf("e1", "e2"))
        val downloader = FakeDownloader(context)

        val result = buildWorker(downloader).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf("e1", "e2"), downloader.downloaded)
        assertNotNull(db.downloadedEpisodeDao().getByGuid("e1"))
        assertNotNull(db.downloadedEpisodeDao().getByGuid("e2"))
        assertEquals(0, store.snapshot().total)
    }

    @Test
    fun doWork_skipsAlreadyDownloadedAndMissingEpisodes() = runTest {
        seedEpisodes("already")
        db.downloadedEpisodeDao().insert(
            DownloadedEpisodeEntity(
                episodeGuid = "already",
                podcastId = 1L,
                localFilePath = "/x",
                fileSizeBytes = 1L
            )
        )
        store.replace(listOf("already", "ghost"))
        val downloader = FakeDownloader(context)

        val result = buildWorker(downloader).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(emptyList<String>(), downloader.downloaded)
    }

    @Test
    fun doWork_retriesWhenSomeDownloadsFail() = runTest {
        seedEpisodes("ok", "bad")
        store.replace(listOf("ok", "bad"))
        val downloader = FakeDownloader(context, failGuids = setOf("bad"))

        val result = buildWorker(downloader).doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertNotNull(db.downloadedEpisodeDao().getByGuid("ok"))
        assertEquals(listOf("bad"), store.snapshot().pending)
        assertEquals(2, store.snapshot().total)
    }

    @Test
    fun doWork_succeedsImmediatelyWhenQueueEmpty() = runTest {
        val result = buildWorker(FakeDownloader(context)).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }
}
