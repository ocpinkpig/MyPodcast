# Backup and Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Export all user library state (subscriptions, downloads list, moments, favorites, queue, play history) to a JSON file via the system file picker, and import it back after a reinstall with background re-download of missing episode audio.

**Architecture:** A `LibraryBackup` domain model is serialized to/from JSON by `BackupSerializer` (Gson). `BackupRepositoryImpl` builds a backup from the Room DAOs and merges one back in a single transaction (merge-only, never deletes). A Hilt `RestoreDownloadsWorker` (WorkManager) re-downloads missing audio using a shared `EpisodeAudioDownloader` extracted from `DownloadEpisodeUseCase`, reading its pending list from a JSON file (`RestoreQueueStore`) so it survives process death and avoids WorkManager's 10 KB `Data` limit. UI is a new `BackupRestoreScreen` opened from the Library overflow menu; SAF (`ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT`) provides phone storage and Google Drive.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Navigation 3, Hilt, Room, Gson (existing dep), WorkManager + hilt-work (new deps), Robolectric/JUnit4 for tests.

**Spec:** `docs/superpowers/specs/2026-07-09-backup-restore-design.md`

**Conscious deviations from spec (approved intent unchanged):**
- The spec's example JSON showed an `appVersion` field. `buildConfig` is disabled in this project, so it is omitted; `formatVersion` alone governs compatibility.
- The worker writes `downloaded_episodes` rows via `DownloadedEpisodeDao.insert` directly rather than `LibraryRepository.saveDownload` (which is a one-line wrapper around the same insert) to keep the worker's dependency list small. Behavior is identical.

**Conventions used throughout:**
- Unit tests live in `app/src/test/...`, run with `./gradlew :app:testDebugUnitTest --tests <Class>`. DAO/repository tests use Robolectric + in-memory Room exactly like `app/src/test/java/com/example/mypodcast/data/local/dao/EpisodeDaoTest.kt` (`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34])`).
- Compile check: `./gradlew :app:compileDebugKotlin`.
- Commit after every green task.

## File Structure

```
app/src/main/java/com/example/mypodcast/
  domain/model/backup/LibraryBackup.kt        (new: backup models, summary, result, exceptions)
  domain/repository/BackupRepository.kt       (new: interface)
  domain/usecase/backup/BackupUseCases.kt     (new: Export/Inspect/Import use cases)
  data/backup/BackupSerializer.kt             (new: JSON read/write + validation)
  data/backup/RestoreQueueStore.kt            (new: persisted pending-restore list)
  data/backup/BackupRepositoryImpl.kt         (new: build + summarize + import merge)
  data/download/EpisodeAudioDownloader.kt     (new: streaming download extracted from use case)
  work/RestoreDownloadsWorker.kt              (new: background re-download worker)
  work/RestoreScheduler.kt                    (new: interface + WorkManager impl + RestoreProgress)
  ui/backup/BackupRestoreViewModel.kt         (new)
  ui/backup/BackupRestoreScreen.kt            (new)
  domain/usecase/episode/DownloadEpisodeUseCase.kt  (modify: delegate to downloader)
  data/local/dao/{Podcast,Episode,Subscription,SavedMoment}Dao.kt  (modify: backup queries)
  di/RepositoryModule.kt                      (modify: bind BackupRepository)
  di/WorkModule.kt                            (new: bind RestoreScheduler)
  NavigationKeys.kt, Navigation.kt            (modify: BackupRestoreNavKey + entry)
  ui/library/LibraryScreen.kt                 (modify: overflow DropdownMenu)
  MyPodcastApplication.kt                     (modify: WorkManager Configuration.Provider)
app/src/main/AndroidManifest.xml              (modify: WorkManager init removal, FGS permission)
gradle/libs.versions.toml, app/build.gradle.kts  (modify: work + hilt-work deps)
```

---

### Task 1: WorkManager + hilt-work dependencies and app wiring

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/example/mypodcast/MyPodcastApplication.kt`

No unit test for build wiring; verification is a successful compile plus the existing suite.

- [ ] **Step 1: Add versions and libraries to the catalog**

In `gradle/libs.versions.toml`, add to `[versions]` (after `gson = "2.11.0"`):

```toml
work = "2.10.0"
androidxHiltWork = "1.2.0"
```

Add to `[libraries]` (after the `gson` entry):

```toml
work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
work-testing = { module = "androidx.work:work-testing", version.ref = "work" }
hilt-work = { module = "androidx.hilt:hilt-work", version.ref = "androidxHiltWork" }
androidx-hilt-compiler = { module = "androidx.hilt:hilt-compiler", version.ref = "androidxHiltWork" }
```

- [ ] **Step 2: Add dependencies to the app module**

In `app/build.gradle.kts` `dependencies` block, after the `// On-device transcription` lines:

```kotlin
  // WorkManager (backup restore downloads)
  implementation(libs.work.runtime.ktx)
  implementation(libs.hilt.work)
  ksp(libs.androidx.hilt.compiler)
  testImplementation(libs.work.testing)
```

- [ ] **Step 3: Wire Hilt worker factory into the Application**

Replace `app/src/main/java/com/example/mypodcast/MyPodcastApplication.kt` with:

```kotlin
package com.example.mypodcast

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.mypodcast.domain.repository.LibraryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MyPodcastApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun libraryRepository(): LibraryRepository
    }

    override fun onCreate() {
        super.onCreate()
        val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { entryPoint.libraryRepository().cleanupOrphanedFiles() }
        }
    }
}
```

- [ ] **Step 4: Manifest — disable default WorkManager init, add foreground-service bits**

In `app/src/main/AndroidManifest.xml`:

1. Add the tools namespace to the root element: `<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">`
2. Add after the existing `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission:

```xml
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

3. Inside `<application>`, after the `PlaybackService` `<service>` element, add:

```xml
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>

        <service
            android:name="androidx.work.impl.foreground.SystemForegroundService"
            android:foregroundServiceType="dataSync"
            tools:node="merge" />
```

- [ ] **Step 5: Verify compile and existing tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all existing tests pass.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/example/mypodcast/MyPodcastApplication.kt
git commit -m "Add WorkManager and hilt-work wiring for restore downloads"
```

---

### Task 2: DAO queries for export and merge

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/data/local/dao/EpisodeDao.kt`
- Modify: `app/src/main/java/com/example/mypodcast/data/local/dao/PodcastDao.kt`
- Modify: `app/src/main/java/com/example/mypodcast/data/local/dao/SubscriptionDao.kt`
- Modify: `app/src/main/java/com/example/mypodcast/data/local/dao/SavedMomentDao.kt`
- Test: `app/src/test/java/com/example/mypodcast/data/local/dao/BackupDaoTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/example/mypodcast/data/local/dao/BackupDaoTest.kt`:

```kotlin
package com.example.mypodcast.data.local.dao

import androidx.room.Room
import com.example.mypodcast.data.local.AppDatabase
import com.example.mypodcast.data.local.entity.EpisodeEntity
import com.example.mypodcast.data.local.entity.PodcastEntity
import com.example.mypodcast.data.local.entity.QueueItemEntity
import com.example.mypodcast.data.local.entity.SavedMomentEntity
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
        db.subscriptionDao().subscribe(com.example.mypodcast.data.local.entity.SubscriptionEntity(podcastId = 1L, subscribedAt = 42L))

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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.data.local.dao.BackupDaoTest`
Expected: FAIL to compile — unresolved references `getAllWithUserState`, `insertAllIgnore`, `mergeUserState`, `getAll`, `exists`.

- [ ] **Step 3: Add the DAO methods**

In `EpisodeDao.kt`, add inside the interface:

```kotlin
    @Query(
        """
        SELECT DISTINCT episodes.* FROM episodes
        LEFT JOIN queue_items ON queue_items.episodeGuid = episodes.guid
        LEFT JOIN saved_moments ON saved_moments.episodeGuid = episodes.guid
        LEFT JOIN downloaded_episodes ON downloaded_episodes.episodeGuid = episodes.guid
        WHERE episodes.isFavorite = 1
            OR episodes.isPlayed = 1
            OR episodes.playbackPosition > 0
            OR episodes.lastPlayedAt > 0
            OR queue_items.episodeGuid IS NOT NULL
            OR saved_moments.episodeGuid IS NOT NULL
            OR downloaded_episodes.episodeGuid IS NOT NULL
        """
    )
    suspend fun getAllWithUserState(): List<EpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(episodes: List<EpisodeEntity>)

    @Query(
        """
        UPDATE episodes SET
            isFavorite = CASE WHEN :isFavorite THEN 1 ELSE isFavorite END,
            isPlayed = CASE WHEN :isPlayed THEN 1 ELSE isPlayed END,
            playbackPosition = MAX(playbackPosition, :playbackPosition),
            lastPlayedAt = MAX(lastPlayedAt, :lastPlayedAt)
        WHERE guid = :guid
        """
    )
    suspend fun mergeUserState(
        guid: String,
        isFavorite: Boolean,
        isPlayed: Boolean,
        playbackPosition: Long,
        lastPlayedAt: Long
    )
```

Add imports `androidx.room.Insert` and `androidx.room.OnConflictStrategy` to `EpisodeDao.kt`.

In `PodcastDao.kt`, add:

```kotlin
    @Query("SELECT * FROM podcasts WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<PodcastEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(podcasts: List<PodcastEntity>)
```

Add imports `androidx.room.Insert` and `androidx.room.OnConflictStrategy` to `PodcastDao.kt`.

In `SubscriptionDao.kt`, add:

```kotlin
    @Query("SELECT * FROM subscriptions")
    suspend fun getAll(): List<SubscriptionEntity>
```

In `SavedMomentDao.kt`, add:

```kotlin
    @Query("SELECT * FROM saved_moments")
    suspend fun getAll(): List<SavedMomentEntity>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM saved_moments " +
            "WHERE episodeGuid = :episodeGuid AND positionMs = :positionMs AND createdAt = :createdAt)"
    )
    suspend fun exists(episodeGuid: String, positionMs: Long, createdAt: Long): Boolean
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.data.local.dao.BackupDaoTest`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/data/local/dao app/src/test/java/com/example/mypodcast/data/local/dao/BackupDaoTest.kt
git commit -m "Add DAO queries for backup export and import merge"
```

---

### Task 3: Backup domain models and JSON serializer

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/domain/model/backup/LibraryBackup.kt`
- Create: `app/src/main/java/com/example/mypodcast/data/backup/BackupSerializer.kt`
- Test: `app/src/test/java/com/example/mypodcast/data/backup/BackupSerializerTest.kt`

- [ ] **Step 1: Create the domain models** (pure data, no test needed on their own)

Create `app/src/main/java/com/example/mypodcast/domain/model/backup/LibraryBackup.kt`:

```kotlin
package com.example.mypodcast.domain.model.backup

data class LibraryBackup(
    val formatVersion: Int,
    val exportedAt: Long,
    val podcasts: List<BackupPodcast>,
    val subscriptions: List<BackupSubscription>,
    val episodes: List<BackupEpisode>,
    val downloads: List<BackupDownload>,
    val queue: List<BackupQueueItem>,
    val moments: List<BackupMoment>
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

data class BackupPodcast(
    val id: Long,
    val title: String,
    val artworkUrl: String,
    val artistName: String,
    val feedUrl: String,
    val description: String?,
    val genres: String,
    val episodeCount: Int,
    val language: String?
)

data class BackupSubscription(val podcastId: Long, val subscribedAt: Long)

data class BackupEpisode(
    val guid: String,
    val podcastId: Long,
    val title: String,
    val description: String?,
    val audioUrl: String,
    val artworkUrl: String?,
    val publishedAt: Long,
    val durationSeconds: Int,
    val fileSizeBytes: Long,
    val playbackPosition: Long,
    val isPlayed: Boolean,
    val isFavorite: Boolean,
    val lastPlayedAt: Long,
    val transcriptUrl: String?,
    val transcriptType: String?
)

data class BackupDownload(val episodeGuid: String, val podcastId: Long, val downloadedAt: Long)

data class BackupQueueItem(val episodeGuid: String, val position: Int)

data class BackupMoment(
    val episodeGuid: String,
    val positionMs: Long,
    val clipStartMs: Long,
    val clipEndMs: Long,
    val transcriptText: String?,
    val createdAt: Long
)

data class BackupSummary(
    val exportedAt: Long,
    val shows: Int,
    val favorites: Int,
    val downloadsToRestore: Int,
    val moments: Int,
    val queueItems: Int
)

data class FeedTarget(val podcastId: Long, val feedUrl: String)

data class ImportResult(
    val downloadsToRestore: List<String>,
    val feedTargets: List<FeedTarget>
)

/** The file is not parseable as a MyPodcast backup. */
class InvalidBackupException(message: String) : Exception(message)

/** The backup was written by a newer app with an unknown format. */
class UnsupportedBackupVersionException(val version: Int) :
    Exception("Unsupported backup format version $version")
```

- [ ] **Step 2: Write the failing serializer tests**

Create `app/src/test/java/com/example/mypodcast/data/backup/BackupSerializerTest.kt`:

```kotlin
package com.example.mypodcast.data.backup

import com.example.mypodcast.domain.model.backup.BackupDownload
import com.example.mypodcast.domain.model.backup.BackupEpisode
import com.example.mypodcast.domain.model.backup.BackupMoment
import com.example.mypodcast.domain.model.backup.BackupPodcast
import com.example.mypodcast.domain.model.backup.BackupQueueItem
import com.example.mypodcast.domain.model.backup.BackupSubscription
import com.example.mypodcast.domain.model.backup.InvalidBackupException
import com.example.mypodcast.domain.model.backup.LibraryBackup
import com.example.mypodcast.domain.model.backup.UnsupportedBackupVersionException
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupSerializerTest {
    private val serializer = BackupSerializer()

    private fun sampleBackup() = LibraryBackup(
        formatVersion = LibraryBackup.FORMAT_VERSION,
        exportedAt = 123L,
        podcasts = listOf(
            BackupPodcast(
                id = 1L,
                title = "Show",
                artworkUrl = "https://art.png",
                artistName = "Artist",
                feedUrl = "https://feed.xml",
                description = "desc",
                genres = "Tech",
                episodeCount = 3,
                language = "en-US"
            )
        ),
        subscriptions = listOf(BackupSubscription(1L, 42L)),
        episodes = listOf(
            BackupEpisode(
                guid = "e1",
                podcastId = 1L,
                title = "Episode",
                description = null,
                audioUrl = "https://audio.mp3",
                artworkUrl = null,
                publishedAt = 10L,
                durationSeconds = 60,
                fileSizeBytes = 1_024L,
                playbackPosition = 5L,
                isPlayed = true,
                isFavorite = true,
                lastPlayedAt = 99L,
                transcriptUrl = null,
                transcriptType = null
            )
        ),
        downloads = listOf(BackupDownload("e1", 1L, 7L)),
        queue = listOf(BackupQueueItem("e1", 0)),
        moments = listOf(BackupMoment("e1", 1_000L, 0L, 2_000L, "text", 5L))
    )

    private fun read(json: String) = serializer.read(ByteArrayInputStream(json.toByteArray()))

    @Test
    fun roundTrip_preservesEverything() {
        val out = ByteArrayOutputStream()
        serializer.write(sampleBackup(), out)

        val parsed = serializer.read(ByteArrayInputStream(out.toByteArray()))

        assertEquals(sampleBackup(), parsed)
    }

    @Test(expected = InvalidBackupException::class)
    fun read_rejectsNonJson() {
        read("this is not json {{{")
    }

    @Test(expected = InvalidBackupException::class)
    fun read_rejectsJsonWithoutFormatVersion() {
        read("""{"exportedAt": 1}""")
    }

    @Test(expected = InvalidBackupException::class)
    fun read_rejectsEpisodeMissingGuid() {
        read(
            """{"formatVersion": 1, "episodes": [{"podcastId": 1, "title": "t", "audioUrl": "u"}]}"""
        )
    }

    @Test
    fun read_rejectsNewerFormatVersion() {
        val error = runCatching { read("""{"formatVersion": 99}""") }.exceptionOrNull()

        assertTrue(error is UnsupportedBackupVersionException)
        assertEquals(99, (error as UnsupportedBackupVersionException).version)
    }

    @Test
    fun read_defaultsMissingSectionsToEmpty() {
        val parsed = read("""{"formatVersion": 1, "exportedAt": 5}""")

        assertEquals(0, parsed.podcasts.size)
        assertEquals(0, parsed.subscriptions.size)
        assertEquals(0, parsed.episodes.size)
        assertEquals(0, parsed.downloads.size)
        assertEquals(0, parsed.queue.size)
        assertEquals(0, parsed.moments.size)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.data.backup.BackupSerializerTest`
Expected: FAIL to compile — `BackupSerializer` not defined.

- [ ] **Step 4: Implement the serializer**

Gson does not enforce Kotlin null-safety, so parsing goes through an all-nullable `Raw*` mirror and is validated field-by-field before constructing the strict domain model.

Create `app/src/main/java/com/example/mypodcast/data/backup/BackupSerializer.kt`:

```kotlin
package com.example.mypodcast.data.backup

import com.example.mypodcast.domain.model.backup.BackupDownload
import com.example.mypodcast.domain.model.backup.BackupEpisode
import com.example.mypodcast.domain.model.backup.BackupMoment
import com.example.mypodcast.domain.model.backup.BackupPodcast
import com.example.mypodcast.domain.model.backup.BackupQueueItem
import com.example.mypodcast.domain.model.backup.BackupSubscription
import com.example.mypodcast.domain.model.backup.InvalidBackupException
import com.example.mypodcast.domain.model.backup.LibraryBackup
import com.example.mypodcast.domain.model.backup.UnsupportedBackupVersionException
import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject

class BackupSerializer @Inject constructor() {
    private val gson = Gson()

    fun write(backup: LibraryBackup, output: OutputStream) {
        OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
            gson.toJson(backup, writer)
        }
    }

    fun read(input: InputStream): LibraryBackup {
        val raw = try {
            InputStreamReader(input, Charsets.UTF_8).use { reader ->
                gson.fromJson(reader, RawBackup::class.java)
            }
        } catch (e: JsonSyntaxException) {
            throw InvalidBackupException("Not a MyPodcast backup file")
        } catch (e: JsonIOException) {
            throw InvalidBackupException("Could not read the backup file")
        } ?: throw InvalidBackupException("The backup file is empty")

        val version = raw.formatVersion
            ?: throw InvalidBackupException("Not a MyPodcast backup file")
        if (version > LibraryBackup.FORMAT_VERSION) {
            throw UnsupportedBackupVersionException(version)
        }

        return LibraryBackup(
            formatVersion = version,
            exportedAt = raw.exportedAt ?: 0L,
            podcasts = raw.podcasts.orEmpty().map { it.validated() },
            subscriptions = raw.subscriptions.orEmpty().map { it.validated() },
            episodes = raw.episodes.orEmpty().map { it.validated() },
            downloads = raw.downloads.orEmpty().map { it.validated() },
            queue = raw.queue.orEmpty().map { it.validated() },
            moments = raw.moments.orEmpty().map { it.validated() }
        )
    }
}

private fun missing(field: String): Nothing =
    throw InvalidBackupException("Backup entry is missing required field: $field")

private class RawBackup(
    val formatVersion: Int? = null,
    val exportedAt: Long? = null,
    val podcasts: List<RawPodcast?>? = null,
    val subscriptions: List<RawSubscription?>? = null,
    val episodes: List<RawEpisode?>? = null,
    val downloads: List<RawDownload?>? = null,
    val queue: List<RawQueueItem?>? = null,
    val moments: List<RawMoment?>? = null
)

private class RawPodcast(
    val id: Long? = null,
    val title: String? = null,
    val artworkUrl: String? = null,
    val artistName: String? = null,
    val feedUrl: String? = null,
    val description: String? = null,
    val genres: String? = null,
    val episodeCount: Int? = null,
    val language: String? = null
)

private fun RawPodcast?.validated() = BackupPodcast(
    id = this?.id ?: missing("podcast.id"),
    title = this.title ?: missing("podcast.title"),
    artworkUrl = this.artworkUrl ?: "",
    artistName = this.artistName ?: "",
    feedUrl = this.feedUrl ?: missing("podcast.feedUrl"),
    description = this.description,
    genres = this.genres ?: "",
    episodeCount = this.episodeCount ?: 0,
    language = this.language
)

private class RawSubscription(val podcastId: Long? = null, val subscribedAt: Long? = null)

private fun RawSubscription?.validated() = BackupSubscription(
    podcastId = this?.podcastId ?: missing("subscription.podcastId"),
    subscribedAt = this.subscribedAt ?: 0L
)

private class RawEpisode(
    val guid: String? = null,
    val podcastId: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val audioUrl: String? = null,
    val artworkUrl: String? = null,
    val publishedAt: Long? = null,
    val durationSeconds: Int? = null,
    val fileSizeBytes: Long? = null,
    val playbackPosition: Long? = null,
    val isPlayed: Boolean? = null,
    val isFavorite: Boolean? = null,
    val lastPlayedAt: Long? = null,
    val transcriptUrl: String? = null,
    val transcriptType: String? = null
)

private fun RawEpisode?.validated() = BackupEpisode(
    guid = this?.guid ?: missing("episode.guid"),
    podcastId = this.podcastId ?: missing("episode.podcastId"),
    title = this.title ?: missing("episode.title"),
    description = this.description,
    audioUrl = this.audioUrl ?: missing("episode.audioUrl"),
    artworkUrl = this.artworkUrl,
    publishedAt = this.publishedAt ?: 0L,
    durationSeconds = this.durationSeconds ?: 0,
    fileSizeBytes = this.fileSizeBytes ?: 0L,
    playbackPosition = this.playbackPosition ?: 0L,
    isPlayed = this.isPlayed ?: false,
    isFavorite = this.isFavorite ?: false,
    lastPlayedAt = this.lastPlayedAt ?: 0L,
    transcriptUrl = this.transcriptUrl,
    transcriptType = this.transcriptType
)

private class RawDownload(
    val episodeGuid: String? = null,
    val podcastId: Long? = null,
    val downloadedAt: Long? = null
)

private fun RawDownload?.validated() = BackupDownload(
    episodeGuid = this?.episodeGuid ?: missing("download.episodeGuid"),
    podcastId = this.podcastId ?: missing("download.podcastId"),
    downloadedAt = this.downloadedAt ?: 0L
)

private class RawQueueItem(val episodeGuid: String? = null, val position: Int? = null)

private fun RawQueueItem?.validated() = BackupQueueItem(
    episodeGuid = this?.episodeGuid ?: missing("queue.episodeGuid"),
    position = this.position ?: 0
)

private class RawMoment(
    val episodeGuid: String? = null,
    val positionMs: Long? = null,
    val clipStartMs: Long? = null,
    val clipEndMs: Long? = null,
    val transcriptText: String? = null,
    val createdAt: Long? = null
)

private fun RawMoment?.validated() = BackupMoment(
    episodeGuid = this?.episodeGuid ?: missing("moment.episodeGuid"),
    positionMs = this.positionMs ?: 0L,
    clipStartMs = this.clipStartMs ?: 0L,
    clipEndMs = this.clipEndMs ?: 0L,
    transcriptText = this.transcriptText,
    createdAt = this.createdAt ?: 0L
)
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.data.backup.BackupSerializerTest`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/domain/model/backup app/src/main/java/com/example/mypodcast/data/backup/BackupSerializer.kt app/src/test/java/com/example/mypodcast/data/backup/BackupSerializerTest.kt
git commit -m "Add backup domain models and validated JSON serializer"
```

---

### Task 4: BackupRepository — createBackup and summarize

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/domain/repository/BackupRepository.kt`
- Create: `app/src/main/java/com/example/mypodcast/data/backup/BackupRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/mypodcast/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/example/mypodcast/data/backup/BackupRepositoryImplTest.kt`

- [ ] **Step 1: Define the interface**

Create `app/src/main/java/com/example/mypodcast/domain/repository/BackupRepository.kt`:

```kotlin
package com.example.mypodcast.domain.repository

import com.example.mypodcast.domain.model.backup.BackupSummary
import com.example.mypodcast.domain.model.backup.ImportResult
import com.example.mypodcast.domain.model.backup.LibraryBackup

interface BackupRepository {
    /** Snapshot of all user library state, ready for serialization. */
    suspend fun createBackup(): LibraryBackup

    /** Counts shown in the import confirmation dialog. Skips already-downloaded episodes. */
    suspend fun summarize(backup: LibraryBackup): BackupSummary

    /** Merges the backup into the local database. Never deletes or overwrites fresher local state. */
    suspend fun import(backup: LibraryBackup): ImportResult
}
```

- [ ] **Step 2: Write the failing tests for createBackup and summarize**

Create `app/src/test/java/com/example/mypodcast/data/backup/BackupRepositoryImplTest.kt`:

```kotlin
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
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.data.backup.BackupRepositoryImplTest`
Expected: FAIL to compile — `BackupRepositoryImpl` not defined.

- [ ] **Step 4: Implement createBackup and summarize (import stub throws for now)**

Create `app/src/main/java/com/example/mypodcast/data/backup/BackupRepositoryImpl.kt`:

```kotlin
package com.example.mypodcast.data.backup

import com.example.mypodcast.data.local.AppDatabase
import com.example.mypodcast.data.local.dao.DownloadedEpisodeDao
import com.example.mypodcast.data.local.dao.EpisodeDao
import com.example.mypodcast.data.local.dao.PodcastDao
import com.example.mypodcast.data.local.dao.QueueDao
import com.example.mypodcast.data.local.dao.SavedMomentDao
import com.example.mypodcast.data.local.dao.SubscriptionDao
import com.example.mypodcast.data.local.entity.EpisodeEntity
import com.example.mypodcast.data.local.entity.PodcastEntity
import com.example.mypodcast.domain.model.backup.BackupDownload
import com.example.mypodcast.domain.model.backup.BackupEpisode
import com.example.mypodcast.domain.model.backup.BackupMoment
import com.example.mypodcast.domain.model.backup.BackupPodcast
import com.example.mypodcast.domain.model.backup.BackupQueueItem
import com.example.mypodcast.domain.model.backup.BackupSubscription
import com.example.mypodcast.domain.model.backup.BackupSummary
import com.example.mypodcast.domain.model.backup.ImportResult
import com.example.mypodcast.domain.model.backup.LibraryBackup
import com.example.mypodcast.domain.repository.BackupRepository
import javax.inject.Inject

class BackupRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val subscriptionDao: SubscriptionDao,
    private val downloadedEpisodeDao: DownloadedEpisodeDao,
    private val queueDao: QueueDao,
    private val savedMomentDao: SavedMomentDao
) : BackupRepository {

    override suspend fun createBackup(): LibraryBackup {
        val subscriptions = subscriptionDao.getAll()
        val episodes = episodeDao.getAllWithUserState()
        val downloads = downloadedEpisodeDao.getAll()
        val queue = queueDao.getQueueItemsOrdered()
        val moments = savedMomentDao.getAll()

        val podcastIds = buildSet {
            subscriptions.forEach { add(it.podcastId) }
            episodes.forEach { add(it.podcastId) }
            downloads.forEach { add(it.podcastId) }
        }
        val podcasts = if (podcastIds.isEmpty()) emptyList() else podcastDao.getByIds(podcastIds.toList())

        return LibraryBackup(
            formatVersion = LibraryBackup.FORMAT_VERSION,
            exportedAt = System.currentTimeMillis(),
            podcasts = podcasts.map { it.toBackup() },
            subscriptions = subscriptions.map { BackupSubscription(it.podcastId, it.subscribedAt) },
            episodes = episodes.map { it.toBackup() },
            downloads = downloads.map { BackupDownload(it.episodeGuid, it.podcastId, it.downloadedAt) },
            queue = queue.map { BackupQueueItem(it.episodeGuid, it.position) },
            moments = moments.map {
                BackupMoment(it.episodeGuid, it.positionMs, it.clipStartMs, it.clipEndMs, it.transcriptText, it.createdAt)
            }
        )
    }

    override suspend fun summarize(backup: LibraryBackup): BackupSummary {
        val downloadsToRestore = backup.downloads.count { downloadedEpisodeDao.getByGuid(it.episodeGuid) == null }
        return BackupSummary(
            exportedAt = backup.exportedAt,
            shows = backup.subscriptions.size,
            favorites = backup.episodes.count { it.isFavorite },
            downloadsToRestore = downloadsToRestore,
            moments = backup.moments.size,
            queueItems = backup.queue.size
        )
    }

    override suspend fun import(backup: LibraryBackup): ImportResult {
        TODO("Implemented in the next task")
    }
}

private fun PodcastEntity.toBackup() = BackupPodcast(
    id = id,
    title = title,
    artworkUrl = artworkUrl,
    artistName = artistName,
    feedUrl = feedUrl,
    description = description,
    genres = genres,
    episodeCount = episodeCount,
    language = language
)

private fun EpisodeEntity.toBackup() = BackupEpisode(
    guid = guid,
    podcastId = podcastId,
    title = title,
    description = description,
    audioUrl = audioUrl,
    artworkUrl = artworkUrl,
    publishedAt = publishedAt,
    durationSeconds = durationSeconds,
    fileSizeBytes = fileSizeBytes,
    playbackPosition = playbackPosition,
    isPlayed = isPlayed,
    isFavorite = isFavorite,
    lastPlayedAt = lastPlayedAt,
    transcriptUrl = transcriptUrl,
    transcriptType = transcriptType
)
```

- [ ] **Step 5: Bind in Hilt**

In `di/RepositoryModule.kt`, add imports `com.example.mypodcast.data.backup.BackupRepositoryImpl` and `com.example.mypodcast.domain.repository.BackupRepository`, and add inside the module:

```kotlin
    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.data.backup.BackupRepositoryImplTest`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/domain/repository/BackupRepository.kt app/src/main/java/com/example/mypodcast/data/backup/BackupRepositoryImpl.kt app/src/main/java/com/example/mypodcast/di/RepositoryModule.kt app/src/test/java/com/example/mypodcast/data/backup/BackupRepositoryImplTest.kt
git commit -m "Add BackupRepository with export snapshot and summary"
```

---

### Task 5: BackupRepository — import merge

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/data/backup/BackupRepositoryImpl.kt`
- Test: `app/src/test/java/com/example/mypodcast/data/backup/BackupRepositoryImplTest.kt`

- [ ] **Step 1: Write the failing import tests**

Add to `BackupRepositoryImplTest.kt`:

```kotlin
    @Test
    fun import_intoEmptyDatabase_restoresEverything() = runTest {
        seedLibrary()
        val backup = repository.createBackup()

        db.clearAllTables()
        val result = repository.import(backup)

        assertEquals(setOf("fav", "downloaded"), db.episodeDao().getAllWithUserState().map { it.guid }.toSet())
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.data.backup.BackupRepositoryImplTest`
Expected: New tests FAIL with `NotImplementedError` from the `import` stub.

- [ ] **Step 3: Implement import**

Replace the `import` stub in `BackupRepositoryImpl.kt` with (also add imports `androidx.room.withTransaction`, `com.example.mypodcast.data.local.entity.QueueItemEntity`, `com.example.mypodcast.data.local.entity.SavedMomentEntity`, `com.example.mypodcast.data.local.entity.SubscriptionEntity`, `com.example.mypodcast.domain.model.backup.FeedTarget`):

```kotlin
    override suspend fun import(backup: LibraryBackup): ImportResult = db.withTransaction {
        podcastDao.insertAllIgnore(backup.podcasts.map { it.toEntity() })

        backup.subscriptions.forEach {
            subscriptionDao.subscribe(SubscriptionEntity(podcastId = it.podcastId, subscribedAt = it.subscribedAt))
        }

        // Episodes whose podcast is unknown even after the podcast merge would
        // violate the FK on episodes.podcastId; drop them instead of failing.
        val referencedIds = backup.episodes.map { it.podcastId }.distinct()
        val knownPodcastIds = if (referencedIds.isEmpty()) emptySet()
        else podcastDao.getByIds(referencedIds).map { it.id }.toSet()
        val importableEpisodes = backup.episodes.filter { it.podcastId in knownPodcastIds }

        val existingGuids = importableEpisodes.map { it.guid }
            .chunked(500)
            .flatMap { episodeDao.getByGuids(it) }
            .map { it.guid }
            .toSet()
        episodeDao.insertAllIgnore(importableEpisodes.filter { it.guid !in existingGuids }.map { it.toEntity() })
        importableEpisodes.filter { it.guid in existingGuids }.forEach {
            episodeDao.mergeUserState(
                guid = it.guid,
                isFavorite = it.isFavorite,
                isPlayed = it.isPlayed,
                playbackPosition = it.playbackPosition,
                lastPlayedAt = it.lastPlayedAt
            )
        }

        val importedGuids = importableEpisodes.map { it.guid }.toSet()

        backup.queue.sortedBy { it.position }.forEach { item ->
            val episodeExists = item.episodeGuid in importedGuids ||
                episodeDao.getByGuid(item.episodeGuid) != null
            if (episodeExists && queueDao.findPosition(item.episodeGuid) == null) {
                queueDao.insert(QueueItemEntity(item.episodeGuid, queueDao.maxPosition() + 1))
            }
        }

        backup.moments.forEach { moment ->
            val episodeExists = moment.episodeGuid in importedGuids ||
                episodeDao.getByGuid(moment.episodeGuid) != null
            if (episodeExists && !savedMomentDao.exists(moment.episodeGuid, moment.positionMs, moment.createdAt)) {
                savedMomentDao.insert(
                    SavedMomentEntity(
                        episodeGuid = moment.episodeGuid,
                        positionMs = moment.positionMs,
                        clipStartMs = moment.clipStartMs,
                        clipEndMs = moment.clipEndMs,
                        transcriptText = moment.transcriptText,
                        createdAt = moment.createdAt
                    )
                )
            }
        }

        val downloadsToRestore = backup.downloads
            .map { it.episodeGuid }
            .filter { guid ->
                guid in importedGuids && downloadedEpisodeDao.getByGuid(guid) == null
            }

        val podcastsById = backup.podcasts.associateBy { it.id }
        val feedTargets = backup.subscriptions.mapNotNull { sub ->
            podcastsById[sub.podcastId]?.let { FeedTarget(it.id, it.feedUrl) }
        }

        ImportResult(downloadsToRestore = downloadsToRestore, feedTargets = feedTargets)
    }
```

And add the entity mappers at the bottom of the file:

```kotlin
private fun BackupPodcast.toEntity() = PodcastEntity(
    id = id,
    title = title,
    artworkUrl = artworkUrl,
    artistName = artistName,
    feedUrl = feedUrl,
    description = description,
    genres = genres,
    episodeCount = episodeCount,
    language = language
)

private fun BackupEpisode.toEntity() = EpisodeEntity(
    guid = guid,
    podcastId = podcastId,
    title = title,
    description = description,
    audioUrl = audioUrl,
    artworkUrl = artworkUrl,
    publishedAt = publishedAt,
    durationSeconds = durationSeconds,
    fileSizeBytes = fileSizeBytes,
    playbackPosition = playbackPosition,
    isPlayed = isPlayed,
    isFavorite = isFavorite,
    lastPlayedAt = lastPlayedAt,
    transcriptUrl = transcriptUrl,
    transcriptType = transcriptType
)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.data.backup.BackupRepositoryImplTest`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/data/backup/BackupRepositoryImpl.kt app/src/test/java/com/example/mypodcast/data/backup/BackupRepositoryImplTest.kt
git commit -m "Implement merge-only backup import"
```

---

### Task 6: Extract EpisodeAudioDownloader from DownloadEpisodeUseCase

The streaming download logic currently lives inside `DownloadEpisodeUseCase` (a `Flow`-emitting use case). The restore worker needs the same logic without the Flow wrapper, so extract it behind an interface (interface also enables a fake in the worker test).

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/data/download/EpisodeAudioDownloader.kt`
- Modify: `app/src/main/java/com/example/mypodcast/domain/usecase/episode/DownloadEpisodeUseCase.kt`
- Modify: `app/src/main/java/com/example/mypodcast/di/RepositoryModule.kt`
- Move test: `app/src/test/java/com/example/mypodcast/domain/usecase/episode/DownloadEpisodeUseCaseTest.kt` → `app/src/test/java/com/example/mypodcast/data/download/EpisodeAudioDownloaderTest.kt`

- [ ] **Step 1: Create the downloader (logic moved verbatim from the use case)**

Create `app/src/main/java/com/example/mypodcast/data/download/EpisodeAudioDownloader.kt`:

```kotlin
package com.example.mypodcast.data.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Streams an episode's audio to app-private storage. */
interface EpisodeAudioDownloader {
    /**
     * Downloads [audioUrl] to `files/episodes/<episodeGuid>.mp3`, reporting percent
     * progress via [onProgress]. Returns the finished file or throws [IOException].
     */
    suspend fun download(
        episodeGuid: String,
        audioUrl: String,
        expectedFileSizeBytes: Long,
        onProgress: (Int) -> Unit = {}
    ): File
}

class OkHttpEpisodeAudioDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) : EpisodeAudioDownloader {

    override suspend fun download(
        episodeGuid: String,
        audioUrl: String,
        expectedFileSizeBytes: Long,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "episodes/$episodeGuid.mp3").also {
            it.parentFile?.mkdirs()
        }
        val tempFile = File(file.parentFile, "${file.name}.part")
        try {
            tempFile.delete()
            val request = Request.Builder().url(audioUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: error("Empty response")
                val totalBytes = expectedDownloadBytes(
                    contentLength = body.contentLength(),
                    episodeFileSizeBytes = expectedFileSizeBytes
                )
                var downloadedBytes = 0L
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytes: Int
                        while (input.read(buffer).also { bytes = it } != -1) {
                            output.write(buffer, 0, bytes)
                            downloadedBytes += bytes
                            if (totalBytes != null) {
                                onProgress((downloadedBytes * 100 / totalBytes).toInt())
                            }
                        }
                    }
                }
                requireCompleteDownload(downloadedBytes, totalBytes)
            }
            if (file.exists() && !file.delete()) {
                throw IOException("Could not replace existing download")
            }
            if (!tempFile.renameTo(file)) {
                throw IOException("Could not finalize download")
            }
            file
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }
}

internal fun expectedDownloadBytes(contentLength: Long, episodeFileSizeBytes: Long): Long? =
    contentLength.takeIf { it > 0 } ?: episodeFileSizeBytes.takeIf { it > 0 }

@Throws(IOException::class)
internal fun requireCompleteDownload(downloadedBytes: Long, expectedBytes: Long?) {
    if (expectedBytes != null && downloadedBytes < expectedBytes) {
        throw IOException("Download incomplete: received $downloadedBytes of $expectedBytes bytes")
    }
}
```

- [ ] **Step 2: Rewrite DownloadEpisodeUseCase to delegate**

Replace `app/src/main/java/com/example/mypodcast/domain/usecase/episode/DownloadEpisodeUseCase.kt` with:

```kotlin
package com.example.mypodcast.domain.usecase.episode

import com.example.mypodcast.data.download.EpisodeAudioDownloader
import com.example.mypodcast.domain.model.DownloadState
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.repository.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class DownloadEpisodeUseCase @Inject constructor(
    private val downloader: EpisodeAudioDownloader,
    private val libraryRepository: LibraryRepository
) {
    operator fun invoke(episode: Episode): Flow<DownloadState> = channelFlow {
        send(DownloadState.Downloading(0))
        try {
            val file = downloader.download(
                episodeGuid = episode.guid,
                audioUrl = episode.audioUrl,
                expectedFileSizeBytes = episode.fileSizeBytes
            ) { progress ->
                trySend(DownloadState.Downloading(progress))
            }
            libraryRepository.saveDownload(episode.guid, episode.podcastId, file.absolutePath, file.length())
            send(DownloadState.Completed(file.absolutePath))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            send(DownloadState.Failed(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)
}
```

Note: `channelFlow` is used because the progress callback is not a suspend context; `trySend` is safe there. No `awaitClose` is needed since the block returns when the download finishes.

- [ ] **Step 3: Bind the downloader interface**

In `di/RepositoryModule.kt`, add imports `com.example.mypodcast.data.download.EpisodeAudioDownloader` and `com.example.mypodcast.data.download.OkHttpEpisodeAudioDownloader`, and add:

```kotlin
    @Binds
    @Singleton
    abstract fun bindEpisodeAudioDownloader(impl: OkHttpEpisodeAudioDownloader): EpisodeAudioDownloader
```

- [ ] **Step 4: Move the helper-function test**

Delete `app/src/test/java/com/example/mypodcast/domain/usecase/episode/DownloadEpisodeUseCaseTest.kt` and create `app/src/test/java/com/example/mypodcast/data/download/EpisodeAudioDownloaderTest.kt`:

```kotlin
package com.example.mypodcast.data.download

import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.io.IOException

class EpisodeAudioDownloaderTest {
    @Test
    fun requireCompleteDownload_rejectsShortDownload() {
        val error = kotlin.runCatching {
            requireCompleteDownload(downloadedBytes = 1_024L, expectedBytes = 2_048L)
        }.exceptionOrNull()

        assertEquals(
            "Download incomplete: received 1024 of 2048 bytes",
            (error as IOException).message
        )
    }

    @Test
    fun expectedDownloadBytes_prefersContentLengthThenFallsBack() {
        assertEquals(10L, expectedDownloadBytes(contentLength = 10L, episodeFileSizeBytes = 20L))
        assertEquals(20L, expectedDownloadBytes(contentLength = -1L, episodeFileSizeBytes = 20L))
        assertEquals(null, expectedDownloadBytes(contentLength = 0L, episodeFileSizeBytes = 0L))
    }
}
```

- [ ] **Step 5: Verify full suite still passes (download flow consumers unchanged)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — in particular `LibraryViewModelTest` and `PodcastDetailScreenSourceTest` style tests that exercise downloads still pass. If `LibraryViewModelTest` constructs `DownloadEpisodeUseCase` directly, update its construction to pass a fake `EpisodeAudioDownloader` instead of an `OkHttpClient`.

- [ ] **Step 6: Commit**

```bash
git add -A app/src/main/java/com/example/mypodcast/data/download app/src/main/java/com/example/mypodcast/domain/usecase/episode/DownloadEpisodeUseCase.kt app/src/main/java/com/example/mypodcast/di/RepositoryModule.kt app/src/test/java/com/example/mypodcast
git commit -m "Extract EpisodeAudioDownloader for reuse by restore worker"
```

---

### Task 7: RestoreQueueStore — persisted pending-restore list

WorkManager `Data` is capped at ~10 KB, which a few hundred GUIDs could exceed; a JSON file in `filesDir` also survives retries and process death, making the worker resumable.

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/data/backup/RestoreQueueStore.kt`
- Test: `app/src/test/java/com/example/mypodcast/data/backup/RestoreQueueStoreTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/example/mypodcast/data/backup/RestoreQueueStoreTest.kt`:

```kotlin
package com.example.mypodcast.data.backup

import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestoreQueueStoreTest {
    private lateinit var store: RestoreQueueStore

    @Before
    fun setUp() {
        store = RestoreQueueStore(RuntimeEnvironment.getApplication())
        store.clear()
    }

    @Test
    fun replace_thenSnapshot_returnsQueue() {
        store.replace(listOf("a", "b", "c"))

        val queue = store.snapshot()

        assertEquals(3, queue.total)
        assertEquals(listOf("a", "b", "c"), queue.pending)
    }

    @Test
    fun remove_keepsTotalButShrinksPending() {
        store.replace(listOf("a", "b"))

        store.remove("a")

        val queue = store.snapshot()
        assertEquals(2, queue.total)
        assertEquals(listOf("b"), queue.pending)
    }

    @Test
    fun snapshot_isEmptyWhenNothingStored() {
        val queue = store.snapshot()

        assertEquals(0, queue.total)
        assertEquals(emptyList<String>(), queue.pending)
    }

    @Test
    fun snapshot_toleratesCorruptFile() {
        File(RuntimeEnvironment.getApplication().filesDir, "restore_downloads.json")
            .writeText("{{{corrupt")

        val queue = store.snapshot()

        assertEquals(0, queue.total)
        assertEquals(emptyList<String>(), queue.pending)
    }

    @Test
    fun clear_removesQueue() {
        store.replace(listOf("a"))

        store.clear()

        assertEquals(0, store.snapshot().total)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.data.backup.RestoreQueueStoreTest`
Expected: FAIL to compile — `RestoreQueueStore` not defined.

- [ ] **Step 3: Implement the store**

Create `app/src/main/java/com/example/mypodcast/data/backup/RestoreQueueStore.kt`:

```kotlin
package com.example.mypodcast.data.backup

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class RestoreQueue(val total: Int, val pending: List<String>)

/**
 * Pending episode GUIDs awaiting re-download after a backup import.
 * Persisted as JSON in filesDir so the restore survives process death and
 * WorkManager retries, and isn't limited by the 10 KB Work Data cap.
 */
@Singleton
class RestoreQueueStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val file: File
        get() = File(context.filesDir, "restore_downloads.json")

    @Synchronized
    fun replace(guids: List<String>) {
        write(RestoreQueue(total = guids.size, pending = guids))
    }

    @Synchronized
    fun snapshot(): RestoreQueue = read()

    @Synchronized
    fun remove(guid: String) {
        val queue = read()
        write(queue.copy(pending = queue.pending - guid))
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    private fun read(): RestoreQueue {
        if (!file.exists()) return EMPTY
        return runCatching { gson.fromJson(file.readText(), RestoreQueue::class.java) }
            .getOrNull() ?: EMPTY
    }

    private fun write(queue: RestoreQueue) {
        file.writeText(gson.toJson(queue))
    }

    private companion object {
        val EMPTY = RestoreQueue(total = 0, pending = emptyList())
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.data.backup.RestoreQueueStoreTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/data/backup/RestoreQueueStore.kt app/src/test/java/com/example/mypodcast/data/backup/RestoreQueueStoreTest.kt
git commit -m "Add persisted restore-download queue store"
```

---

### Task 8: RestoreDownloadsWorker and RestoreScheduler

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/work/RestoreDownloadsWorker.kt`
- Create: `app/src/main/java/com/example/mypodcast/work/RestoreScheduler.kt`
- Create: `app/src/main/java/com/example/mypodcast/di/WorkModule.kt`
- Test: `app/src/test/java/com/example/mypodcast/work/RestoreDownloadsWorkerTest.kt`

- [ ] **Step 1: Write the failing worker tests**

Create `app/src/test/java/com/example/mypodcast/work/RestoreDownloadsWorkerTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.work.RestoreDownloadsWorkerTest`
Expected: FAIL to compile — `RestoreDownloadsWorker` not defined.

- [ ] **Step 3: Implement the worker**

Create `app/src/main/java/com/example/mypodcast/work/RestoreDownloadsWorker.kt`:

```kotlin
package com.example.mypodcast.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.mypodcast.data.backup.RestoreQueueStore
import com.example.mypodcast.data.download.EpisodeAudioDownloader
import com.example.mypodcast.data.local.dao.DownloadedEpisodeDao
import com.example.mypodcast.data.local.dao.EpisodeDao
import com.example.mypodcast.data.local.entity.DownloadedEpisodeEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Re-downloads episode audio after a backup import. Reads pending GUIDs from
 * [RestoreQueueStore]; each completed file gets its downloaded_episodes row,
 * so the Library fills in progressively and retries are idempotent.
 */
@HiltWorker
class RestoreDownloadsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val store: RestoreQueueStore,
    private val episodeDao: EpisodeDao,
    private val downloadedEpisodeDao: DownloadedEpisodeDao,
    private val downloader: EpisodeAudioDownloader
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val queue = store.snapshot()
        if (queue.pending.isEmpty()) {
            store.clear()
            return Result.success()
        }

        var anyFailed = false
        for (guid in queue.pending) {
            val done = queue.total - store.snapshot().pending.size
            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to queue.total))
            // Foreground promotion can be refused (e.g. background start
            // restrictions); the work still proceeds without a notification.
            runCatching { setForeground(foregroundInfo(done, queue.total)) }

            if (downloadedEpisodeDao.getByGuid(guid) != null) {
                store.remove(guid)
                continue
            }
            val episode = episodeDao.getByGuid(guid)
            if (episode == null) {
                store.remove(guid)
                continue
            }
            try {
                val file = downloader.download(
                    episodeGuid = guid,
                    audioUrl = episode.audioUrl,
                    expectedFileSizeBytes = episode.fileSizeBytes
                )
                downloadedEpisodeDao.insert(
                    DownloadedEpisodeEntity(
                        episodeGuid = guid,
                        podcastId = episode.podcastId,
                        localFilePath = file.absolutePath,
                        fileSizeBytes = file.length()
                    )
                )
                store.remove(guid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                anyFailed = true
            }
        }

        return when {
            !anyFailed -> {
                store.clear()
                Result.success()
            }
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> {
                store.clear()
                Result.failure()
            }
        }
    }

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Restore downloads", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Restoring downloads")
            .setContentText("${done + 1} of $total")
            .setProgress(total, done, false)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val UNIQUE_NAME = "restore-downloads"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val CHANNEL_ID = "restore_downloads"
        const val NOTIFICATION_ID = 2001
        const val MAX_RETRIES = 5
    }
}
```

- [ ] **Step 4: Implement the scheduler**

Create `app/src/main/java/com/example/mypodcast/work/RestoreScheduler.kt`:

```kotlin
package com.example.mypodcast.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.mypodcast.data.backup.RestoreQueueStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class RestoreProgress(val done: Int, val total: Int)

/** Schedules and observes the background re-download of imported episodes. */
interface RestoreScheduler {
    fun scheduleRestore(episodeGuids: List<String>)

    /** Emits progress while a restore runs, null when idle. */
    fun observeProgress(): Flow<RestoreProgress?>
}

class WorkManagerRestoreScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: RestoreQueueStore
) : RestoreScheduler {

    override fun scheduleRestore(episodeGuids: List<String>) {
        if (episodeGuids.isEmpty()) return
        store.replace(episodeGuids)
        val request = OneTimeWorkRequestBuilder<RestoreDownloadsWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(RestoreDownloadsWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun observeProgress(): Flow<RestoreProgress?> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(RestoreDownloadsWorker.UNIQUE_NAME)
            .map { infos ->
                val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING } ?: return@map null
                val total = running.progress.getInt(RestoreDownloadsWorker.KEY_TOTAL, 0)
                if (total <= 0) null
                else RestoreProgress(
                    done = running.progress.getInt(RestoreDownloadsWorker.KEY_DONE, 0),
                    total = total
                )
            }
}
```

Create `app/src/main/java/com/example/mypodcast/di/WorkModule.kt`:

```kotlin
package com.example.mypodcast.di

import com.example.mypodcast.work.RestoreScheduler
import com.example.mypodcast.work.WorkManagerRestoreScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {

    @Binds
    @Singleton
    abstract fun bindRestoreScheduler(impl: WorkManagerRestoreScheduler): RestoreScheduler
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.work.RestoreDownloadsWorkerTest`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/work app/src/main/java/com/example/mypodcast/di/WorkModule.kt app/src/test/java/com/example/mypodcast/work
git commit -m "Add restore-downloads worker and scheduler"
```

---

### Task 9: Backup use cases and BackupRestoreViewModel

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/domain/usecase/backup/BackupUseCases.kt`
- Create: `app/src/main/java/com/example/mypodcast/ui/backup/BackupRestoreViewModel.kt`
- Test: `app/src/test/java/com/example/mypodcast/ui/backup/BackupRestoreViewModelTest.kt`

- [ ] **Step 1: Create the use cases**

Create `app/src/main/java/com/example/mypodcast/domain/usecase/backup/BackupUseCases.kt`:

```kotlin
package com.example.mypodcast.domain.usecase.backup

import com.example.mypodcast.data.backup.BackupSerializer
import com.example.mypodcast.domain.model.backup.BackupSummary
import com.example.mypodcast.domain.model.backup.ImportResult
import com.example.mypodcast.domain.model.backup.LibraryBackup
import com.example.mypodcast.domain.repository.BackupRepository
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class ExportLibraryUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
    private val serializer: BackupSerializer
) {
    suspend operator fun invoke(output: OutputStream) {
        serializer.write(backupRepository.createBackup(), output)
    }
}

data class InspectedBackup(val backup: LibraryBackup, val summary: BackupSummary)

class InspectBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
    private val serializer: BackupSerializer
) {
    /** @throws com.example.mypodcast.domain.model.backup.InvalidBackupException
     *  @throws com.example.mypodcast.domain.model.backup.UnsupportedBackupVersionException */
    suspend operator fun invoke(input: InputStream): InspectedBackup {
        val backup = serializer.read(input)
        return InspectedBackup(backup, backupRepository.summarize(backup))
    }
}

class ImportLibraryUseCase @Inject constructor(
    private val backupRepository: BackupRepository
) {
    suspend operator fun invoke(backup: LibraryBackup): ImportResult =
        backupRepository.import(backup)
}
```

- [ ] **Step 2: Write the failing ViewModel tests**

Create `app/src/test/java/com/example/mypodcast/ui/backup/BackupRestoreViewModelTest.kt`:

```kotlin
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
        // Implement remaining EpisodeRepository members as no-op stubs that
        // throw NotImplementedError; only fetchEpisodesForPodcast is exercised.
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
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
            episodeRepository = episodeRepository
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
```

Note for the implementer: `FakeEpisodeRepository` must override every member of `EpisodeRepository` — open `domain/repository/EpisodeRepository.kt` and stub each remaining function with `TODO()` (they are not called by these tests); only `fetchEpisodesForPodcast` needs a real body. If `TODO()` conflicts with a `Flow` return type used during init, return `kotlinx.coroutines.flow.emptyFlow()` instead.

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.ui.backup.BackupRestoreViewModelTest`
Expected: FAIL to compile — `BackupRestoreViewModel` not defined.

- [ ] **Step 4: Implement the ViewModel**

Create `app/src/main/java/com/example/mypodcast/ui/backup/BackupRestoreViewModel.kt`:

```kotlin
package com.example.mypodcast.ui.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypodcast.domain.model.backup.BackupSummary
import com.example.mypodcast.domain.model.backup.FeedTarget
import com.example.mypodcast.domain.model.backup.InvalidBackupException
import com.example.mypodcast.domain.model.backup.LibraryBackup
import com.example.mypodcast.domain.model.backup.UnsupportedBackupVersionException
import com.example.mypodcast.domain.repository.EpisodeRepository
import com.example.mypodcast.domain.usecase.backup.ExportLibraryUseCase
import com.example.mypodcast.domain.usecase.backup.ImportLibraryUseCase
import com.example.mypodcast.domain.usecase.backup.InspectBackupUseCase
import com.example.mypodcast.work.RestoreProgress
import com.example.mypodcast.work.RestoreScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PendingImport(
    val fileName: String,
    val backup: LibraryBackup,
    val summary: BackupSummary
)

data class BackupRestoreUiState(
    val isExporting: Boolean = false,
    val lastExportAt: Long? = null,
    val pendingImport: PendingImport? = null,
    val isImporting: Boolean = false,
    val restoreProgress: RestoreProgress? = null,
    val message: String? = null
)

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportLibrary: ExportLibraryUseCase,
    private val inspectBackup: InspectBackupUseCase,
    private val importLibrary: ImportLibraryUseCase,
    private val restoreScheduler: RestoreScheduler,
    private val episodeRepository: EpisodeRepository
) : ViewModel() {

    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        BackupRestoreUiState(
            lastExportAt = prefs.getLong(KEY_LAST_EXPORT, 0L).takeIf { it > 0L }
        )
    )
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            restoreScheduler.observeProgress().collect { progress ->
                _uiState.update { it.copy(restoreProgress = progress) }
            }
        }
    }

    fun export(uri: Uri) {
        if (_uiState.value.isExporting) return
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    // "wt" truncates when overwriting a longer existing document;
                    // some providers (and Robolectric shadows) only accept the default mode.
                    val output = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
                        ?: context.contentResolver.openOutputStream(uri)
                        ?: throw IOException("Couldn't open the selected location")
                    output.use { exportLibrary(it) }
                }
            }.onSuccess {
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_EXPORT, now).apply()
                _uiState.update { it.copy(isExporting = false, lastExportAt = now, message = "Backup saved") }
            }.onFailure { error ->
                _uiState.update { it.copy(isExporting = false, message = "Export failed: ${error.message}") }
            }
        }
    }

    fun inspect(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("Couldn't open the selected file")
                    input.use { inspectBackup(it) }
                }
            }.onSuccess { inspected ->
                _uiState.update {
                    it.copy(
                        pendingImport = PendingImport(
                            fileName = displayName(uri),
                            backup = inspected.backup,
                            summary = inspected.summary
                        )
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(message = importErrorMessage(error)) }
            }
        }
    }

    fun confirmImport() {
        val pending = _uiState.value.pendingImport ?: return
        if (_uiState.value.isImporting) return
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            runCatching { importLibrary(pending.backup) }
                .onSuccess { result ->
                    if (result.downloadsToRestore.isNotEmpty()) {
                        restoreScheduler.scheduleRestore(result.downloadsToRestore)
                    }
                    val message = if (result.downloadsToRestore.isEmpty()) "Backup restored"
                    else "Backup restored. Downloading ${result.downloadsToRestore.size} episodes in the background"
                    _uiState.update {
                        it.copy(isImporting = false, pendingImport = null, message = message)
                    }
                    refreshFeeds(result.feedTargets)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isImporting = false, pendingImport = null, message = "Import failed: ${error.message}")
                    }
                }
        }
    }

    fun dismissImport() {
        _uiState.update { it.copy(pendingImport = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun refreshFeeds(targets: List<FeedTarget>) {
        viewModelScope.launch {
            targets.forEach { target ->
                runCatching { episodeRepository.fetchEpisodesForPodcast(target.podcastId, target.feedUrl) }
            }
        }
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) return cursor.getString(index)
                    }
                }
        }
        return uri.lastPathSegment ?: "backup file"
    }

    private fun importErrorMessage(error: Throwable): String = when (error) {
        is UnsupportedBackupVersionException ->
            "This backup was made by a newer version of MyPodcast. Update the app to restore it."
        is InvalidBackupException -> "This file isn't a MyPodcast backup."
        else -> "Couldn't read the backup: ${error.message}"
    }

    private companion object {
        const val KEY_LAST_EXPORT = "last_export_at"
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.ui.backup.BackupRestoreViewModelTest`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/domain/usecase/backup app/src/main/java/com/example/mypodcast/ui/backup/BackupRestoreViewModel.kt app/src/test/java/com/example/mypodcast/ui/backup
git commit -m "Add backup use cases and BackupRestoreViewModel"
```

---

### Task 10: Screen, navigation, and Library menu entry point

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/ui/backup/BackupRestoreScreen.kt`
- Modify: `app/src/main/java/com/example/mypodcast/NavigationKeys.kt`
- Modify: `app/src/main/java/com/example/mypodcast/Navigation.kt`
- Modify: `app/src/main/java/com/example/mypodcast/ui/library/LibraryScreen.kt`
- Test: `app/src/test/java/com/example/mypodcast/ui/backup/BackupRestoreSourceTest.kt`

- [ ] **Step 1: Write the failing source-wiring test** (matches the repo's `*SourceTest` convention)

Create `app/src/test/java/com/example/mypodcast/ui/backup/BackupRestoreSourceTest.kt`:

```kotlin
package com.example.mypodcast.ui.backup

import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.io.File

class BackupRestoreSourceTest {
    private fun source(path: String): String {
        val root = File(System.getProperty("user.dir"))
        return File(root, "src/main/java/com/example/mypodcast/$path").readText()
    }

    @Test
    fun navigationKeys_declareBackupRestoreKey() {
        assertTrue(source("NavigationKeys.kt").contains("data object BackupRestoreNavKey : NavKey"))
    }

    @Test
    fun navigation_registersBackupRestoreEntry() {
        assertTrue(source("Navigation.kt").contains("entry<BackupRestoreNavKey>"))
    }

    @Test
    fun libraryScreen_opensBackupRestoreFromOverflowMenu() {
        val library = source("ui/library/LibraryScreen.kt")
        assertTrue(library.contains("DropdownMenu"))
        assertTrue(library.contains("Backup and restore"))
        assertTrue(library.contains("onOpenBackupRestore"))
    }

    @Test
    fun screen_launchesSafPickersAndConfirmDialog() {
        val screen = source("ui/backup/BackupRestoreScreen.kt")
        assertTrue(screen.contains("CreateDocument(\"application/json\")"))
        assertTrue(screen.contains("OpenDocument"))
        assertTrue(screen.contains("Restore this backup?"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.ui.backup.BackupRestoreSourceTest`
Expected: FAIL — `BackupRestoreScreen.kt` missing / assertions fail.

- [ ] **Step 3: Add the nav key and entry**

In `NavigationKeys.kt`, add:

```kotlin
@Serializable data object BackupRestoreNavKey : NavKey
```

In `Navigation.kt`, add import `com.example.mypodcast.ui.backup.BackupRestoreScreen`, and inside `entryProvider` (after the `PlayerNavKey` entry):

```kotlin
            entry<BackupRestoreNavKey> {
                BackupRestoreScreen(onBack = { backStack.removeLastOrNull() })
            }
```

In the existing `LibraryScreen(...)` call inside `entry<LibraryNavKey>`, add the parameter:

```kotlin
                        onOpenBackupRestore = { backStack.add(BackupRestoreNavKey) },
```

- [ ] **Step 4: Replace the Library overflow placeholder with a menu**

In `ui/library/LibraryScreen.kt`:

1. Add the parameter to the composable signature:

```kotlin
fun LibraryScreen(
    onPodcastClick: (Long) -> Unit,
    onBack: () -> Unit = {},
    onEpisodePlay: (String) -> Unit = {},
    onOpenBackupRestore: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
```

2. Add a state holder near the other `remember` state at the top of the composable body:

```kotlin
    var overflowExpanded by remember { mutableStateOf(false) }
```

3. Replace the placeholder action:

```kotlin
                        IconButton(onClick = { /* More library options coming later. */ }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More library options")
                        }
```

with:

```kotlin
                        Box {
                            IconButton(onClick = { overflowExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More library options")
                            }
                            DropdownMenu(
                                expanded = overflowExpanded,
                                onDismissRequest = { overflowExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Backup and restore") },
                                    onClick = {
                                        overflowExpanded = false
                                        onOpenBackupRestore()
                                    }
                                )
                            }
                        }
```

4. Add imports: `androidx.compose.material3.DropdownMenu`, `androidx.compose.material3.DropdownMenuItem` (`Box`, `mutableStateOf`, `remember`, `getValue`, `setValue` are already imported).

- [ ] **Step 5: Create the screen**

Create `app/src/main/java/com/example/mypodcast/ui/backup/BackupRestoreScreen.kt`:

```kotlin
package com.example.mypodcast.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    viewModel: BackupRestoreViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::inspect) }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Backup and restore",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExportCard(
                isExporting = state.isExporting,
                lastExportAt = state.lastExportAt,
                onExport = { exportLauncher.launch(defaultBackupFileName()) }
            )
            ImportCard(
                restoreProgress = state.restoreProgress,
                onChooseFile = {
                    importLauncher.launch(arrayOf("application/json", "application/octet-stream"))
                }
            )
            Text(
                text = "Backups include your library data only, not audio files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    state.pendingImport?.let { pending ->
        ImportConfirmDialog(
            pending = pending,
            isImporting = state.isImporting,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::dismissImport
        )
    }
}

@Composable
private fun ExportCard(
    isExporting: Boolean,
    lastExportAt: Long?,
    onExport: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.FileUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Export backup", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "Save your shows, downloads, moments, favorites, queue, and history " +
                    "to a file in phone storage or Google Drive.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onExport,
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Export…")
                }
            }
            Text(
                text = "Last export: ${lastExportAt?.let(::formatTimestamp) ?: "never"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImportCard(
    restoreProgress: com.example.mypodcast.work.RestoreProgress?,
    onChooseFile: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Import backup", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "Restore from a backup file. Missing episode audio downloads again in the background.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onChooseFile, modifier = Modifier.fillMaxWidth()) {
                Text("Choose backup file…")
            }
            if (restoreProgress != null) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Restoring downloads", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "${restoreProgress.done} of ${restoreProgress.total}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                LinearProgressIndicator(
                    progress = {
                        if (restoreProgress.total == 0) 0f
                        else restoreProgress.done.toFloat() / restoreProgress.total
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ImportConfirmDialog(
    pending: PendingImport,
    isImporting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        title = { Text("Restore this backup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = pending.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                SummaryRow("Shows", pending.summary.shows)
                SummaryRow("Favorites", pending.summary.favorites)
                SummaryRow("Downloads to restore", pending.summary.downloadsToRestore)
                SummaryRow("Saved moments", pending.summary.moments)
                SummaryRow("Queue items", pending.summary.queueItems)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Your current library is kept. Backup items are merged in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isImporting) {
                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Restore")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isImporting) { Text("Cancel") }
        }
    )
}

@Composable
private fun SummaryRow(label: String, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun defaultBackupFileName(): String {
    val date = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
        .withZone(ZoneId.systemDefault())
        .format(Instant.now())
    return "MyPodcast-backup-$date.json"
}

private fun formatTimestamp(millis: Long): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
```

- [ ] **Step 6: Run the source test and full suite**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.ui.backup.BackupRestoreSourceTest`
Expected: PASS (4 tests).

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (includes `MainScreenSourceTest`/`NavigationSourceTest`, which must not regress).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/ui/backup/BackupRestoreScreen.kt app/src/main/java/com/example/mypodcast/NavigationKeys.kt app/src/main/java/com/example/mypodcast/Navigation.kt app/src/main/java/com/example/mypodcast/ui/library/LibraryScreen.kt app/src/test/java/com/example/mypodcast/ui/backup/BackupRestoreSourceTest.kt
git commit -m "Add backup and restore screen with Library menu entry"
```

---

### Task 11: Final verification

**Files:** none new.

- [ ] **Step 1: Full build and test suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Manual QA on an emulator or device** (requires a running emulator; skip if unavailable and note it)

1. Subscribe to a show, favorite an episode, download one episode, save a moment, queue an episode, play something briefly.
2. Library → overflow menu → Backup and restore → Export… → save to Downloads (and/or Drive if signed in). Verify the snackbar and "Last export" timestamp.
3. Settings → Apps → MyPodcast → Clear storage (simulates reinstall).
4. Reopen app → Library → Backup and restore → Choose backup file… → pick the exported file → verify the summary dialog counts → Restore.
5. Verify: subscriptions back in Library, favorites/history/queue/moments restored, "Restoring downloads" progress appears (in-app bar + notification), and the Downloads tab fills in when downloads complete.
6. Import the same file a second time → verify no duplicates appear anywhere.
7. Pick a non-backup JSON file → verify the "isn't a MyPodcast backup" error and no data change.

- [ ] **Step 3: Commit any fixes, then hand off**

Use the superpowers:verification-before-completion skill before claiming done, then superpowers:finishing-a-development-branch to decide merge/PR.
