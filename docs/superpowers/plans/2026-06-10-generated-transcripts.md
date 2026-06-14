# On-Device Transcript Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate transcripts on-device for downloaded episodes that lack a publisher transcript, while the episode plays, surfacing them on the existing transcript page plus a Library badge.

**Architecture:** A `TranscriptionSessionManager` (Hilt singleton, lifecycle-hosted by `PlaybackService`) watches `PlayerRepository.playerState`. When a downloaded, transcript-less episode plays, it runs an `EpisodeTranscriber` that decodes the MP3 independently at real-time pace (`Mp3PcmStreamer`) and feeds ML Kit GenAI Speech Recognition (`MlKitSpeechEngine`), computing exact cue timestamps from the byte clock. Progress persists to a JSON store; a Room `transcriptStatus` column drives the Library badge. `TranscriptRepositoryImpl` falls back to the generated store when no publisher URL exists.

**Tech Stack:** Kotlin, Coroutines/Flow, Hilt, Room, Media3 (host service only), MediaExtractor/MediaCodec, ML Kit `genai-speech-recognition:1.0.0-alpha1`, Gson, JUnit4 + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-06-10-generated-transcripts-design.md` — source of truth for requirements.

**Conscious deviation from spec:** the spec says recognizer locale comes from the feed `<language>` tag "when present". The app does not store feed language anywhere (verified — no entity/model has it), so "when present" is never true; we use `Locale.getDefault()` and do NOT add feed-language plumbing (YAGNI).

**Conventions used below:**
- All paths relative to repo root. Package root: `app/src/main/java/com/example/mypodcast/`, tests: `app/src/test/java/com/example/mypodcast/`.
- Test command: `./gradlew :app:testDebugUnitTest --tests "<fqcn>"` — expect `BUILD SUCCESSFUL` and the named tests listed as passed (add `--info` if you need per-test lines). A failing step must fail with the described reason before you implement.
- Commit after every task (commands given). Never push.

---

### Task 1: Dependencies + domain model additions

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/example/mypodcast/domain/model/Transcript.kt`
- Create: `app/src/main/java/com/example/mypodcast/domain/model/TranscriptStatus.kt`
- Create: `app/src/main/java/com/example/mypodcast/domain/transcription/TranscriptionMonitor.kt`

- [ ] **Step 1.1: Add version catalog entries**

In `gradle/libs.versions.toml`, add to `[versions]` (after `reorderable = "2.5.1"`):

```toml
mlkitGenaiSpeech = "1.0.0-alpha1"
gson = "2.11.0"
```

Add to `[libraries]` (after the `# Reorderable LazyColumn` entry):

```toml
# Transcription
mlkit-genai-speech = { group = "com.google.mlkit", name = "genai-speech-recognition", version.ref = "mlkitGenaiSpeech" }
gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }
```

- [ ] **Step 1.2: Add dependencies to app module**

In `app/build.gradle.kts`, inside `dependencies { }` (near the retrofit/okhttp lines), add:

```kotlin
implementation(libs.mlkit.genai.speech)
implementation(libs.gson)
```

- [ ] **Step 1.3: Extend Transcript model**

Replace the `Transcript` data class in `domain/model/Transcript.kt` (keep `TranscriptCue` and all KDoc as-is, append the new property doc):

```kotlin
/**
 * A parsed episode transcript.
 *
 * [isSynced] is true when cues carry real timestamps (VTT/SRT/JSON) so the
 * Player can highlight, auto-scroll, and seek. It is false for plain-text or
 * HTML transcripts, which render as a simple scrollable block.
 *
 * [transcribedUpToMs] is non-null only for partially generated on-device
 * transcripts: the position up to which audio has been transcribed. Null means
 * the transcript is complete (publisher transcripts are always complete).
 */
data class Transcript(
    val cues: List<TranscriptCue>,
    val isSynced: Boolean,
    val transcribedUpToMs: Long? = null
)
```

- [ ] **Step 1.4: Create TranscriptStatus**

Create `domain/model/TranscriptStatus.kt`:

```kotlin
package com.example.mypodcast.domain.model

/** Lifecycle of on-device transcript generation for a downloaded episode. */
enum class TranscriptStatus { NONE, IN_PROGRESS, COMPLETE }
```

- [ ] **Step 1.5: Create TranscriptionMonitor**

Create `domain/transcription/TranscriptionMonitor.kt`:

```kotlin
package com.example.mypodcast.domain.transcription

import com.example.mypodcast.domain.model.TranscriptCue
import kotlinx.coroutines.flow.StateFlow

/** Read-only view of the live on-device transcription session, for the UI. */
interface TranscriptionMonitor {
    /** Null when no session is active and none has produced cues this process. */
    val live: StateFlow<LiveTranscription?>
}

/** Snapshot of an in-flight (or just-finished) generation session. */
data class LiveTranscription(
    val episodeGuid: String,
    val cues: List<TranscriptCue>,
    val transcribedUpToMs: Long,
    val isComplete: Boolean
)
```

- [ ] **Step 1.6: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (the new ML Kit artifact must resolve; if Gradle can't find it, check the maven coordinates against https://developers.google.com/ml-kit/genai/speech-recognition/android and adjust the version in the catalog — it is an alpha artifact).

- [ ] **Step 1.7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/example/mypodcast/domain/
git commit -m "feat: add ML Kit speech dep and transcription domain models"
```

---

### Task 2: PCM math (downmix, resample, byte clock)

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/data/transcription/PcmMath.kt`
- Test: `app/src/test/java/com/example/mypodcast/data/transcription/PcmMathTest.kt`

- [ ] **Step 2.1: Write the failing tests**

Create `app/src/test/java/com/example/mypodcast/data/transcription/PcmMathTest.kt`:

```kotlin
package com.example.mypodcast.data.transcription

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmMathTest {

    @Test
    fun `downmix mono is identity`() {
        val input = shortArrayOf(1, -2, 3)
        assertArrayEquals(input, downmixToMono(input, channels = 1))
    }

    @Test
    fun `downmix stereo averages channel pairs`() {
        // Frames: (100, 200), (-100, 100), (32767, 32767)
        val input = shortArrayOf(100, 200, -100, 100, 32767, 32767)
        assertArrayEquals(shortArrayOf(150, 0, 32767), downmixToMono(input, channels = 2))
    }

    @Test
    fun `resample same rate is identity`() {
        val input = shortArrayOf(0, 100, 200, 300)
        assertArrayEquals(input, resampleLinear(input, fromRate = 16000, toRate = 16000))
    }

    @Test
    fun `resample halves sample count from 32kHz to 16kHz`() {
        val input = ShortArray(32) { (it * 10).toShort() }
        val output = resampleLinear(input, fromRate = 32000, toRate = 16000)
        assertEquals(16, output.size)
        // First output sample maps exactly onto input[0].
        assertEquals(0, output[0].toInt())
    }

    @Test
    fun `resample interpolates between input samples`() {
        // 2 -> 4 samples: positions 0, 0.5, 1.0, 1.5 over input [0, 100]
        val output = resampleLinear(shortArrayOf(0, 100), fromRate = 8000, toRate = 16000)
        assertEquals(4, output.size)
        assertEquals(0, output[0].toInt())
        assertEquals(50, output[1].toInt())
        assertEquals(100, output[2].toInt())
        assertEquals(100, output[3].toInt()) // clamped at the last input sample
    }

    @Test
    fun `byte clock converts 16-bit mono 16kHz bytes to ms`() {
        // 32_000 bytes/sec => 32 bytes/ms
        assertEquals(0L, pcm16BytesToMs(0))
        assertEquals(1_000L, pcm16BytesToMs(32_000))
        assertEquals(12_345L, pcm16BytesToMs(12_345L * 32))
    }
}
```

- [ ] **Step 2.2: Run tests, verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.data.transcription.PcmMathTest"`
Expected: FAIL — compilation error, `downmixToMono` etc. unresolved.

- [ ] **Step 2.3: Implement PcmMath**

Create `app/src/main/java/com/example/mypodcast/data/transcription/PcmMath.kt`:

```kotlin
package com.example.mypodcast.data.transcription

/** Bytes per second of the recognizer's required input: 16-bit mono @ 16 kHz. */
internal const val TARGET_SAMPLE_RATE = 16_000
internal const val TARGET_BYTES_PER_SECOND = TARGET_SAMPLE_RATE * 2

/** Averages interleaved [channels]-channel 16-bit PCM frames down to mono. */
internal fun downmixToMono(input: ShortArray, channels: Int): ShortArray {
    require(channels >= 1) { "channels must be >= 1" }
    if (channels == 1) return input
    val frames = input.size / channels
    val out = ShortArray(frames)
    for (frame in 0 until frames) {
        var sum = 0
        val base = frame * channels
        for (ch in 0 until channels) sum += input[base + ch]
        out[frame] = (sum / channels).toShort()
    }
    return out
}

/** Linear-interpolation resample of mono 16-bit PCM from [fromRate] to [toRate]. */
internal fun resampleLinear(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
    require(fromRate > 0 && toRate > 0) { "rates must be positive" }
    if (fromRate == toRate || input.isEmpty()) return input
    val outSize = (input.size.toLong() * toRate / fromRate).toInt()
    val out = ShortArray(outSize)
    val step = fromRate.toDouble() / toRate
    for (i in 0 until outSize) {
        val srcPos = i * step
        val idx = srcPos.toInt()
        val frac = srcPos - idx
        val a = input[idx.coerceAtMost(input.lastIndex)].toInt()
        val b = input[(idx + 1).coerceAtMost(input.lastIndex)].toInt()
        out[i] = (a + (b - a) * frac).toInt().toShort()
    }
    return out
}

/** Position in milliseconds represented by [byteCount] bytes of target-format PCM. */
internal fun pcm16BytesToMs(byteCount: Long): Long =
    byteCount * 1000 / TARGET_BYTES_PER_SECOND
```

- [ ] **Step 2.4: Run tests, verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.data.transcription.PcmMathTest"`
Expected: PASS (6 tests).

- [ ] **Step 2.5: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/data/transcription/PcmMath.kt app/src/test/java/com/example/mypodcast/data/transcription/PcmMathTest.kt
git commit -m "feat: add PCM downmix/resample/byte-clock math for transcription"
```

---

### Task 3: GeneratedTranscriptStore (JSON persistence)

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/data/transcription/GeneratedTranscriptStore.kt`
- Test: `app/src/test/java/com/example/mypodcast/data/transcription/GeneratedTranscriptStoreTest.kt`

- [ ] **Step 3.1: Write the failing tests**

Create `app/src/test/java/com/example/mypodcast/data/transcription/GeneratedTranscriptStoreTest.kt`:

```kotlin
package com.example.mypodcast.data.transcription

import com.example.mypodcast.domain.model.TranscriptCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GeneratedTranscriptStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = GeneratedTranscriptStore(tmp.root)

    private val sample = GeneratedTranscript(
        cues = listOf(
            TranscriptCue(startMs = 0, endMs = 4_200, text = "Welcome back to the show."),
            TranscriptCue(startMs = 4_200, endMs = 9_000, text = "Today we talk transcripts.")
        ),
        transcribedUpToMs = 9_000,
        isComplete = false,
        engineVersion = "mlkit-genai-1.0.0-alpha1"
    )

    @Test
    fun `read returns null when nothing was written`() {
        assertNull(store().read("guid-1"))
    }

    @Test
    fun `write then read round-trips all fields`() {
        store().write("guid-1", sample)
        assertEquals(sample, store().read("guid-1"))
    }

    @Test
    fun `write replaces previous content atomically`() {
        val s = store()
        s.write("guid-1", sample)
        val completed = sample.copy(isComplete = true, transcribedUpToMs = 60_000)
        s.write("guid-1", completed)
        assertEquals(completed, s.read("guid-1"))
        // No leftover temp file.
        val dir = File(tmp.root, "transcripts/generated")
        assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test
    fun `corrupt file reads as null instead of throwing`() {
        val dir = File(tmp.root, "transcripts/generated").apply { mkdirs() }
        File(dir, "guid-1.json").writeText("{ not json")
        assertNull(store().read("guid-1"))
    }

    @Test
    fun `delete removes the file`() {
        val s = store()
        s.write("guid-1", sample)
        s.delete("guid-1")
        assertNull(s.read("guid-1"))
        assertFalse(File(tmp.root, "transcripts/generated/guid-1.json").exists())
    }
}
```

- [ ] **Step 3.2: Run tests, verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.data.transcription.GeneratedTranscriptStoreTest"`
Expected: FAIL — `GeneratedTranscriptStore` / `GeneratedTranscript` unresolved.

- [ ] **Step 3.3: Implement the store**

Create `app/src/main/java/com/example/mypodcast/data/transcription/GeneratedTranscriptStore.kt`:

```kotlin
package com.example.mypodcast.data.transcription

import com.example.mypodcast.domain.model.TranscriptCue
import com.google.gson.Gson
import java.io.File

/** On-disk state of a (possibly partial) on-device generated transcript. */
data class GeneratedTranscript(
    val cues: List<TranscriptCue>,
    val transcribedUpToMs: Long,
    val isComplete: Boolean,
    val engineVersion: String
)

/**
 * Persists generated transcripts as one JSON file per episode under
 * `<baseDir>/transcripts/generated/<guid>.json`. Writes are atomic
 * (`.part` + rename), matching the idiom used elsewhere in the app.
 */
class GeneratedTranscriptStore(private val baseDir: File) {

    private val gson = Gson()

    fun read(episodeGuid: String): GeneratedTranscript? {
        val file = fileFor(episodeGuid)
        if (!file.exists() || file.length() == 0L) return null
        return runCatching { gson.fromJson(file.readText(), GeneratedTranscript::class.java) }
            .getOrNull()
            ?.takeIf { it.cues != null } // Gson can yield null fields on shape mismatch
    }

    fun write(episodeGuid: String, transcript: GeneratedTranscript) {
        val file = fileFor(episodeGuid)
        val tempFile = File(file.parentFile, "${file.name}.part")
        tempFile.writeText(gson.toJson(transcript))
        if (file.exists() && !file.delete()) error("Could not replace generated transcript")
        if (!tempFile.renameTo(file)) error("Could not finalize generated transcript")
    }

    fun delete(episodeGuid: String) {
        fileFor(episodeGuid).delete()
    }

    private fun fileFor(guid: String): File =
        File(baseDir, "transcripts/generated/$guid.json").also { it.parentFile?.mkdirs() }
}
```

- [ ] **Step 3.4: Run tests, verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.data.transcription.GeneratedTranscriptStoreTest"`
Expected: PASS (5 tests).

- [ ] **Step 3.5: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/data/transcription/GeneratedTranscriptStore.kt app/src/test/java/com/example/mypodcast/data/transcription/GeneratedTranscriptStoreTest.kt
git commit -m "feat: add atomic JSON store for generated transcripts"
```

---

### Task 4: Room migration, DAO, and LibraryRepository status plumbing

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/data/local/entity/DownloadedEpisodeEntity.kt`
- Modify: `app/src/main/java/com/example/mypodcast/data/local/Migrations.kt`
- Modify: `app/src/main/java/com/example/mypodcast/data/local/AppDatabase.kt` (version 7 → 8)
- Modify: `app/src/main/java/com/example/mypodcast/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/example/mypodcast/data/local/dao/DownloadedEpisodeDao.kt`
- Modify: `app/src/main/java/com/example/mypodcast/domain/repository/LibraryRepository.kt`
- Modify: `app/src/main/java/com/example/mypodcast/data/repository/LibraryRepositoryImpl.kt`
- Test: `app/src/test/java/com/example/mypodcast/data/local/MigrationsTest.kt`

- [ ] **Step 4.1: Write the failing migration test**

`MigrationTestHelper` needs instrumented tests and exported schemas (`exportSchema = false` here), so we verify the migration SQL on the JVM with a recording `SupportSQLiteDatabase` proxy.

Create `app/src/test/java/com/example/mypodcast/data/local/MigrationsTest.kt`:

```kotlin
package com.example.mypodcast.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class MigrationsTest {

    private fun recordingDb(executed: MutableList<String>): SupportSQLiteDatabase =
        Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL") executed.add(args!![0] as String)
            null
        } as SupportSQLiteDatabase

    @Test
    fun `migration 7 to 8 adds transcriptStatus with NONE default`() {
        val executed = mutableListOf<String>()

        MIGRATION_7_8.migrate(recordingDb(executed))

        assertEquals(1, executed.size)
        val sql = executed.single()
        assertTrue(sql.contains("ALTER TABLE downloaded_episodes"))
        assertTrue(sql.contains("ADD COLUMN transcriptStatus TEXT NOT NULL DEFAULT 'NONE'"))
    }
}
```

- [ ] **Step 4.2: Run test, verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.data.local.MigrationsTest"`
Expected: FAIL — `MIGRATION_7_8` unresolved.

- [ ] **Step 4.3: Add migration, bump version, register**

Append to `data/local/Migrations.kt`:

```kotlin
internal val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE downloaded_episodes ADD COLUMN transcriptStatus TEXT NOT NULL DEFAULT 'NONE'"
        )
    }
}
```

In `data/local/AppDatabase.kt`, change `version = 7` to `version = 8`.

In `di/DatabaseModule.kt`, add `import com.example.mypodcast.data.local.MIGRATION_7_8` and append `MIGRATION_7_8` to the `.addMigrations(...)` list after `MIGRATION_6_7`.

In `data/local/entity/DownloadedEpisodeEntity.kt`, add the field (after `fileSizeBytes`):

```kotlin
@Entity(tableName = "downloaded_episodes")
data class DownloadedEpisodeEntity(
    @PrimaryKey val episodeGuid: String,
    val podcastId: Long,
    val localFilePath: String,
    val downloadedAt: Long = System.currentTimeMillis(),
    val fileSizeBytes: Long,
    val transcriptStatus: String = "NONE"
)
```

- [ ] **Step 4.4: Run migration test, verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.data.local.MigrationsTest"`
Expected: PASS (1 test).

- [ ] **Step 4.5: Add DAO method**

In `data/local/dao/DownloadedEpisodeDao.kt`, add inside the interface:

```kotlin
@Query("UPDATE downloaded_episodes SET transcriptStatus = :status WHERE episodeGuid = :guid")
suspend fun updateTranscriptStatus(guid: String, status: String)
```

- [ ] **Step 4.6: Extend LibraryRepository interface**

In `domain/repository/LibraryRepository.kt`, add imports for `TranscriptStatus` and add three members to the interface (alongside the existing download methods):

```kotlin
/** Absolute path of the downloaded audio file, or null if not downloaded. */
suspend fun getDownloadedFilePath(episodeGuid: String): String?

/** On-device transcript generation status per downloaded episode guid. */
fun observeTranscriptStatuses(): Flow<Map<String, TranscriptStatus>>

suspend fun setTranscriptStatus(episodeGuid: String, status: TranscriptStatus)
```

- [ ] **Step 4.7: Implement in LibraryRepositoryImpl**

In `data/repository/LibraryRepositoryImpl.kt`:

1. Add constructor parameter `private val generatedTranscriptStore: GeneratedTranscriptStore` (import `com.example.mypodcast.data.transcription.GeneratedTranscriptStore`). Hilt provision comes in Task 8 — until then the app module won't compile for DI, which is fine; unit tests and `compileDebugKotlin` of this file still work because Hilt validation happens at kapt/ksp on assemble. If `compileDebugKotlin` runs Hilt processing in this project and fails, temporarily add to `di/DatabaseModule.kt`:

```kotlin
@Provides
@Singleton
fun provideGeneratedTranscriptStore(@ApplicationContext context: Context) =
    com.example.mypodcast.data.transcription.GeneratedTranscriptStore(context.filesDir)
```

(This placement is temporary — Task 8.2 creates `TranscriptionModule` as the permanent home and deletes this one. If compilation doesn't force you to add it now, skip it and let Task 8 provide it.)

2. Add the implementations:

```kotlin
override suspend fun getDownloadedFilePath(episodeGuid: String): String? =
    downloadedEpisodeDao.getByGuid(episodeGuid)?.localFilePath

override fun observeTranscriptStatuses(): Flow<Map<String, TranscriptStatus>> =
    downloadedEpisodeDao.observeAll().map { downloads ->
        downloads.associate { d ->
            d.episodeGuid to runCatching { TranscriptStatus.valueOf(d.transcriptStatus) }
                .getOrDefault(TranscriptStatus.NONE)
        }
    }

override suspend fun setTranscriptStatus(episodeGuid: String, status: TranscriptStatus) =
    downloadedEpisodeDao.updateTranscriptStatus(episodeGuid, status.name)
```

3. Extend `deleteDownload` to clean up generated artifacts — add before `downloadedEpisodeDao.deleteByGuid(episodeGuid)`:

```kotlin
withContext(Dispatchers.IO) {
    runCatching { generatedTranscriptStore.delete(episodeGuid) }
}
```

(Imports needed: `com.example.mypodcast.domain.model.TranscriptStatus`.)

- [ ] **Step 4.8: Compile and run local tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.example.mypodcast.data.local.MigrationsTest"`
Expected: BUILD SUCCESSFUL, test passes.

- [ ] **Step 4.9: Commit**

```bash
git add -A app/src/main/java/com/example/mypodcast/data/local/ app/src/main/java/com/example/mypodcast/di/DatabaseModule.kt app/src/main/java/com/example/mypodcast/domain/repository/LibraryRepository.kt app/src/main/java/com/example/mypodcast/data/repository/LibraryRepositoryImpl.kt app/src/test/java/com/example/mypodcast/data/local/MigrationsTest.kt
git commit -m "feat: add transcriptStatus column, migration, and library status plumbing"
```

---

### Task 5: Engine abstraction + EpisodeTranscriber

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/data/transcription/SpeechTranscriptionEngine.kt`
- Create: `app/src/main/java/com/example/mypodcast/data/transcription/PcmSource.kt`
- Create: `app/src/main/java/com/example/mypodcast/data/transcription/EpisodeTranscriber.kt`
- Test: `app/src/test/java/com/example/mypodcast/data/transcription/EpisodeTranscriberTest.kt`
- Test fixture: `app/src/test/java/com/example/mypodcast/data/transcription/TranscriptionFakes.kt`

- [ ] **Step 5.1: Create the interfaces (no logic — no test first)**

Create `app/src/main/java/com/example/mypodcast/data/transcription/PcmSource.kt`:

```kotlin
package com.example.mypodcast.data.transcription

import kotlinx.coroutines.flow.Flow

/**
 * A chunk of 16-bit mono 16 kHz PCM. [endPositionMs] is the media position at
 * the END of [bytes], computed from the byte clock — exact, not estimated.
 */
class PcmChunk(val bytes: ByteArray, val endPositionMs: Long)

/** Produces real-time-paced PCM in recognizer format from a local audio file. */
interface PcmSource {
    /** Streams from [startMs] until end of file. Pacing ~1s of audio per 1s wall time. */
    fun stream(startMs: Long): Flow<PcmChunk>
}

interface PcmSourceFactory {
    fun create(filePath: String): PcmSource
}
```

Create `app/src/main/java/com/example/mypodcast/data/transcription/SpeechTranscriptionEngine.kt`:

```kotlin
package com.example.mypodcast.data.transcription

import kotlinx.coroutines.flow.Flow
import java.io.Closeable
import java.util.Locale

enum class EngineAvailability { AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE }

/**
 * Thin, swappable wrapper around an ASR engine (ML Kit GenAI Speech
 * Recognition in production). Everything above this interface is JVM-testable.
 */
interface SpeechTranscriptionEngine {
    suspend fun checkAvailability(): EngineAvailability

    /** Kick off the on-device model download. Fire-and-forget; safe to repeat. */
    suspend fun requestModelDownload()

    /** Opens a recognition session expecting 16-bit mono 16 kHz PCM via [SpeechSession.feed]. */
    fun openSession(locale: Locale): SpeechSession

    companion object {
        const val VERSION = "mlkit-genai-1.0.0-alpha1"
    }
}

interface SpeechSession : Closeable {
    /**
     * Finalized text segments, in order. The flow completes after [finish] once
     * the engine has flushed its last segment, and fails if recognition fails.
     */
    val results: Flow<String>

    /** Feed the next PCM chunk. Suspends if the engine applies backpressure. */
    suspend fun feed(chunk: ByteArray)

    /** Signal end of audio so the engine finalizes pending output. */
    suspend fun finish()
}
```

- [ ] **Step 5.2: Write the failing transcriber tests (with fakes)**

Create `app/src/test/java/com/example/mypodcast/data/transcription/TranscriptionFakes.kt`:

```kotlin
package com.example.mypodcast.data.transcription

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * Scripted PCM source: emits [chunks] without real-time pacing (tests must not
 * sleep). Each entry is (byteCount, endPositionMs).
 */
class FakePcmSource(private val chunks: List<Pair<Int, Long>>) : PcmSource {
    override fun stream(startMs: Long): Flow<PcmChunk> = flow {
        chunks.forEach { (size, endMs) -> emit(PcmChunk(ByteArray(size), endMs)) }
    }
}

/**
 * Scripted engine: emits a queued text after the session has been fed at least
 * the paired number of bytes. Completing happens when finish() is called.
 */
class FakeSpeechEngine(
    private val script: List<Pair<Long, String>>, // fedBytesAtLeast -> segment text
    var availability: EngineAvailability = EngineAvailability.AVAILABLE,
    var failOnFeed: Boolean = false
) : SpeechTranscriptionEngine {
    var modelDownloadRequests = 0
        private set
    val openedSessions = mutableListOf<FakeSpeechSession>()

    override suspend fun checkAvailability(): EngineAvailability = availability
    override suspend fun requestModelDownload() { modelDownloadRequests++ }
    override fun openSession(locale: Locale): SpeechSession =
        FakeSpeechSession(script, failOnFeed).also { openedSessions.add(it) }
}

class FakeSpeechSession(
    script: List<Pair<Long, String>>,
    private val failOnFeed: Boolean
) : SpeechSession {
    private val pending = script.toMutableList()
    private val channel = Channel<String>(Channel.UNLIMITED)
    private var fedBytes = 0L
    var closed = false
        private set

    override val results: Flow<String> = channel.consumeAsFlow()

    override suspend fun feed(chunk: ByteArray) {
        if (failOnFeed) throw IllegalStateException("engine exploded")
        fedBytes += chunk.size
        while (pending.isNotEmpty() && fedBytes >= pending.first().first) {
            channel.send(pending.removeAt(0).second)
        }
    }

    override suspend fun finish() {
        while (pending.isNotEmpty()) channel.send(pending.removeAt(0).second)
        channel.close()
    }

    override fun close() { closed = true; channel.close() }
}
```

Create `app/src/test/java/com/example/mypodcast/data/transcription/EpisodeTranscriberTest.kt`:

```kotlin
package com.example.mypodcast.data.transcription

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class EpisodeTranscriberTest {

    @Test
    fun `assigns contiguous timestamps from the byte clock`() = runTest {
        // Two 1-second chunks (32_000 bytes each). Engine finalizes one segment
        // after the first chunk, one after the second.
        val source = FakePcmSource(listOf(32_000 to 1_000L, 32_000 to 2_000L))
        val engine = FakeSpeechEngine(listOf(32_000L to "hello", 64_000L to "world"))

        val events = EpisodeTranscriber(engine)
            .transcribe(source, startMs = 0L, locale = Locale.US)
            .toList()

        val cues = events.filterIsInstance<TranscriberEvent.Cue>().map { it.cue }
        assertEquals(listOf("hello", "world"), cues.map { it.text })
        assertEquals(0L, cues[0].startMs)
        assertEquals(cues[0].endMs, cues[1].startMs) // contiguous
        assertTrue(cues[1].endMs <= 2_000L)
        assertEquals(TranscriberEvent.Completed, events.last())
    }

    @Test
    fun `resume offsets timestamps by startMs`() = runTest {
        val source = FakePcmSource(listOf(32_000 to 61_000L))
        val engine = FakeSpeechEngine(listOf(32_000L to "later words"))

        val events = EpisodeTranscriber(engine)
            .transcribe(source, startMs = 60_000L, locale = Locale.US)
            .toList()

        val cue = events.filterIsInstance<TranscriberEvent.Cue>().single().cue
        assertEquals(60_000L, cue.startMs)
        assertEquals(61_000L, cue.endMs)
    }

    @Test
    fun `emits progress for silent audio with no cues`() = runTest {
        val source = FakePcmSource(listOf(32_000 to 1_000L, 32_000 to 2_000L))
        val engine = FakeSpeechEngine(emptyList())

        val events = EpisodeTranscriber(engine)
            .transcribe(source, startMs = 0L, locale = Locale.US)
            .toList()

        val progress = events.filterIsInstance<TranscriberEvent.Progress>()
        assertEquals(listOf(1_000L, 2_000L), progress.map { it.upToMs })
        assertEquals(TranscriberEvent.Completed, events.last())
    }

    @Test
    fun `closes the session even when the engine fails`() = runTest {
        val source = FakePcmSource(listOf(32_000 to 1_000L))
        val engine = FakeSpeechEngine(emptyList(), failOnFeed = true)

        val result = runCatching {
            EpisodeTranscriber(engine).transcribe(source, 0L, Locale.US).toList()
        }

        assertTrue(result.isFailure)
        assertTrue(engine.openedSessions.single().closed)
    }

    @Test
    fun `blank segments are dropped`() = runTest {
        val source = FakePcmSource(listOf(32_000 to 1_000L))
        val engine = FakeSpeechEngine(listOf(32_000L to "   "))

        val events = EpisodeTranscriber(engine).transcribe(source, 0L, Locale.US).toList()

        assertTrue(events.filterIsInstance<TranscriberEvent.Cue>().isEmpty())
    }
}
```

- [ ] **Step 5.3: Run tests, verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.data.transcription.EpisodeTranscriberTest"`
Expected: FAIL — `EpisodeTranscriber` / `TranscriberEvent` unresolved.

- [ ] **Step 5.4: Implement EpisodeTranscriber**

Create `app/src/main/java/com/example/mypodcast/data/transcription/EpisodeTranscriber.kt`:

```kotlin
package com.example.mypodcast.data.transcription

import com.example.mypodcast.domain.model.TranscriptCue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface TranscriberEvent {
    /** A finalized cue with byte-clock timestamps. */
    data class Cue(val cue: TranscriptCue) : TranscriberEvent

    /** Audio fed up to this media position (also advances through silence). */
    data class Progress(val upToMs: Long) : TranscriberEvent

    /** The whole file (from startMs to EOF) has been transcribed. */
    data object Completed : TranscriberEvent
}

/**
 * Drives one transcription pass: pulls paced PCM from a [PcmSource], feeds an
 * engine session, and stamps each finalized segment with timestamps derived
 * from the amount of audio fed so far ([PcmChunk.endPositionMs]). Cues are
 * contiguous: each starts where the previous one ended.
 */
class EpisodeTranscriber(private val engine: SpeechTranscriptionEngine) {

    fun transcribe(source: PcmSource, startMs: Long, locale: Locale): Flow<TranscriberEvent> =
        channelFlow {
            engine.openSession(locale).use { session ->
                var lastFedMs = startMs
                var lastCueEndMs = startMs

                val resultsJob = launch {
                    session.results.collect { text ->
                        val trimmed = text.trim()
                        if (trimmed.isNotEmpty()) {
                            val end = lastFedMs
                            send(
                                TranscriberEvent.Cue(
                                    TranscriptCue(startMs = lastCueEndMs, endMs = end, text = trimmed)
                                )
                            )
                            lastCueEndMs = end
                        }
                    }
                }

                source.stream(startMs).collect { chunk ->
                    session.feed(chunk.bytes)
                    lastFedMs = chunk.endPositionMs
                    send(TranscriberEvent.Progress(chunk.endPositionMs))
                }
                session.finish()
                resultsJob.join()
                send(TranscriberEvent.Completed)
            }
        }
}
```

- [ ] **Step 5.5: Run tests, verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.data.transcription.EpisodeTranscriberTest"`
Expected: PASS (5 tests).

- [ ] **Step 5.6: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/data/transcription/ app/src/test/java/com/example/mypodcast/data/transcription/
git commit -m "feat: add engine abstraction and byte-clock episode transcriber"
```

---

### Task 6: TranscriptionSessionManager (state machine)

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/data/transcription/TranscriptionSessionManager.kt`
- Test: `app/src/test/java/com/example/mypodcast/data/transcription/TranscriptionSessionManagerTest.kt`
- Test fixture: extend `TranscriptionFakes.kt` with `FakePlayerRepository`, `FakeLibraryRepository`

- [ ] **Step 6.1: Write the failing tests**

Append to `app/src/test/java/com/example/mypodcast/data/transcription/TranscriptionFakes.kt`:

```kotlin
// --- Session-manager fakes -------------------------------------------------

class FakePlayerRepository : com.example.mypodcast.domain.repository.PlayerRepository {
    val state = kotlinx.coroutines.flow.MutableStateFlow(com.example.mypodcast.domain.model.PlayerState())
    override val playerState: kotlinx.coroutines.flow.StateFlow<com.example.mypodcast.domain.model.PlayerState>
        get() = state

    override fun play(episode: com.example.mypodcast.domain.model.Episode) = Unit
    override fun prepare(episode: com.example.mypodcast.domain.model.Episode) = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun skipForward(seconds: Int) = Unit
    override fun skipBack(seconds: Int) = Unit
    override fun setPlaybackSpeed(speed: Float) = Unit
    override fun setSleepTimer(minutes: Int) = Unit
    override fun cancelSleepTimer() = Unit
    override fun setFavorite(guid: String, isFavorite: Boolean) = Unit
    override fun enqueue(episode: com.example.mypodcast.domain.model.Episode) = Unit
    override fun enqueueNext(episode: com.example.mypodcast.domain.model.Episode) = Unit
    override fun removeFromQueue(guid: String) = Unit
    override fun clearQueue() = Unit
    override fun skipToQueueItem(guid: String) = Unit
    override fun moveQueueItem(fromIndex: Int, toIndex: Int) = Unit
    override fun release() = Unit
}

/** Minimal LibraryRepository fake: only the members the session manager touches. */
class FakeLibraryRepository(
    private val pathByGuid: Map<String, String> = emptyMap()
) : com.example.mypodcast.domain.repository.LibraryRepository {
    val statusUpdates = mutableListOf<Pair<String, com.example.mypodcast.domain.model.TranscriptStatus>>()

    override suspend fun getDownloadedFilePath(episodeGuid: String): String? = pathByGuid[episodeGuid]
    override suspend fun setTranscriptStatus(
        episodeGuid: String,
        status: com.example.mypodcast.domain.model.TranscriptStatus
    ) { statusUpdates.add(episodeGuid to status) }
    override fun observeTranscriptStatuses() =
        kotlinx.coroutines.flow.flowOf(emptyMap<String, com.example.mypodcast.domain.model.TranscriptStatus>())

    // Members unused by the session manager:
    override suspend fun subscribe(podcastId: Long) = Unit
    override suspend fun unsubscribe(podcastId: Long) = Unit
    override fun observeSubscriptions() =
        kotlinx.coroutines.flow.flowOf(emptyList<com.example.mypodcast.domain.model.Podcast>())
    override fun observeIsSubscribed(podcastId: Long) = kotlinx.coroutines.flow.flowOf(false)
    override fun observeDownloadedEpisodes() =
        kotlinx.coroutines.flow.flowOf(emptyList<com.example.mypodcast.domain.model.Episode>())
    override fun observeIsDownloaded(episodeGuid: String) = kotlinx.coroutines.flow.flowOf(false)
    override suspend fun saveDownload(episodeGuid: String, podcastId: Long, localPath: String, sizeBytes: Long) = Unit
    override suspend fun deleteDownload(episodeGuid: String) = Unit
    override suspend fun cleanupOrphanedFiles() = Unit
}
```

> NOTE: `FakeLibraryRepository` must implement every member of the real
> `LibraryRepository` interface — if the interface has members not listed
> here, stub them the same way (`= Unit` / `flowOf(...)`). Check the
> interface before writing the fake.

Create `app/src/test/java/com/example/mypodcast/data/transcription/TranscriptionSessionManagerTest.kt`:

```kotlin
package com.example.mypodcast.data.transcription

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.model.TranscriptStatus
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TranscriptionSessionManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun episode(
        guid: String = "ep-1",
        transcriptUrl: String? = null
    ) = Episode(
        guid = guid,
        podcastId = 1L,
        title = "Episode",
        description = null,
        audioUrl = "https://example.com/$guid.mp3",
        artworkUrl = null,
        publishedAt = 0L,
        durationSeconds = 120,
        fileSizeBytes = 0L,
        transcriptUrl = transcriptUrl
    )

    private fun manager(
        player: FakePlayerRepository,
        library: FakeLibraryRepository,
        engine: FakeSpeechEngine,
        sourcesByPath: Map<String, PcmSource>
    ): Pair<TranscriptionSessionManager, GeneratedTranscriptStore> {
        val store = GeneratedTranscriptStore(tmp.root)
        val mgr = TranscriptionSessionManager(
            playerRepository = player,
            libraryRepository = library,
            store = store,
            engine = engine,
            pcmSourceFactory = object : PcmSourceFactory {
                override fun create(filePath: String): PcmSource =
                    sourcesByPath.getValue(filePath)
            }
        )
        return mgr to store
    }

    @Test
    fun `completes transcription and marks status COMPLETE`() = runTest {
        val player = FakePlayerRepository()
        val library = FakeLibraryRepository(mapOf("ep-1" to "/files/ep-1.mp3"))
        val engine = FakeSpeechEngine(listOf(32_000L to "hello world"))
        val source = FakePcmSource(listOf(32_000 to 1_000L))
        val (mgr, store) = manager(player, library, engine, mapOf("/files/ep-1.mp3" to source))

        mgr.start(this)
        player.state.value = PlayerState(episode = episode(), isPlaying = true)
        advanceUntilIdle()

        val saved = store.read("ep-1")
        assertNotNull(saved)
        assertTrue(saved!!.isComplete)
        assertEquals("hello world", saved.cues.single().text)
        assertEquals(
            listOf("ep-1" to TranscriptStatus.IN_PROGRESS, "ep-1" to TranscriptStatus.COMPLETE),
            library.statusUpdates
        )
        assertEquals(true, mgr.live.value?.isComplete)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `pause persists partial progress and resume continues from saved offset`() = runTest {
        val player = FakePlayerRepository()
        val library = FakeLibraryRepository(mapOf("ep-1" to "/files/ep-1.mp3"))

        // First session: a source that never finishes (large script, slow drip via suspension).
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val hangingSource = object : PcmSource {
            override fun stream(startMs: Long) = kotlinx.coroutines.flow.flow {
                emit(PcmChunk(ByteArray(32_000), 1_000L))
                gate.await() // never completes -> session must be cancelled by pause
            }
        }
        val engine = FakeSpeechEngine(listOf(32_000L to "first part"))
        val (mgr, store) = manager(player, library, engine, mapOf("/files/ep-1.mp3" to hangingSource))

        mgr.start(this)
        player.state.value = PlayerState(episode = episode(), isPlaying = true)
        advanceUntilIdle()
        player.state.value = PlayerState(episode = episode(), isPlaying = false) // pause
        advanceUntilIdle()

        val saved = store.read("ep-1")
        assertNotNull(saved)
        assertEquals(false, saved!!.isComplete)
        assertEquals("first part", saved.cues.single().text)
        assertTrue(saved.transcribedUpToMs >= 1_000L)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `skips episodes with a publisher transcript`() = runTest {
        val player = FakePlayerRepository()
        val library = FakeLibraryRepository(mapOf("ep-1" to "/files/ep-1.mp3"))
        val engine = FakeSpeechEngine(emptyList())
        val (mgr, store) = manager(player, library, engine, emptyMap())

        mgr.start(this)
        player.state.value = PlayerState(
            episode = episode(transcriptUrl = "https://feeds.example.com/t.vtt"),
            isPlaying = true
        )
        advanceUntilIdle()

        assertNull(store.read("ep-1"))
        assertTrue(library.statusUpdates.isEmpty())
        assertTrue(engine.openedSessions.isEmpty())
        coroutineContext.cancelChildren()
    }

    @Test
    fun `skips non-downloaded episodes`() = runTest {
        val player = FakePlayerRepository()
        val library = FakeLibraryRepository(emptyMap()) // nothing downloaded
        val engine = FakeSpeechEngine(emptyList())
        val (mgr, store) = manager(player, library, engine, emptyMap())

        mgr.start(this)
        player.state.value = PlayerState(episode = episode(), isPlaying = true)
        advanceUntilIdle()

        assertNull(store.read("ep-1"))
        assertTrue(engine.openedSessions.isEmpty())
        coroutineContext.cancelChildren()
    }

    @Test
    fun `silently skips when engine unavailable`() = runTest {
        val player = FakePlayerRepository()
        val library = FakeLibraryRepository(mapOf("ep-1" to "/files/ep-1.mp3"))
        val engine = FakeSpeechEngine(emptyList(), availability = EngineAvailability.UNAVAILABLE)
        val (mgr, store) = manager(player, library, engine, emptyMap())

        mgr.start(this)
        player.state.value = PlayerState(episode = episode(), isPlaying = true)
        advanceUntilIdle()

        assertNull(store.read("ep-1"))
        assertTrue(library.statusUpdates.isEmpty())
        coroutineContext.cancelChildren()
    }

    @Test
    fun `requests model download once when downloadable, does not transcribe`() = runTest {
        val player = FakePlayerRepository()
        val library = FakeLibraryRepository(mapOf("ep-1" to "/files/ep-1.mp3"))
        val engine = FakeSpeechEngine(emptyList(), availability = EngineAvailability.DOWNLOADABLE)
        val (mgr, _) = manager(player, library, engine, emptyMap())

        mgr.start(this)
        player.state.value = PlayerState(episode = episode(), isPlaying = true)
        advanceUntilIdle()

        assertEquals(1, engine.modelDownloadRequests)
        assertTrue(engine.openedSessions.isEmpty())
        coroutineContext.cancelChildren()
    }

    @Test
    fun `does not rerun an already complete transcript`() = runTest {
        val player = FakePlayerRepository()
        val library = FakeLibraryRepository(mapOf("ep-1" to "/files/ep-1.mp3"))
        val engine = FakeSpeechEngine(emptyList())
        val (mgr, store) = manager(player, library, engine, emptyMap())
        store.write(
            "ep-1",
            GeneratedTranscript(emptyList(), 120_000L, isComplete = true, engineVersion = "x")
        )

        mgr.start(this)
        player.state.value = PlayerState(episode = episode(), isPlaying = true)
        advanceUntilIdle()

        assertTrue(engine.openedSessions.isEmpty())
        coroutineContext.cancelChildren()
    }

    @Test
    fun `engine error persists progress without crashing`() = runTest {
        val player = FakePlayerRepository()
        val library = FakeLibraryRepository(mapOf("ep-1" to "/files/ep-1.mp3"))
        val engine = FakeSpeechEngine(emptyList(), failOnFeed = true)
        val source = FakePcmSource(listOf(32_000 to 1_000L))
        val (mgr, store) = manager(player, library, engine, mapOf("/files/ep-1.mp3" to source))

        mgr.start(this)
        player.state.value = PlayerState(episode = episode(), isPlaying = true)
        advanceUntilIdle()

        val saved = store.read("ep-1")
        assertNotNull(saved) // progress file exists, not complete
        assertEquals(false, saved!!.isComplete)
        coroutineContext.cancelChildren()
    }
}
```

- [ ] **Step 6.2: Run tests, verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.data.transcription.TranscriptionSessionManagerTest"`
Expected: FAIL — `TranscriptionSessionManager` unresolved.

- [ ] **Step 6.3: Implement the session manager**

Create `app/src/main/java/com/example/mypodcast/data/transcription/TranscriptionSessionManager.kt`:

```kotlin
package com.example.mypodcast.data.transcription

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.TranscriptStatus
import com.example.mypodcast.domain.repository.LibraryRepository
import com.example.mypodcast.domain.repository.PlayerRepository
import com.example.mypodcast.domain.transcription.LiveTranscription
import com.example.mypodcast.domain.transcription.TranscriptionMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches playback and runs on-device transcript generation for downloaded,
 * transcript-less episodes while they play ("Variant 2" of the design spec:
 * an independent paced decode anchored to the playback session, not a tap of
 * the playback audio).
 *
 * Hosted by PlaybackService: [start] in onCreate, [stop] in onDestroy.
 */
@Singleton
class TranscriptionSessionManager @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val libraryRepository: LibraryRepository,
    private val store: GeneratedTranscriptStore,
    private val engine: SpeechTranscriptionEngine,
    private val pcmSourceFactory: PcmSourceFactory
) : TranscriptionMonitor {

    private val _live = MutableStateFlow<LiveTranscription?>(null)
    override val live: StateFlow<LiveTranscription?> = _live.asStateFlow()

    private var watchJob: Job? = null
    private var cachedAvailability: EngineAvailability? = null

    fun start(scope: CoroutineScope) {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch {
            playerRepository.playerState
                .map { state ->
                    state.episode?.takeIf {
                        state.isPlaying && it.transcriptUrl.isNullOrBlank()
                    }
                }
                .distinctUntilChangedBy { it?.guid }
                .collectLatest { episode ->
                    // collectLatest cancels the running session on pause/stop/switch.
                    if (episode != null) runSession(episode)
                }
        }
    }

    fun stop() {
        watchJob?.cancel()
        watchJob = null
    }

    private suspend fun runSession(episode: Episode) {
        val filePath = libraryRepository.getDownloadedFilePath(episode.guid) ?: return
        if (!engineReady()) return
        val resumed = store.read(episode.guid)
        if (resumed?.isComplete == true) return

        var cues = resumed?.cues.orEmpty()
        var upToMs = resumed?.transcribedUpToMs ?: 0L
        var unsavedCues = 0
        var lastPersistedMs = upToMs

        libraryRepository.setTranscriptStatus(episode.guid, TranscriptStatus.IN_PROGRESS)
        _live.value = LiveTranscription(episode.guid, cues, upToMs, isComplete = false)

        suspend fun persist(isComplete: Boolean) {
            store.write(
                episode.guid,
                GeneratedTranscript(cues, upToMs, isComplete, SpeechTranscriptionEngine.VERSION)
            )
            unsavedCues = 0
            lastPersistedMs = upToMs
        }

        try {
            EpisodeTranscriber(engine)
                .transcribe(pcmSourceFactory.create(filePath), startMs = upToMs, locale = Locale.getDefault())
                .collect { event ->
                    when (event) {
                        is TranscriberEvent.Cue -> {
                            cues = cues + event.cue
                            upToMs = maxOf(upToMs, event.cue.endMs)
                            unsavedCues++
                            _live.value =
                                LiveTranscription(episode.guid, cues, upToMs, isComplete = false)
                        }

                        is TranscriberEvent.Progress -> {
                            upToMs = maxOf(upToMs, event.upToMs)
                            _live.value =
                                LiveTranscription(episode.guid, cues, upToMs, isComplete = false)
                        }

                        TranscriberEvent.Completed -> {
                            persist(isComplete = true)
                            libraryRepository.setTranscriptStatus(
                                episode.guid, TranscriptStatus.COMPLETE
                            )
                            _live.value =
                                LiveTranscription(episode.guid, cues, upToMs, isComplete = true)
                        }
                    }
                    if (unsavedCues >= PERSIST_EVERY_CUES ||
                        (upToMs - lastPersistedMs >= PERSIST_EVERY_MS && (unsavedCues > 0 || cues.isEmpty()))
                    ) {
                        persist(isComplete = false)
                    }
                }
        } catch (e: kotlinx.coroutines.CancellationException) {
            persist(isComplete = false)
            throw e
        } catch (_: Exception) {
            // Engine/decoder failure: keep what we have; retry next playback session.
            persist(isComplete = false)
        }
    }

    /**
     * AVAILABLE -> proceed. DOWNLOADABLE -> request the model once, skip for
     * now (re-check on next session). DOWNLOADING/UNAVAILABLE -> skip silently.
     */
    private suspend fun engineReady(): Boolean {
        val status = cachedAvailability ?: engine.checkAvailability()
        return when (status) {
            EngineAvailability.AVAILABLE -> {
                cachedAvailability = EngineAvailability.AVAILABLE
                true
            }
            EngineAvailability.DOWNLOADABLE -> {
                runCatching { engine.requestModelDownload() }
                cachedAvailability = null // re-check next time
                false
            }
            EngineAvailability.DOWNLOADING -> {
                cachedAvailability = null
                false
            }
            EngineAvailability.UNAVAILABLE -> {
                cachedAvailability = EngineAvailability.UNAVAILABLE
                false
            }
        }
    }

    private companion object {
        const val PERSIST_EVERY_CUES = 15
        const val PERSIST_EVERY_MS = 30_000L
    }
}
```

Note on the persist-cadence condition: progress through silence (no cues) also persists every 30s so resume skips silent stretches — but only when there are zero cues yet or unsaved cues exist; the final state is always persisted on completion/cancel/error anyway. If a test in 6.1 contradicts this exact condition, simplify the condition to `unsavedCues >= PERSIST_EVERY_CUES || upToMs - lastPersistedMs >= PERSIST_EVERY_MS` — the tests are the contract, the cadence detail is not.

- [ ] **Step 6.4: Run tests, verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.data.transcription.TranscriptionSessionManagerTest"`
Expected: PASS (8 tests). Also rerun `EpisodeTranscriberTest` (shared fakes changed): PASS.

- [ ] **Step 6.5: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/data/transcription/TranscriptionSessionManager.kt app/src/test/java/com/example/mypodcast/data/transcription/
git commit -m "feat: add playback-anchored transcription session manager"
```

---

### Task 7: Mp3PcmStreamer (MediaCodec decode + real-time pacing)

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/data/transcription/Mp3PcmStreamer.kt`

Android framework classes (`MediaExtractor`/`MediaCodec`) — no JVM unit test; the pure math it delegates to is already tested (Task 2). Verified on device in Task 12.

- [ ] **Step 7.1: Implement the streamer**

Create `app/src/main/java/com/example/mypodcast/data/transcription/Mp3PcmStreamer.kt`:

```kotlin
package com.example.mypodcast.data.transcription

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder
import javax.inject.Inject

/**
 * Decodes a local audio file with MediaExtractor/MediaCodec, converts the
 * output to the recognizer's format (16-bit mono 16 kHz PCM) and emits it in
 * ~100 ms chunks paced at real-time rate, as the ML Kit alpha API requires.
 * Supports starting from an arbitrary media position for resume.
 */
class Mp3PcmStreamer(private val filePath: String) : PcmSource {

    class Factory @Inject constructor() : PcmSourceFactory {
        override fun create(filePath: String): PcmSource = Mp3PcmStreamer(filePath)
    }

    override fun stream(startMs: Long): Flow<PcmChunk> = flow {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(filePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("No audio track in $filePath")
            extractor.selectTrack(trackIndex)
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val format = extractor.getTrackFormat(trackIndex)
            val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val pending = ByteArrayOutputStream(TARGET_CHUNK_BYTES * 2)
            var emittedBytes = 0L
            val startedAtNanos = System.nanoTime()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            suspend fun emitPending(flush: Boolean) {
                val data = pending.toByteArray()
                var offset = 0
                while (data.size - offset >= TARGET_CHUNK_BYTES || (flush && offset < data.size)) {
                    val len = minOf(TARGET_CHUNK_BYTES, data.size - offset)
                    val chunk = data.copyOfRange(offset, offset + len)
                    offset += len
                    emittedBytes += len
                    // Real-time pacing against the byte clock, keeping ~200 ms headroom.
                    val mediaAheadMs = pcm16BytesToMs(emittedBytes) -
                        (System.nanoTime() - startedAtNanos) / 1_000_000
                    if (mediaAheadMs > PACING_HEADROOM_MS) delay(mediaAheadMs - PACING_HEADROOM_MS)
                    emit(PcmChunk(chunk, startMs + pcm16BytesToMs(emittedBytes)))
                }
                pending.reset()
                pending.write(data, offset, data.size - offset)
            }

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inIndex, 0, sampleSize, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                if (outIndex >= 0) {
                    val isEos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    // Drop pre-roll decoded before the requested start position
                    // (seekTo lands on the previous sync frame).
                    if (bufferInfo.size > 0 && bufferInfo.presentationTimeUs >= startMs * 1000) {
                        val outBuffer = codec.getOutputBuffer(outIndex)!!
                        val outFormat = codec.getOutputFormat(outIndex)
                        val sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                        val shorts = ShortArray(bufferInfo.size / 2)
                        outBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                        val mono = downmixToMono(shorts, channels)
                        val resampled = resampleLinear(mono, sampleRate, TARGET_SAMPLE_RATE)
                        val bytes = ByteArray(resampled.size * 2)
                        java.nio.ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer().put(resampled)
                        pending.write(bytes)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    emitPending(flush = isEos)
                    if (isEos) outputDone = true
                }
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        /** 100 ms of target-format PCM. */
        const val TARGET_CHUNK_BYTES = TARGET_BYTES_PER_SECOND / 10
        const val PACING_HEADROOM_MS = 200L
        const val DEQUEUE_TIMEOUT_US = 10_000L
    }
}
```

- [ ] **Step 7.2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7.3: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/data/transcription/Mp3PcmStreamer.kt
git commit -m "feat: add MediaCodec decoder with real-time paced 16kHz mono output"
```

---

### Task 8: MlKitSpeechEngine + Hilt module + PlaybackService hookup

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/data/transcription/MlKitSpeechEngine.kt`
- Create: `app/src/main/java/com/example/mypodcast/di/TranscriptionModule.kt`
- Modify: `app/src/main/java/com/example/mypodcast/media/PlaybackService.kt`
- Modify: `app/src/main/java/com/example/mypodcast/di/DatabaseModule.kt` (remove the temporary store provider if it was added in 4.7; the permanent one lives in TranscriptionModule)

> **ALPHA-API CAUTION:** `genai-speech-recognition:1.0.0-alpha1` is alpha. The
> code below follows the documented surface (SpeechRecognizer / Options /
> Request, `AudioSource.fromPfd`, `checkStatus()` returning FeatureStatus,
> streamed partial-then-final results). Before assuming compile errors are
> your fault, open the artifact's classes (External Libraries in the IDE, or
> unzip the AAR) and align names. Confine ALL adjustments to this one file —
> that is the point of the wrapper.

- [ ] **Step 8.1: Implement the ML Kit wrapper**

Create `app/src/main/java/com/example/mypodcast/data/transcription/MlKitSpeechEngine.kt`:

```kotlin
package com.example.mypodcast.data.transcription

import android.os.Build
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.Locale
import javax.inject.Inject

// ML Kit GenAI Speech Recognition (alpha). Align these imports with the
// actual artifact if they differ — keep changes confined to this file.
import com.google.mlkit.genai.speech.SpeechRecognition
import com.google.mlkit.genai.speech.SpeechRecognizer
import com.google.mlkit.genai.speech.SpeechRecognizerOptions
import com.google.mlkit.genai.speech.SpeechRecognizerRequest
import com.google.mlkit.genai.speech.AudioSource
import com.google.mlkit.genai.speech.FeatureStatus

class MlKitSpeechEngine @Inject constructor() : SpeechTranscriptionEngine {

    override suspend fun checkAvailability(): EngineAvailability {
        if (Build.VERSION.SDK_INT < MIN_API) return EngineAvailability.UNAVAILABLE
        return runCatching {
            val recognizer = SpeechRecognition.getClient(SpeechRecognizerOptions.builder().build())
            try {
                when (recognizer.checkStatus().await()) {
                    FeatureStatus.AVAILABLE -> EngineAvailability.AVAILABLE
                    FeatureStatus.DOWNLOADABLE -> EngineAvailability.DOWNLOADABLE
                    FeatureStatus.DOWNLOADING -> EngineAvailability.DOWNLOADING
                    else -> EngineAvailability.UNAVAILABLE
                }
            } finally {
                recognizer.close()
            }
        }.getOrDefault(EngineAvailability.UNAVAILABLE)
    }

    override suspend fun requestModelDownload() {
        if (Build.VERSION.SDK_INT < MIN_API) return
        runCatching {
            val recognizer = SpeechRecognition.getClient(SpeechRecognizerOptions.builder().build())
            try {
                recognizer.download().await()
            } finally {
                recognizer.close()
            }
        }
    }

    override fun openSession(locale: Locale): SpeechSession = MlKitSpeechSession(locale)

    private companion object {
        const val MIN_API = 31
    }
}

private class MlKitSpeechSession(private val locale: Locale) : SpeechSession {

    private val recognizer: SpeechRecognizer =
        SpeechRecognition.getClient(
            SpeechRecognizerOptions.builder()
                .setLocale(locale)
                .build()
        )
    private val pipe: Array<ParcelFileDescriptor> = ParcelFileDescriptor.createPipe()
    private val readSide: ParcelFileDescriptor = pipe[0]
    private val writeStream: OutputStream =
        ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])

    override val results: Flow<String> = callbackFlow {
        val request = SpeechRecognizerRequest.builder(AudioSource.fromPfd(readSide)).build()
        recognizer.startRecognition(
            request,
            { response ->
                // Only forward finalized content; partials are superseded.
                if (response.isFinal) trySend(response.text)
            },
            { error -> close(error) },
            { close() } // recognition completed
        )
        awaitClose { runCatching { recognizer.stopRecognition() } }
    }

    override suspend fun feed(chunk: ByteArray) = withContext(Dispatchers.IO) {
        writeStream.write(chunk)
    }

    override suspend fun finish() = withContext(Dispatchers.IO) {
        // Closing the write end signals EOF; the recognizer flushes and completes.
        writeStream.close()
    }

    override fun close() {
        runCatching { writeStream.close() }
        runCatching { readSide.close() }
        runCatching { recognizer.close() }
    }
}
```

If `kotlinx-coroutines-tasks` (`.await()` on Task) is not already on the classpath via transitive deps, add to the catalog `kotlinx-coroutines-play-services = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-play-services", version.ref = "coroutines" }` and `implementation(libs.kotlinx.coroutines.play.services)` to `app/build.gradle.kts`.

- [ ] **Step 8.2: Create the Hilt module**

Create `app/src/main/java/com/example/mypodcast/di/TranscriptionModule.kt`:

```kotlin
package com.example.mypodcast.di

import android.content.Context
import com.example.mypodcast.data.transcription.GeneratedTranscriptStore
import com.example.mypodcast.data.transcription.MlKitSpeechEngine
import com.example.mypodcast.data.transcription.Mp3PcmStreamer
import com.example.mypodcast.data.transcription.PcmSourceFactory
import com.example.mypodcast.data.transcription.SpeechTranscriptionEngine
import com.example.mypodcast.data.transcription.TranscriptionSessionManager
import com.example.mypodcast.domain.transcription.TranscriptionMonitor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TranscriptionModule {

    @Binds
    @Singleton
    abstract fun bindSpeechEngine(impl: MlKitSpeechEngine): SpeechTranscriptionEngine

    @Binds
    @Singleton
    abstract fun bindPcmSourceFactory(impl: Mp3PcmStreamer.Factory): PcmSourceFactory

    @Binds
    abstract fun bindTranscriptionMonitor(impl: TranscriptionSessionManager): TranscriptionMonitor

    companion object {
        @Provides
        @Singleton
        fun provideGeneratedTranscriptStore(@ApplicationContext context: Context): GeneratedTranscriptStore =
            GeneratedTranscriptStore(context.filesDir)
    }
}
```

(If 4.7 added a `provideGeneratedTranscriptStore` to `DatabaseModule`, delete it there now — exactly one provider must exist.)

- [ ] **Step 8.3: Host the manager in PlaybackService**

In `media/PlaybackService.kt`:

1. Add imports:

```kotlin
import com.example.mypodcast.data.transcription.TranscriptionSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
```

2. Add fields next to `playerController`:

```kotlin
@Inject
lateinit var transcriptionSessionManager: TranscriptionSessionManager

private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
```

3. At the end of `onCreate()` (after the `session.player.addListener(...)` block):

```kotlin
transcriptionSessionManager.start(serviceScope)
```

4. In `onDestroy()`, before `super.onDestroy()`:

```kotlin
transcriptionSessionManager.stop()
serviceScope.cancel()
```

- [ ] **Step 8.4: Full build (Hilt graph validation)**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. This validates the complete DI graph including LibraryRepositoryImpl's new store parameter. If ML Kit symbols fail to compile, fix names inside `MlKitSpeechEngine.kt` only (see ALPHA-API CAUTION above).

- [ ] **Step 8.5: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/data/transcription/MlKitSpeechEngine.kt app/src/main/java/com/example/mypodcast/di/ app/src/main/java/com/example/mypodcast/media/PlaybackService.kt gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: wire ML Kit engine, Hilt module, and PlaybackService host"
```

---

### Task 9: TranscriptRepositoryImpl generated-store fallback

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/data/repository/TranscriptRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/mypodcast/di/TranscriptionModule.kt` (qualifier + files-dir provider)
- Test: `app/src/test/java/com/example/mypodcast/data/repository/TranscriptRepositoryImplTest.kt`

- [ ] **Step 9.1: Write the failing tests**

The impl currently takes `@ApplicationContext Context` only to derive `filesDir` — replace that parameter with a qualified `File` so the class is constructible on the JVM.

Create `app/src/test/java/com/example/mypodcast/data/repository/TranscriptRepositoryImplTest.kt`:

```kotlin
package com.example.mypodcast.data.repository

import com.example.mypodcast.data.transcription.GeneratedTranscript
import com.example.mypodcast.data.transcription.GeneratedTranscriptStore
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.TranscriptCue
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TranscriptRepositoryImplTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun episode(transcriptUrl: String? = null) = Episode(
        guid = "ep-1",
        podcastId = 1L,
        title = "Episode",
        description = null,
        audioUrl = "https://example.com/ep.mp3",
        artworkUrl = null,
        publishedAt = 0L,
        durationSeconds = 120,
        fileSizeBytes = 0L,
        transcriptUrl = transcriptUrl
    )

    private fun repository(store: GeneratedTranscriptStore) = TranscriptRepositoryImpl(
        filesDir = tmp.root,
        okHttpClient = OkHttpClient(),
        generatedTranscriptStore = store
    )

    @Test
    fun `no publisher url and no generated transcript yields empty unsynced`() = runTest {
        val result = repository(GeneratedTranscriptStore(tmp.root)).getTranscript(episode())

        val transcript = result.getOrThrow()
        assertTrue(transcript.cues.isEmpty())
        assertEquals(false, transcript.isSynced)
    }

    @Test
    fun `partial generated transcript is synced with coverage marker`() = runTest {
        val store = GeneratedTranscriptStore(tmp.root)
        store.write(
            "ep-1",
            GeneratedTranscript(
                cues = listOf(TranscriptCue(0, 5_000, "Generated words")),
                transcribedUpToMs = 5_000,
                isComplete = false,
                engineVersion = "x"
            )
        )

        val transcript = repository(store).getTranscript(episode()).getOrThrow()

        assertEquals("Generated words", transcript.cues.single().text)
        assertTrue(transcript.isSynced)
        assertEquals(5_000L, transcript.transcribedUpToMs)
    }

    @Test
    fun `complete generated transcript has null coverage marker`() = runTest {
        val store = GeneratedTranscriptStore(tmp.root)
        store.write(
            "ep-1",
            GeneratedTranscript(
                cues = listOf(TranscriptCue(0, 5_000, "Done")),
                transcribedUpToMs = 120_000,
                isComplete = true,
                engineVersion = "x"
            )
        )

        val transcript = repository(store).getTranscript(episode()).getOrThrow()

        assertNull(transcript.transcribedUpToMs)
    }

    @Test
    fun `publisher transcript wins - generated store not consulted when url present`() = runTest {
        // Pre-seed the publisher cache file so no network call happens
        // (cache path: <filesDir>/transcripts/<guid>, raw VTT body).
        val cacheDir = java.io.File(tmp.root, "transcripts").apply { mkdirs() }
        java.io.File(cacheDir, "ep-1").writeText(
            "WEBVTT\n\n00:00:00.000 --> 00:00:04.000\nPublisher cue\n"
        )
        val store = GeneratedTranscriptStore(tmp.root)
        store.write(
            "ep-1",
            GeneratedTranscript(
                cues = listOf(TranscriptCue(0, 1_000, "Generated cue")),
                transcribedUpToMs = 1_000,
                isComplete = false,
                engineVersion = "x"
            )
        )

        val transcript = repository(store)
            .getTranscript(episode(transcriptUrl = "https://feeds.example.com/t.vtt"))
            .getOrThrow()

        assertEquals("Publisher cue", transcript.cues.single().text)
        assertNull(transcript.transcribedUpToMs)
    }
}
```

- [ ] **Step 9.2: Run tests, verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.data.repository.TranscriptRepositoryImplTest"`
Expected: FAIL — constructor mismatch (`filesDir`, `generatedTranscriptStore` parameters don't exist yet).

- [ ] **Step 9.3: Refactor the repository**

In `data/repository/TranscriptRepositoryImpl.kt`:

1. Replace the constructor and the blank-URL branch:

```kotlin
class TranscriptRepositoryImpl @Inject constructor(
    @TranscriptFilesDir private val filesDir: File,
    private val okHttpClient: OkHttpClient,
    private val generatedTranscriptStore: GeneratedTranscriptStore
) : TranscriptRepository {

    override suspend fun getTranscript(episode: Episode): Result<Transcript> =
        withContext(Dispatchers.IO) {
            val url = episode.transcriptUrl?.takeIf { it.isNotBlank() }
                ?: return@withContext Result.success(generatedTranscript(episode.guid))
            runCatching {
                val raw = cachedBody(episode.guid) ?: downloadBody(episode.guid, url)
                parse(raw, transcriptFormatOf(episode.transcriptType, url))
            }
        }

    /** On-device generated transcript, or the empty placeholder when absent. */
    private fun generatedTranscript(guid: String): Transcript {
        val generated = generatedTranscriptStore.read(guid)
            ?: return Transcript(emptyList(), isSynced = false)
        return Transcript(
            cues = generated.cues,
            isSynced = true,
            transcribedUpToMs = generated.transcribedUpToMs.takeIf { !generated.isComplete }
        )
    }
```

2. Replace `File(context.filesDir, ...)` with `File(filesDir, ...)` in `cacheFile`, remove the `@ApplicationContext private val context: Context` parameter and the now-unused `Context`/`ApplicationContext` imports, and add imports for `GeneratedTranscriptStore` and `TranscriptFilesDir`. Update the class KDoc to mention the generated-store fallback.

3. Add the qualifier + provider in `di/TranscriptionModule.kt`:

```kotlin
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TranscriptFilesDir
```

(Top level in the same file, outside the module class.) And inside the module's `companion object`:

```kotlin
@Provides
@Singleton
@TranscriptFilesDir
fun provideTranscriptFilesDir(@ApplicationContext context: Context): File = context.filesDir
```

(Import `java.io.File`. The qualifier import in the repository is `com.example.mypodcast.di.TranscriptFilesDir`.)

- [ ] **Step 9.4: Run tests, verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.data.repository.TranscriptRepositoryImplTest"`
Expected: PASS (4 tests).

- [ ] **Step 9.5: Build + commit**

Run: `./gradlew :app:assembleDebug` — Expected: BUILD SUCCESSFUL.

```bash
git add app/src/main/java/com/example/mypodcast/data/repository/TranscriptRepositoryImpl.kt app/src/main/java/com/example/mypodcast/di/TranscriptionModule.kt app/src/test/java/com/example/mypodcast/data/repository/TranscriptRepositoryImplTest.kt
git commit -m "feat: serve generated transcripts from TranscriptRepository fallback"
```

---

### Task 10: Player UI — live transcript + coverage footer

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/player/PlayerViewModel.kt`
- Modify: `app/src/main/java/com/example/mypodcast/ui/player/TranscriptPage.kt`

No new JVM tests (Compose UI + ViewModel flow-merging glue; the underlying sources are tested in Tasks 5/6/9, matching the spec's testing scope).

- [ ] **Step 10.1: Merge live session into PlayerViewModel**

In `ui/player/PlayerViewModel.kt`:

1. Add constructor parameter `private val transcriptionMonitor: TranscriptionMonitor` (import `com.example.mypodcast.domain.transcription.TranscriptionMonitor` and `kotlinx.coroutines.flow.filter`).

2. Replace the body of `loadTranscript` (keep signature and reuse-guard):

```kotlin
fun loadTranscript(episode: Episode?) {
    if (episode == null) return
    if (episode.guid == loadedTranscriptGuid &&
        _transcriptState.value !is TranscriptUiState.Error
    ) return

    loadedTranscriptGuid = episode.guid
    transcriptJob?.cancel()
    _transcriptState.value = TranscriptUiState.Loading

    transcriptJob = viewModelScope.launch {
        if (!episode.transcriptUrl.isNullOrBlank()) {
            _transcriptState.value = getTranscript(episode).fold(
                onSuccess = { transcript ->
                    if (transcript.cues.isEmpty()) TranscriptUiState.Empty
                    else TranscriptUiState.Loaded(transcript)
                },
                onFailure = { TranscriptUiState.Error(it.message ?: "Couldn't load transcript") }
            )
            return@launch
        }

        // No publisher transcript: show what's been generated so far, then
        // follow the live on-device session for this episode.
        val stored = getTranscript(episode).getOrNull()
        _transcriptState.value = stored
            ?.takeIf { it.cues.isNotEmpty() }
            ?.let { TranscriptUiState.Loaded(it) }
            ?: TranscriptUiState.Empty

        transcriptionMonitor.live
            .filter { it != null && it.episodeGuid == episode.guid }
            .collect { live ->
                if (live!!.cues.isNotEmpty()) {
                    _transcriptState.value = TranscriptUiState.Loaded(
                        Transcript(
                            cues = live.cues,
                            isSynced = true,
                            transcribedUpToMs = live.transcribedUpToMs
                                .takeIf { !live.isComplete }
                        )
                    )
                }
            }
    }
}
```

- [ ] **Step 10.2: Coverage footer + refreshed empty copy in TranscriptPage**

In `ui/player/TranscriptPage.kt`:

1. In the `TranscriptUiState.Loaded` branch, wrap the existing synced/plain split to add a footer when `transcribedUpToMs != null`:

```kotlin
is TranscriptUiState.Loaded -> Column(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f)) {
        if (transcriptState.transcript.isSynced) {
            SyncedTranscript(
                cues = transcriptState.transcript.cues,
                positionMs = positionMs,
                onSeek = onSeek
            )
        } else {
            PlainTranscript(cues = transcriptState.transcript.cues)
        }
    }
    transcriptState.transcript.transcribedUpToMs?.let { upToMs ->
        Text(
            text = "Transcribing as you listen · up to ${formatTranscribedUpTo(upToMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        )
    }
}
```

2. Add the formatter at the bottom of the file:

```kotlin
private fun formatTranscribedUpTo(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
```

3. Update the Empty-state secondary text (the string currently ending in "On-device transcription is coming soon.") to:

```kotlin
text = "Transcripts appear here when the podcast provides them. " +
    "For downloaded episodes, a transcript is generated on this device as you listen.",
```

- [ ] **Step 10.3: Build + run full unit suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 10.4: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/ui/player/
git commit -m "feat: show live generated transcript with coverage footer in player"
```

---

### Task 11: Library badge for completed transcripts

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/library/LibraryViewModel.kt`
- Modify: `app/src/main/java/com/example/mypodcast/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/example/mypodcast/ui/components/EpisodeListItem.kt`

- [ ] **Step 11.1: Surface statuses in LibraryUiState**

In `ui/library/LibraryViewModel.kt`:

1. Add to `LibraryUiState`:

```kotlin
val transcriptReadyGuids: Set<String> = emptySet()
```

2. Add a collector in `init` (alongside the existing `observeDownloads` collector; import `com.example.mypodcast.domain.model.TranscriptStatus`):

```kotlin
viewModelScope.launch {
    libraryRepository.observeTranscriptStatuses().collect { statuses ->
        _uiState.update {
            it.copy(
                transcriptReadyGuids = statuses
                    .filterValues { status -> status == TranscriptStatus.COMPLETE }
                    .keys
            )
        }
    }
}
```

- [ ] **Step 11.2: Add the badge to EpisodeListItem**

In `ui/components/EpisodeListItem.kt`:

1. Add parameter (after `isDownloaded: Boolean = false`):

```kotlin
hasTranscript: Boolean = false,
```

2. In the metadata `Row` (where the favorite/played icons render, before the date `Text`), add:

```kotlin
if (hasTranscript) {
    Icon(
        Icons.Filled.Subtitles,
        contentDescription = "Transcript available",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(14.dp)
    )
    Spacer(Modifier.width(4.dp))
}
```

3. Add import `androidx.compose.material.icons.filled.Subtitles`.

- [ ] **Step 11.3: Wire the Downloads tab call site**

In `ui/library/LibraryScreen.kt`, the Downloads-tab `EpisodeListItem(` call (~line 312) gains:

```kotlin
hasTranscript = episode.guid in uiState.transcriptReadyGuids,
```

(Match the variable name for the collected state in that scope — the screen collects `viewModel.uiState`; if the local is named differently, e.g. `state`, use that.) If the Downloads search results also render `EpisodeListItem` for downloads, add the same parameter there.

- [ ] **Step 11.4: Build + commit**

Run: `./gradlew :app:assembleDebug` — Expected: BUILD SUCCESSFUL.

```bash
git add app/src/main/java/com/example/mypodcast/ui/library/ app/src/main/java/com/example/mypodcast/ui/components/EpisodeListItem.kt
git commit -m "feat: show transcript badge on downloaded episodes"
```

---

### Task 12: Final verification

- [ ] **Step 12.1: Full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, zero failures. (This includes the pre-existing source tests — e.g. `NavigationSourceTest`, `QueueScreenSourceTest` — which must still pass.)

- [ ] **Step 12.2: Full debug build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 12.3: Summarize device-only verification for the user**

The following cannot be verified on the JVM and needs a physical API 31+ device (ideally also one below API 31 for the silent-skip path):

1. ML Kit model availability/download flow (`MlKitSpeechEngine.checkAvailability`).
2. End-to-end: download an episode without a publisher transcript, play it, watch cues appear on the transcript page with the "Transcribing as you listen" footer.
3. Pause/resume continues from the saved offset (kill the app between sessions to verify persistence).
4. Library badge appears after a full listen-through.
5. Episodes WITH publisher transcripts are untouched (no status writes, no generated files).
6. Real-time pacing holds on long episodes without `MediaCodec` errors.

- [ ] **Step 12.4: Verify no system notifications were introduced**

Run: `grep -rn "NotificationCompat\|NotificationManager" app/src/main/java/com/example/mypodcast/data/transcription/`
Expected: no output. (The spec forbids any notification in this feature; the only notification code in the app remains the pre-existing media notification in `PlaybackService`.)

---

## Spec coverage map (self-review)

| Spec requirement | Task |
|---|---|
| Auto, only when no publisher transcriptUrl | 6 (session gate), 9 (repo precedence) |
| Silent skip on unsupported devices | 6 (`engineReady`), 8 (`MIN_API = 31`) |
| No notifications | 12.4 verification; none added anywhere |
| Badge on Library rows when COMPLETE | 4 (status column), 11 (UI) |
| Live status on transcript page | 10 |
| Runs only while episode plays (Variant 2) | 6 (`collectLatest` on playerState) |
| PCM 16 kHz mono real-time pacing | 2 (math), 7 (streamer) |
| Exact byte-clock timestamps, isSynced=true | 5 (transcriber), 2 (math) |
| Atomic JSON progress persistence | 3 |
| Resume from saved offset | 5 (startMs), 6 (resume read) |
| Engine error → suspend, retry next session | 5 (close test), 6 (error test) |
| Delete download cleans up generated file + status | 4.7 |
| Locale | Device locale (documented deviation — feed language not stored) |
| Engine behind swappable interface | 5, 8 |
| Tests: resampler, byte clock, JSON round-trip, state machine, repo selection, migration | 2, 3, 6, 9, 4 |
