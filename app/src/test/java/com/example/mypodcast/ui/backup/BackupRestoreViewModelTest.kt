package com.example.mypodcast.ui.backup

import android.net.Uri
import androidx.room.Room
import com.example.mypodcast.data.backup.BackupRepositoryImpl
import com.example.mypodcast.data.backup.BackupSerializer
import com.example.mypodcast.data.local.AppDatabase
import com.example.mypodcast.data.local.entity.EpisodeEntity
import com.example.mypodcast.data.local.entity.PodcastEntity
import com.example.mypodcast.data.local.entity.SubscriptionEntity
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.TopShow
import com.example.mypodcast.domain.repository.EpisodeRepository
import com.example.mypodcast.domain.usecase.backup.ExportLibraryUseCase
import com.example.mypodcast.domain.usecase.backup.ImportLibraryUseCase
import com.example.mypodcast.domain.usecase.backup.InspectBackupUseCase
import com.example.mypodcast.work.RestoreProgress
import com.example.mypodcast.work.RestoreScheduler
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestoreViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var viewModel: BackupRestoreViewModel
    private lateinit var scheduler: FakeScheduler
    private lateinit var episodeRepository: FakeEpisodeRepository

    private class FakeScheduler : RestoreScheduler {
        val scheduled = mutableListOf<List<String>>()
        val progress = MutableStateFlow<RestoreProgress?>(null)
        override fun scheduleRestore(episodeGuids: List<String>) {
            scheduled += listOf(episodeGuids)
        }
        override fun observeProgress(): Flow<RestoreProgress?> = progress
    }

    private class FakeEpisodeRepository : EpisodeRepository {
        val refreshed = mutableListOf<Long>()
        override suspend fun fetchEpisodesForPodcast(podcastId: Long, feedUrl: String): List<Episode> {
            refreshed += podcastId
            return emptyList()
        }
        override fun observeEpisodesForPodcast(podcastId: Long): Flow<List<Episode>> = TODO()
        override suspend fun getEpisode(guid: String): Episode? = TODO()
        override suspend fun getLastUnfinishedPlayback(): Episode? = TODO()
        override suspend fun updateProgress(guid: String, positionMs: Long, isPlayed: Boolean) = TODO()
        override suspend fun updateFavorite(guid: String, isFavorite: Boolean) = TODO()
        override fun observeFavoriteEpisodes(): Flow<List<Episode>> = TODO()
        override fun observeHistoryEpisodes(): Flow<List<Episode>> = TODO()
        override suspend fun touchLastPlayed(guid: String, ts: Long) = TODO()
        override fun observeNewEpisodeCounts(threshold: Long): Flow<Map<Long, Int>> = TODO()
        override fun observeTopShows(limit: Int): Flow<List<TopShow>> = TODO()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            // Bind Room's executors to the test scheduler so advanceUntilIdle()
            // covers suspend DAO hops; default executors are real thread pools.
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        val repository = BackupRepositoryImpl(
            db = db,
            podcastDao = db.podcastDao(),
            episodeDao = db.episodeDao(),
            subscriptionDao = db.subscriptionDao(),
            downloadedEpisodeDao = db.downloadedEpisodeDao(),
            queueDao = db.queueDao(),
            savedMomentDao = db.savedMomentDao()
        )
        val serializer = BackupSerializer()
        scheduler = FakeScheduler()
        episodeRepository = FakeEpisodeRepository()
        viewModel = BackupRestoreViewModel(
            context = RuntimeEnvironment.getApplication(),
            exportLibrary = ExportLibraryUseCase(repository, serializer),
            inspectBackup = InspectBackupUseCase(repository, serializer),
            importLibrary = ImportLibraryUseCase(repository),
            restoreScheduler = scheduler,
            episodeRepository = episodeRepository,
            ioDispatcher = dispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private suspend fun seedFavorite() {
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
        db.subscriptionDao().subscribe(SubscriptionEntity(podcastId = 1L))
        db.episodeDao().upsertAll(
            listOf(
                EpisodeEntity(
                    guid = "fav",
                    podcastId = 1L,
                    title = "Fav",
                    description = null,
                    audioUrl = "https://a.mp3",
                    artworkUrl = null,
                    publishedAt = 1L,
                    durationSeconds = 1,
                    fileSizeBytes = 1L,
                    isFavorite = true
                )
            )
        )
    }

    private fun tempFileUri(name: String): Uri {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, name)
        return Uri.fromFile(file)
    }

    @Test
    fun export_writesFileAndReportsSuccess() = runTest(dispatcher) {
        seedFavorite()
        val uri = tempFileUri("backup.json")

        viewModel.export(uri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Backup saved", state.message)
        assertNotNull(state.lastExportAt)
        assertTrue(File(uri.path!!).readText().contains("\"fav\""))
    }

    @Test
    fun inspect_validFile_populatesPendingImport() = runTest(dispatcher) {
        seedFavorite()
        val uri = tempFileUri("backup.json")
        viewModel.export(uri)
        advanceUntilIdle()

        viewModel.inspect(uri)
        advanceUntilIdle()

        val pending = viewModel.uiState.value.pendingImport
        assertNotNull(pending)
        assertEquals(1, pending!!.summary.shows)
        assertEquals(1, pending.summary.favorites)
    }

    @Test
    fun inspect_invalidFile_reportsError() = runTest(dispatcher) {
        val uri = tempFileUri("junk.json")
        File(uri.path!!).writeText("not json at all {{{")

        viewModel.inspect(uri)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingImport)
        assertEquals("This file isn't a MyPodcast backup.", viewModel.uiState.value.message)
    }

    @Test
    fun confirmImport_importsSchedulesAndRefreshesFeeds() = runTest(dispatcher) {
        seedFavorite()
        val uri = tempFileUri("backup.json")
        viewModel.export(uri)
        advanceUntilIdle()
        db.clearAllTables()

        viewModel.inspect(uri)
        advanceUntilIdle()
        viewModel.confirmImport()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.pendingImport)
        assertEquals("Backup restored", state.message)
        assertEquals(listOf(1L), episodeRepository.refreshed)
        assertEquals(0, scheduler.scheduled.size)
        assertEquals(listOf(1L), db.subscriptionDao().getAll().map { it.podcastId })
    }

    @Test
    fun restoreProgress_flowsIntoUiState() = runTest(dispatcher) {
        scheduler.progress.value = RestoreProgress(done = 3, total = 12)
        advanceUntilIdle()

        assertEquals(RestoreProgress(3, 12), viewModel.uiState.value.restoreProgress)
    }
}
