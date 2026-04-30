# Resume Playback Position + Played Indicator — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist the playback position of the current episode so that revisiting it resumes from the same point, and show a checkmark on episodes that have played to completion.

**Architecture:** `PlayerController` already seeks to `episode.playbackPosition` on load — the missing piece is writing that position back to Room. We extend `EpisodeEntity` with an `isPlayed` boolean, add an atomic `updateProgress` DAO method, and inject `EpisodeRepository` into `PlayerController` so it can persist progress at six trigger points (periodic, pause, manual seek, episode switch, end-of-stream, release). We also fix a latent bug where re-fetching the RSS feed wipes saved positions, by merging existing progress before upsert.

**Tech Stack:** Kotlin 2.3.20, Room 2.7.1 (with KSP 2.3.7), Media3 ExoPlayer 1.5.1, Hilt 2.59.2, Jetpack Compose, Coroutines, JUnit 4 for unit tests.

**Spec:** `docs/superpowers/specs/2026-04-29-resume-playback-position-design.md`

---

## File Structure

**Create:**
- `app/src/main/java/com/example/mypodcast/data/local/Migrations.kt` — Room migrations live here so we don't bloat `AppDatabase.kt`.
- `app/src/test/java/com/example/mypodcast/media/PlayerControllerProgressTest.kt` — unit test for the progress-write decision logic.

**Modify:**
- `app/src/main/java/com/example/mypodcast/data/local/entity/EpisodeEntity.kt` — add `isPlayed` column.
- `app/src/main/java/com/example/mypodcast/data/local/dao/EpisodeDao.kt` — replace `updatePosition` with `updateProgress`, add `getByGuids`.
- `app/src/main/java/com/example/mypodcast/data/local/AppDatabase.kt` — bump version to 2.
- `app/src/main/java/com/example/mypodcast/di/DatabaseModule.kt` — register migration.
- `app/src/main/java/com/example/mypodcast/domain/model/Episode.kt` — add `isPlayed` field.
- `app/src/main/java/com/example/mypodcast/domain/repository/EpisodeRepository.kt` — rename method to `updateProgress`.
- `app/src/main/java/com/example/mypodcast/data/repository/EpisodeRepositoryImpl.kt` — RSS-merge fix; map `isPlayed`.
- `app/src/main/java/com/example/mypodcast/data/repository/LibraryRepositoryImpl.kt` — map `isPlayed`.
- `app/src/main/java/com/example/mypodcast/media/PlayerController.kt` — inject `EpisodeRepository`; persist at six triggers.
- `app/src/main/java/com/example/mypodcast/ui/components/EpisodeListItem.kt` — show played-checkmark next to date row.
- `app/src/test/java/com/example/mypodcast/ui/player/PlayerViewModelTest.kt` — extend `episode()` helper with `isPlayed = false`.

---

## Task 1: Schema — add `isPlayed` column to `EpisodeEntity`

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/data/local/entity/EpisodeEntity.kt`

- [ ] **Step 1: Add the new column**

Replace the contents of `EpisodeEntity.kt` with:

```kotlin
package com.example.mypodcast.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "episodes",
    foreignKeys = [ForeignKey(
        entity = PodcastEntity::class,
        parentColumns = ["id"],
        childColumns = ["podcastId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("podcastId")]
)
data class EpisodeEntity(
    @PrimaryKey val guid: String,
    val podcastId: Long,
    val title: String,
    val description: String?,
    val audioUrl: String,
    val artworkUrl: String?,
    val publishedAt: Long,
    val durationSeconds: Int,
    val fileSizeBytes: Long,
    val playbackPosition: Long = 0L,
    val isPlayed: Boolean = false
)
```

- [ ] **Step 2: Confirm compile fails (no migration yet)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS — Kotlin compiles fine; the schema mismatch is detected only at runtime, but the compile step should still succeed.

---

## Task 2: Add Room migration v1 → v2

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/data/local/Migrations.kt`
- Modify: `app/src/main/java/com/example/mypodcast/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/example/mypodcast/di/DatabaseModule.kt`

- [ ] **Step 1: Create the Migrations file**

Create `app/src/main/java/com/example/mypodcast/data/local/Migrations.kt`:

```kotlin
package com.example.mypodcast.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN isPlayed INTEGER NOT NULL DEFAULT 0")
    }
}
```

- [ ] **Step 2: Bump database version**

In `app/src/main/java/com/example/mypodcast/data/local/AppDatabase.kt`, change:

```kotlin
    version = 1,
```

to:

```kotlin
    version = 2,
```

- [ ] **Step 3: Register the migration in `DatabaseModule`**

Replace `app/src/main/java/com/example/mypodcast/di/DatabaseModule.kt` with:

```kotlin
package com.example.mypodcast.di

import android.content.Context
import androidx.room.Room
import com.example.mypodcast.data.local.AppDatabase
import com.example.mypodcast.data.local.MIGRATION_1_2
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "mypodcast.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun providePodcastDao(db: AppDatabase) = db.podcastDao()

    @Provides
    fun provideEpisodeDao(db: AppDatabase) = db.episodeDao()

    @Provides
    fun provideSubscriptionDao(db: AppDatabase) = db.subscriptionDao()

    @Provides
    fun provideDownloadedEpisodeDao(db: AppDatabase) = db.downloadedEpisodeDao()
}
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/data/local/entity/EpisodeEntity.kt \
        app/src/main/java/com/example/mypodcast/data/local/AppDatabase.kt \
        app/src/main/java/com/example/mypodcast/data/local/Migrations.kt \
        app/src/main/java/com/example/mypodcast/di/DatabaseModule.kt
git commit -m "Add isPlayed column with Room migration to v2"
```

---

## Task 3: Extend `EpisodeDao` with `updateProgress` and `getByGuids`

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/data/local/dao/EpisodeDao.kt`

- [ ] **Step 1: Replace `updatePosition` and add `getByGuids`**

Replace the contents of `EpisodeDao.kt` with:

```kotlin
package com.example.mypodcast.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.mypodcast.data.local.entity.EpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC")
    fun observeByPodcast(podcastId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE guid = :guid")
    suspend fun getByGuid(guid: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE guid IN (:guids)")
    suspend fun getByGuids(guids: List<String>): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE guid IN (:guids)")
    fun observeByGuids(guids: List<String>): Flow<List<EpisodeEntity>>

    @Upsert
    suspend fun upsertAll(episodes: List<EpisodeEntity>)

    @Query("UPDATE episodes SET playbackPosition = :positionMs, isPlayed = :isPlayed WHERE guid = :guid")
    suspend fun updateProgress(guid: String, positionMs: Long, isPlayed: Boolean)
}
```

Note that `updatePosition` is **removed** — Task 4 updates the only caller.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL — `EpisodeRepositoryImpl.updatePlaybackPosition` references the removed `updatePosition`. We fix this in the next task; do not commit yet.

---

## Task 4: Update domain model + repository interface + impl

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/domain/model/Episode.kt`
- Modify: `app/src/main/java/com/example/mypodcast/domain/repository/EpisodeRepository.kt`
- Modify: `app/src/main/java/com/example/mypodcast/data/repository/EpisodeRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/mypodcast/data/repository/LibraryRepositoryImpl.kt`

- [ ] **Step 1: Add `isPlayed` to the domain `Episode` model**

Replace `app/src/main/java/com/example/mypodcast/domain/model/Episode.kt` with:

```kotlin
package com.example.mypodcast.domain.model

data class Episode(
    val guid: String,
    val podcastId: Long,
    val title: String,
    val description: String?,
    val audioUrl: String,
    val artworkUrl: String?,
    val publishedAt: Long,
    val durationSeconds: Int,
    val fileSizeBytes: Long,
    val playbackPosition: Long = 0L,
    val isPlayed: Boolean = false
)
```

- [ ] **Step 2: Rename the repository method**

Replace `app/src/main/java/com/example/mypodcast/domain/repository/EpisodeRepository.kt` with:

```kotlin
package com.example.mypodcast.domain.repository

import com.example.mypodcast.domain.model.Episode
import kotlinx.coroutines.flow.Flow

interface EpisodeRepository {
    suspend fun fetchEpisodesForPodcast(podcastId: Long, feedUrl: String): List<Episode>
    fun observeEpisodesForPodcast(podcastId: Long): Flow<List<Episode>>
    suspend fun getEpisode(guid: String): Episode?
    suspend fun updateProgress(guid: String, positionMs: Long, isPlayed: Boolean)
}
```

- [ ] **Step 3: Update `EpisodeRepositoryImpl` — RSS merge fix and mapper update**

Replace `app/src/main/java/com/example/mypodcast/data/repository/EpisodeRepositoryImpl.kt` with:

```kotlin
package com.example.mypodcast.data.repository

import com.example.mypodcast.data.local.dao.EpisodeDao
import com.example.mypodcast.data.local.dao.PodcastDao
import com.example.mypodcast.data.local.entity.EpisodeEntity
import com.example.mypodcast.data.remote.rss.RssParser
import com.example.mypodcast.data.remote.rss.model.RssEpisode
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.repository.EpisodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class EpisodeRepositoryImpl @Inject constructor(
    private val rssParser: RssParser,
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao
) : EpisodeRepository {

    override suspend fun fetchEpisodesForPodcast(podcastId: Long, feedUrl: String): List<Episode> {
        val feed = rssParser.parse(feedUrl)
        val parsed = feed.episodes.map { it.toEntity(podcastId) }

        // Preserve any saved progress before upsert — RSS doesn't carry it.
        val priorByGuid = episodeDao.getByGuids(parsed.map { it.guid }).associateBy { it.guid }
        val merged = parsed.map { e ->
            val prior = priorByGuid[e.guid]
            if (prior == null) e
            else e.copy(playbackPosition = prior.playbackPosition, isPlayed = prior.isPlayed)
        }
        episodeDao.upsertAll(merged)

        // Backfill the podcast's description from the RSS feed channel-level
        // <description> / <itunes:summary>. iTunes Search doesn't return one,
        // so this is the only way to populate it.
        val feedDescription = feed.description?.takeIf { it.isNotBlank() }
        val existingPodcast = podcastDao.getById(podcastId)
        if (feedDescription != null && existingPodcast != null && existingPodcast.description.isNullOrBlank()) {
            podcastDao.upsert(existingPodcast.copy(description = feedDescription))
        }

        val podcastArtwork = existingPodcast?.artworkUrl
        return merged.map { it.toDomain(podcastArtwork) }
    }

    override fun observeEpisodesForPodcast(podcastId: Long): Flow<List<Episode>> {
        val podcastArtworkFlow = podcastDao.observeByIds(listOf(podcastId))
            .map { it.firstOrNull()?.artworkUrl }
        return episodeDao.observeByPodcast(podcastId).combine(podcastArtworkFlow) { entities, artwork ->
            entities.map { it.toDomain(artwork) }
        }
    }

    override suspend fun getEpisode(guid: String): Episode? {
        val entity = episodeDao.getByGuid(guid) ?: return null
        val podcastArtwork = podcastDao.getById(entity.podcastId)?.artworkUrl
        return entity.toDomain(podcastArtwork)
    }

    override suspend fun updateProgress(guid: String, positionMs: Long, isPlayed: Boolean) =
        episodeDao.updateProgress(guid, positionMs, isPlayed)

    private fun RssEpisode.toEntity(podcastId: Long) = EpisodeEntity(
        guid = guid,
        podcastId = podcastId,
        title = title,
        description = description,
        audioUrl = audioUrl,
        artworkUrl = artworkUrl,
        publishedAt = publishedAt,
        durationSeconds = durationSeconds,
        fileSizeBytes = fileSizeBytes
    )

    private fun EpisodeEntity.toDomain(podcastArtworkFallback: String?) = Episode(
        guid = guid,
        podcastId = podcastId,
        title = title,
        description = description,
        audioUrl = audioUrl,
        artworkUrl = artworkUrl?.takeIf { it.isNotBlank() } ?: podcastArtworkFallback,
        publishedAt = publishedAt,
        durationSeconds = durationSeconds,
        fileSizeBytes = fileSizeBytes,
        playbackPosition = playbackPosition,
        isPlayed = isPlayed
    )
}
```

- [ ] **Step 4: Map `isPlayed` in `LibraryRepositoryImpl.observeDownloadedEpisodes`**

In `app/src/main/java/com/example/mypodcast/data/repository/LibraryRepositoryImpl.kt`, find the block (lines ~74-87) that constructs `Episode(...)` inside `observeDownloadedEpisodes`. The existing code ends with `playbackPosition = e.playbackPosition`. Add `isPlayed = e.isPlayed` after it:

```kotlin
                    entities.map { e ->
                        Episode(
                            guid = e.guid,
                            podcastId = e.podcastId,
                            title = e.title,
                            description = e.description,
                            audioUrl = pathByGuid[e.guid] ?: e.audioUrl,
                            artworkUrl = e.artworkUrl?.takeIf { it.isNotBlank() }
                                ?: artworkByPodcastId[e.podcastId],
                            publishedAt = e.publishedAt,
                            durationSeconds = e.durationSeconds,
                            fileSizeBytes = e.fileSizeBytes,
                            playbackPosition = e.playbackPosition,
                            isPlayed = e.isPlayed
                        )
                    }
```

- [ ] **Step 5: Verify build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/data/local/dao/EpisodeDao.kt \
        app/src/main/java/com/example/mypodcast/domain/model/Episode.kt \
        app/src/main/java/com/example/mypodcast/domain/repository/EpisodeRepository.kt \
        app/src/main/java/com/example/mypodcast/data/repository/EpisodeRepositoryImpl.kt \
        app/src/main/java/com/example/mypodcast/data/repository/LibraryRepositoryImpl.kt
git commit -m "Wire isPlayed through DAO, domain model, and mappers; preserve progress on RSS refresh"
```

---

## Task 5: Persist progress from `PlayerController`

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/media/PlayerController.kt`

- [ ] **Step 1: Replace `PlayerController.kt` with the version that persists progress**

Replace `app/src/main/java/com/example/mypodcast/media/PlayerController.kt` with:

```kotlin
package com.example.mypodcast.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.repository.EpisodeRepository
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sleepTimerManager: SleepTimerManager,
    private val episodeRepository: Lazy<EpisodeRepository>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            true
        )
        .build()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState

    private var currentEpisode: Episode? = null
    private var positionJob: Job? = null
    private var ticksSinceLastSave = 0

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _playerState.update { it.copy(
                    isBuffering = state == Player.STATE_BUFFERING,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                )}
                if (state == Player.STATE_READY) startPositionUpdates()
                if (state == Player.STATE_ENDED) {
                    stopPositionUpdates()
                    persistEnded()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                    persistCurrent()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _playerState.update { it.copy(error = error.message) }
            }
        })

        scope.launch {
            sleepTimerManager.remainingMs.collect { remaining ->
                _playerState.update { it.copy(sleepTimerRemainingMs = remaining) }
            }
        }
    }

    fun playEpisode(episode: Episode) {
        loadEpisode(episode, autoPlay = true)
    }

    fun prepareEpisode(episode: Episode) {
        // No-op when this episode is already loaded — preserves current
        // play/pause state (and position) when the user re-opens the same
        // episode from a list row.
        if (currentEpisode?.guid == episode.guid) {
            _playerState.update { it.copy(episode = episode, previewEpisode = null, error = null) }
            return
        }
        _playerState.update { it.copy(previewEpisode = episode, error = null) }
    }

    private fun loadEpisode(episode: Episode, autoPlay: Boolean) {
        // Persist outgoing episode's position before swapping.
        persistCurrent()

        currentEpisode = episode
        val mediaItem = MediaItem.Builder()
            .setUri(episode.audioUrl)
            .setMediaId(episode.guid)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setArtworkUri(episode.artworkUrl?.let { Uri.parse(it) })
                    .build()
            )
            .build()
        exoPlayer.setMediaItem(mediaItem)
        if (episode.playbackPosition > 0L) exoPlayer.seekTo(episode.playbackPosition)
        exoPlayer.prepare()
        if (autoPlay) {
            startPlaybackService()
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
        _playerState.update {
            it.copy(
                episode = episode,
                previewEpisode = null,
                positionMs = episode.playbackPosition,
                durationMs = episode.durationSeconds.takeIf { seconds -> seconds > 0 }?.times(1000L) ?: 0L,
                error = null
            )
        }
    }

    fun pause() = exoPlayer.pause()
    fun resume() {
        startPlaybackService()
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            exoPlayer.seekTo(0L)
            _playerState.update { it.copy(positionMs = 0L, error = null) }
        }
        exoPlayer.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _playerState.update { it.copy(positionMs = positionMs) }
        // A non-zero seek on a played episode means the user is re-listening:
        // clear isPlayed so the checkmark goes away.
        val episode = currentEpisode ?: return
        val clearPlayed = episode.isPlayed && positionMs > 0L
        if (clearPlayed) {
            currentEpisode = episode.copy(isPlayed = false, playbackPosition = positionMs)
            _playerState.update { it.copy(episode = currentEpisode) }
        }
        persistProgress(episode.guid, positionMs, isPlayed = if (clearPlayed) false else episode.isPlayed)
    }

    fun skipForward(seconds: Int = 15) = seekTo((exoPlayer.currentPosition + seconds * 1000L).coerceAtMost(exoPlayer.duration))
    fun skipBack(seconds: Int = 15) = seekTo((exoPlayer.currentPosition - seconds * 1000L).coerceAtLeast(0L))

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
        _playerState.update { it.copy(speed = speed) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerManager.start(minutes) { exoPlayer.pause() }
    }

    fun cancelSleepTimer() = sleepTimerManager.cancel()

    fun release() {
        // Block briefly so progress is persisted even when the process is
        // about to die. NonCancellable so the surrounding scope tearing down
        // doesn't drop the write.
        runBlocking {
            withContext(NonCancellable) { persistCurrentNow() }
        }
        positionJob?.cancel()
        exoPlayer.release()
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        ticksSinceLastSave = 0
        positionJob = scope.launch {
            while (true) {
                _playerState.update { it.copy(
                    positionMs = exoPlayer.currentPosition,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                )}
                ticksSinceLastSave++
                if (ticksSinceLastSave >= SAVE_EVERY_N_TICKS) {
                    ticksSinceLastSave = 0
                    persistCurrent()
                }
                delay(500L)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        ticksSinceLastSave = 0
    }

    private fun startPlaybackService() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, PlaybackService::class.java)
        )
    }

    private fun persistCurrent() {
        val episode = currentEpisode ?: return
        val positionMs = exoPlayer.currentPosition
        if (positionMs < MIN_PERSIST_POSITION_MS) return
        persistProgress(episode.guid, positionMs, episode.isPlayed)
    }

    private suspend fun persistCurrentNow() {
        val episode = currentEpisode ?: return
        val positionMs = exoPlayer.currentPosition
        if (positionMs < MIN_PERSIST_POSITION_MS) return
        episodeRepository.get().updateProgress(episode.guid, positionMs, episode.isPlayed)
    }

    private fun persistEnded() {
        val episode = currentEpisode ?: return
        currentEpisode = episode.copy(playbackPosition = 0L, isPlayed = true)
        _playerState.update { it.copy(episode = currentEpisode, positionMs = 0L) }
        persistProgress(episode.guid, positionMs = 0L, isPlayed = true)
    }

    private fun persistProgress(guid: String, positionMs: Long, isPlayed: Boolean) {
        scope.launch(Dispatchers.IO) {
            episodeRepository.get().updateProgress(guid, positionMs, isPlayed)
        }
    }

    companion object {
        // Save every 10 * 500 ms = 5 s while playing.
        private const val SAVE_EVERY_N_TICKS = 10
        // Avoid stomping a fresh seek-to-0 with a stale earlier value.
        private const val MIN_PERSIST_POSITION_MS = 1_000L
    }
}
```

Notes for the engineer:
- **Why `Lazy<EpisodeRepository>`:** `PlayerRepositoryImpl` already binds `PlayerController`, and `EpisodeRepositoryImpl` injects `EpisodeDao`. Hilt resolves both fine, but using `Lazy` keeps `PlayerController` initialization cheap and avoids any future cycle if `EpisodeRepository` ever depends on a player-side dependency.
- **`STATE_ENDED` writing 0:** matches the existing `resume()` code that already calls `seekTo(0)` on resume-from-ended.
- **`runBlocking` in `release()`:** unusual but acceptable here — `release()` is called from a teardown path, not the UI loop. Keep it bounded by the suspending DAO call (Room IO), which is fast.

- [ ] **Step 2: Build the app**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/media/PlayerController.kt
git commit -m "Persist episode progress from PlayerController at six trigger points"
```

---

## Task 6: Show played-checkmark in `EpisodeListItem`

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/components/EpisodeListItem.kt`

The existing `Icons.Default.CheckCircle` is already used as the **downloaded** indicator, so we use `Icons.Filled.Done` for played.

- [ ] **Step 1: Add the played indicator next to the date row**

In `app/src/main/java/com/example/mypodcast/ui/components/EpisodeListItem.kt`:

1. Add this import in the alphabetically-correct spot (after `import androidx.compose.material.icons.filled.Delete`):

```kotlin
import androidx.compose.material.icons.filled.Done
```

2. Replace the inner `Column` (the one with the title + date row, currently lines 69-81) with:

```kotlin
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (episode.isPlayed) {
                    Icon(
                        Icons.Filled.Done,
                        contentDescription = "Played",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = formatDate(episode.publishedAt) + " · " + formatDuration(episode.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
```

(`Row`, `Spacer`, `Modifier.width`, `Alignment`, and `Icon` are already imported in this file.)

- [ ] **Step 2: Build the app**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/ui/components/EpisodeListItem.kt
git commit -m "Show played-checkmark on finished episodes in list"
```

---

## Task 7: Fix `PlayerViewModelTest` `episode()` helper

**Files:**
- Modify: `app/src/test/java/com/example/mypodcast/ui/player/PlayerViewModelTest.kt`

The existing helper omits `isPlayed`, which is fine because the new field has a default — but we ought to confirm tests still build and pass.

- [ ] **Step 1: Run the existing unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.ui.player.PlayerViewModelTest"`
Expected: PASS, 2 tests run.

If they fail because of the `isPlayed` field, modify the `episode()` helper at line 51 to add `isPlayed = false`. Otherwise no change is needed; this task is a verification.

- [ ] **Step 2: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, all existing tests green.

If any of `MainScreenViewModelTest`, `DownloadEpisodeUseCaseTest`, or `PlayerUiFormattersTest` fail because they reference an `Episode` constructor that's now ambiguous, just add `isPlayed = false` to those construction sites. (They use defaults today, so this is unlikely.)

---

## Task 8: Manual on-device verification

Per repo memory: "Verify UI changes on device, not just build."

Build, install, and verify each scenario.

- [ ] **Step 1: Build a debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL; APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 2: Install on the connected device**

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: `Success`.

- [ ] **Step 3: Verify resume — streamed episode**

1. Open the app → search → pick a podcast → tap an episode → play for ~30 s.
2. Force-stop the app (`adb shell am force-stop com.example.mypodcast`).
3. Re-open the app → re-open the same episode.

Expected: playback resumes near 30 s (not from 0).

- [ ] **Step 4: Verify resume — downloaded episode**

1. From the podcast detail screen, download an episode.
2. Open it from Library → Downloads → play for ~20 s → pause.
3. Force-stop the app.
4. Re-open → Library → Downloads → tap the same episode.

Expected: playback resumes near 20 s.

- [ ] **Step 5: Verify played indicator**

1. Skip an episode to ~5 s before its end (use the seek bar) and let it finish.
2. Return to the episode list for that podcast.

Expected: the episode shows a small checkmark before the date/duration line.

- [ ] **Step 6: Verify reset on end**

After Step 5, tap the same episode and tap play.

Expected: it starts from 0 (not from the end).

- [ ] **Step 7: Verify RSS refresh preserves progress**

1. Pick an episode, play to ~15 s, pause.
2. From the podcast detail screen, pull-to-refresh.
3. Tap the same episode again.

Expected: position is still ~15 s (not 0).

- [ ] **Step 8: Verify replay clears the checkmark**

1. After Step 6, seek the played episode to the middle (e.g., 50%).
2. Pause.
3. Return to the episode list.

Expected: the checkmark is gone, position is preserved at the seek point.

- [ ] **Step 9: Final commit if any test-only fixes were needed**

If Task 7 required changes:

```bash
git add app/src/test/java/com/example/mypodcast/ui/player/PlayerViewModelTest.kt
git commit -m "Update tests for Episode.isPlayed field"
```

---

## Done criteria

- All 8 tasks complete.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` is green.
- All eight on-device scenarios in Task 8 behave as described.
