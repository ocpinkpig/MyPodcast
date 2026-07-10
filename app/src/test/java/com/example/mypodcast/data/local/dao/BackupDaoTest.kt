package com.example.mypodcast.data.local.dao

import androidx.room.Room
import com.example.mypodcast.data.local.AppDatabase
import com.example.mypodcast.data.local.entity.EpisodeEntity
import com.example.mypodcast.data.local.entity.PodcastEntity
import com.example.mypodcast.data.local.entity.QueueItemEntity
import com.example.mypodcast.data.local.entity.SavedMomentEntity
import com.example.mypodcast.data.local.entity.SubscriptionEntity
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
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
class BackupDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun podcast(id: Long = 1L) = PodcastEntity(
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
        podcastId: Long = 1L,
        isFavorite: Boolean = false,
        isPlayed: Boolean = false,
        playbackPosition: Long = 0L,
        lastPlayedAt: Long = 0L
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
        isPlayed = isPlayed,
        isFavorite = isFavorite,
        lastPlayedAt = lastPlayedAt
    )

    @Test
    fun getAllWithUserState_returnsOnlyEpisodesWithUserState() = runTest {
        db.podcastDao().upsert(podcast())
        db.episodeDao().upsertAll(
            listOf(
                episode("fav", isFavorite = true),
                episode("played", isPlayed = true),
                episode("progress", playbackPosition = 5_000L),
                episode("history", lastPlayedAt = 9_000L),
                episode("queued"),
                episode("moment"),
                episode("plain")
            )
        )
        db.queueDao().insert(QueueItemEntity("queued", 0))
        db.savedMomentDao().insert(
            SavedMomentEntity(
                episodeGuid = "moment",
                positionMs = 1_000L,
                clipStartMs = 0L,
                clipEndMs = 2_000L,
                transcriptText = null,
                createdAt = 1L
            )
        )

        val guids = db.episodeDao().getAllWithUserState().map { it.guid }.toSet()

        assertEquals(setOf("fav", "played", "progress", "history", "queued", "moment"), guids)
    }

    @Test
    fun insertAllIgnore_keepsExistingEpisodeRow() = runTest {
        db.podcastDao().upsert(podcast())
        db.episodeDao().upsertAll(listOf(episode("e1", playbackPosition = 7_000L)))

        db.episodeDao().insertAllIgnore(listOf(episode("e1", playbackPosition = 0L)))

        assertEquals(7_000L, db.episodeDao().getByGuid("e1")?.playbackPosition)
    }

    @Test
    fun mergeUserState_orsFlagsAndTakesMaxTimestamps() = runTest {
        db.podcastDao().upsert(podcast())
        db.episodeDao().upsertAll(
            listOf(episode("e1", isFavorite = false, isPlayed = true, playbackPosition = 9_000L, lastPlayedAt = 100L))
        )

        db.episodeDao().mergeUserState(
            guid = "e1",
            isFavorite = true,
            isPlayed = false,
            playbackPosition = 4_000L,
            lastPlayedAt = 500L
        )

        val merged = db.episodeDao().getByGuid("e1")!!
        assertTrue(merged.isFavorite)
        assertTrue(merged.isPlayed)
        assertEquals(9_000L, merged.playbackPosition)
        assertEquals(500L, merged.lastPlayedAt)
    }

    @Test
    fun podcastInsertAllIgnore_keepsExistingRow() = runTest {
        db.podcastDao().upsert(podcast(1L).copy(title = "Fresh local title"))

        db.podcastDao().insertAllIgnore(listOf(podcast(1L).copy(title = "Stale backup title"), podcast(2L)))

        assertEquals("Fresh local title", db.podcastDao().getById(1L)?.title)
        assertEquals("Show 2", db.podcastDao().getById(2L)?.title)
    }

    @Test
    fun subscriptionGetAll_returnsRows() = runTest {
        db.podcastDao().upsert(podcast(1L))
        db.subscriptionDao().subscribe(SubscriptionEntity(podcastId = 1L, subscribedAt = 42L))

        val all = db.subscriptionDao().getAll()

        assertEquals(listOf(1L), all.map { it.podcastId })
    }

    @Test
    fun savedMomentExists_matchesOnGuidPositionAndCreatedAt() = runTest {
        db.podcastDao().upsert(podcast())
        db.episodeDao().upsertAll(listOf(episode("e1")))
        db.savedMomentDao().insert(
            SavedMomentEntity(
                episodeGuid = "e1",
                positionMs = 1_000L,
                clipStartMs = 0L,
                clipEndMs = 2_000L,
                transcriptText = null,
                createdAt = 77L
            )
        )

        assertTrue(db.savedMomentDao().exists("e1", 1_000L, 77L))
        assertFalse(db.savedMomentDao().exists("e1", 1_000L, 78L))
    }

    @Test
    fun savedMomentGetAll_returnsRows() = runTest {
        db.podcastDao().upsert(podcast())
        db.episodeDao().upsertAll(listOf(episode("e1")))
        db.savedMomentDao().insert(
            SavedMomentEntity(
                episodeGuid = "e1",
                positionMs = 1_000L,
                clipStartMs = 0L,
                clipEndMs = 2_000L,
                transcriptText = "text",
                createdAt = 1L
            )
        )

        assertEquals(1, db.savedMomentDao().getAll().size)
    }
}
