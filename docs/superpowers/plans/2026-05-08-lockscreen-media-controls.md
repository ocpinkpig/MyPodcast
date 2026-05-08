# Lockscreen Media Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface the currently-playing episode on the Android lockscreen via the system media notification — artwork as backdrop, title + podcast subtitle, working play/pause/seek/skip/next controls, and tap-to-open.

**Architecture:** The app already binds a Media3 `MediaSession` inside `PlaybackService`, which Android renders as the lockscreen "now playing" panel. Three small changes complete the experience: (1) attach a `sessionActivity` PendingIntent, (2) enrich `MediaMetadata` with the podcast show name as the artist line, and (3) wrap the ExoPlayer in a `ForwardingPlayer` so the lockscreen "next" button advances through the in-app queue.

**Tech Stack:** Kotlin, Android, Hilt, AndroidX Media3 1.5.1 (`media3-exoplayer`, `media3-session`), Room, Coroutines.

---

## File Structure

**Modify:**
- `app/src/main/java/com/example/mypodcast/media/PlaybackService.kt` — add `sessionActivity` PendingIntent; wrap player in `QueueAwarePlayer` before passing to `MediaSession.Builder`.
- `app/src/main/java/com/example/mypodcast/media/PlayerController.kt` — inject `PodcastRepository` (Lazy); enrich `MediaMetadata` with podcast title (artist) when available; promote `playNextInQueue` visibility so `QueueAwarePlayer` can call it; expose a read-only `hasQueueItems()` accessor.

**Create:**
- `app/src/main/java/com/example/mypodcast/media/QueueAwarePlayer.kt` — `ForwardingPlayer` that advertises and routes "next" commands to `PlayerController.playNextInQueue()`.
- `app/src/test/java/com/example/mypodcast/media/QueueAwarePlayerTest.kt` — unit tests for the forwarding logic.

---

### Task 1: `QueueAwarePlayer` — failing test for `seekToNextMediaItem`

**Files:**
- Test: `app/src/test/java/com/example/mypodcast/media/QueueAwarePlayerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.mypodcast.media

import androidx.media3.common.Player
import androidx.media3.test.utils.StubPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueAwarePlayerTest {

    private class FakeUnderlying : StubPlayer() {
        var seekToNextCalled = 0
        override fun seekToNextMediaItem() { seekToNextCalled++ }
        override fun seekToNext() { seekToNextCalled++ }
        override fun getAvailableCommands(): Player.Commands =
            Player.Commands.Builder().addAllCommands().build()
    }

    @Test
    fun `seekToNextMediaItem invokes onNext callback instead of underlying`() {
        var nextInvocations = 0
        val underlying = FakeUnderlying()
        val player = QueueAwarePlayer(
            wrapped = underlying,
            hasQueueItems = { true },
            onNext = { nextInvocations++ }
        )

        player.seekToNextMediaItem()

        assertEquals(1, nextInvocations)
        assertEquals(0, underlying.seekToNextCalled)
    }
}
```

If `androidx.media3.test.utils.StubPlayer` is not on the classpath, replace `FakeUnderlying` with an anonymous subclass of `androidx.media3.common.SimpleBasePlayer` initialized with the application's Looper, OR with a Mockito mock of `Player`. Prefer `StubPlayer` (add `testImplementation("androidx.media3:media3-test-utils:1.5.1")` to `app/build.gradle.kts` if needed).

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.media.QueueAwarePlayerTest`

Expected: compile failure (`QueueAwarePlayer` does not exist).

- [ ] **Step 3: Create the minimal `QueueAwarePlayer`**

Create `app/src/main/java/com/example/mypodcast/media/QueueAwarePlayer.kt`:

```kotlin
package com.example.mypodcast.media

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

class QueueAwarePlayer(
    wrapped: Player,
    private val hasQueueItems: () -> Boolean,
    private val onNext: () -> Unit
) : ForwardingPlayer(wrapped) {

    override fun seekToNextMediaItem() {
        onNext()
    }

    override fun seekToNext() {
        onNext()
    }

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        return if (hasQueueItems()) {
            Player.Commands.Builder()
                .addAll(base)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .build()
        } else {
            Player.Commands.Builder()
                .addAll(base)
                .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_NEXT)
                .build()
        }
    }

    override fun hasNextMediaItem(): Boolean = hasQueueItems()
}
```

- [ ] **Step 4: Run the test and verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.media.QueueAwarePlayerTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/media/QueueAwarePlayer.kt \
        app/src/test/java/com/example/mypodcast/media/QueueAwarePlayerTest.kt \
        app/build.gradle.kts
git commit -m "Add QueueAwarePlayer to forward 'next' to in-app queue"
```

---

### Task 2: `QueueAwarePlayer` — `hasQueueItems = false` disables next

**Files:**
- Test: `app/src/test/java/com/example/mypodcast/media/QueueAwarePlayerTest.kt`

- [ ] **Step 1: Add the failing test**

Append to `QueueAwarePlayerTest`:

```kotlin
@Test
fun `availableCommands omits next when queue empty`() {
    val player = QueueAwarePlayer(
        wrapped = FakeUnderlying(),
        hasQueueItems = { false },
        onNext = { }
    )

    val commands = player.availableCommands

    assertTrue(!commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
    assertTrue(!commands.contains(Player.COMMAND_SEEK_TO_NEXT))
}

@Test
fun `availableCommands includes next when queue non-empty`() {
    val player = QueueAwarePlayer(
        wrapped = FakeUnderlying(),
        hasQueueItems = { true },
        onNext = { }
    )

    val commands = player.availableCommands

    assertTrue(commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
    assertTrue(commands.contains(Player.COMMAND_SEEK_TO_NEXT))
}
```

- [ ] **Step 2: Run and verify pass (already implemented)**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.mypodcast.media.QueueAwarePlayerTest`

Expected: all three tests PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/example/mypodcast/media/QueueAwarePlayerTest.kt
git commit -m "Test QueueAwarePlayer next-command gating by queue state"
```

---

### Task 3: Expose `playNextInQueue` and `hasQueueItems` on `PlayerController`

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/media/PlayerController.kt`

- [ ] **Step 1: Promote `playNextInQueue` and add `hasQueueItems()`**

In `PlayerController.kt`:

Find:
```kotlin
    private fun playNextInQueue() {
```
Replace with:
```kotlin
    internal fun playNextInQueue() {
```

Add a new public method anywhere in the class body (e.g. just above `release()`):

```kotlin
    fun hasQueueItems(): Boolean = _playerState.value.queue.isNotEmpty()
```

- [ ] **Step 2: Build to confirm it compiles**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/media/PlayerController.kt
git commit -m "Expose playNextInQueue and hasQueueItems for media session"
```

---

### Task 4: Wire `QueueAwarePlayer` into `MediaSession`

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/media/PlaybackService.kt`

- [ ] **Step 1: Wrap the ExoPlayer in `QueueAwarePlayer` when building the session**

In `PlaybackService.kt`, replace the `mediaSession` creation in `onCreate`:

Find:
```kotlin
        mediaSession = MediaSession.Builder(this, playerController.exoPlayer).build()
```
Replace with:
```kotlin
        val queueAwarePlayer = QueueAwarePlayer(
            wrapped = playerController.exoPlayer,
            hasQueueItems = { playerController.hasQueueItems() },
            onNext = { playerController.playNextInQueue() }
        )
        mediaSession = MediaSession.Builder(this, queueAwarePlayer).build()
```

- [ ] **Step 2: Build to confirm it compiles**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/media/PlaybackService.kt
git commit -m "Route MediaSession through QueueAwarePlayer"
```

---

### Task 5: Set `sessionActivity` so the notification opens the app

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/media/PlaybackService.kt`

- [ ] **Step 1: Build a PendingIntent for `MainActivity` and attach it to the session**

In `PlaybackService.kt`, add imports near the top:

```kotlin
import android.app.PendingIntent
import android.content.Intent
import com.example.mypodcast.MainActivity
```

Replace the session-creation block (now using `QueueAwarePlayer`) with:

```kotlin
        val queueAwarePlayer = QueueAwarePlayer(
            wrapped = playerController.exoPlayer,
            hasQueueItems = { playerController.hasQueueItems() },
            onNext = { playerController.playNextInQueue() }
        )
        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(this, queueAwarePlayer)
            .setSessionActivity(sessionActivityIntent)
            .build()
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/media/PlaybackService.kt
git commit -m "Open MainActivity when lockscreen notification is tapped"
```

---

### Task 6: Inject `PodcastRepository` into `PlayerController`

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/media/PlayerController.kt`

`Episode` has `podcastId: Long` but not the show's display name. `PodcastRepository.observePodcast(podcastId)` exposes a `Flow<Podcast?>` we can read once.

- [ ] **Step 1: Add the dependency to the constructor**

In `PlayerController.kt`, locate the constructor:

```kotlin
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sleepTimerManager: SleepTimerManager,
    private val episodeRepository: Lazy<EpisodeRepository>,
    private val queueDao: Lazy<QueueDao>
) {
```

Replace with:

```kotlin
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sleepTimerManager: SleepTimerManager,
    private val episodeRepository: Lazy<EpisodeRepository>,
    private val queueDao: Lazy<QueueDao>,
    private val podcastRepository: Lazy<com.example.mypodcast.domain.repository.PodcastRepository>
) {
```

(Use a top-level import instead of FQCN if you prefer — add `import com.example.mypodcast.domain.repository.PodcastRepository` and drop the package prefix.)

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL — Hilt will provide the dependency automatically because `PodcastRepository` is already bound (verify by checking `RepositoryModule` if compilation fails).

- [ ] **Step 3: Update existing tests if any construct `PlayerController` directly**

Run: `./gradlew :app:testDebugUnitTest`

If tests fail because constructor changed, add a Mockito mock or stub:

```kotlin
val podcastRepository = mock<com.example.mypodcast.domain.repository.PodcastRepository>()
val controller = PlayerController(
    context, sleepTimerManager,
    dagger.Lazy { episodeRepository },
    dagger.Lazy { queueDao },
    dagger.Lazy { podcastRepository }
)
```

Expected: all unit tests PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/media/PlayerController.kt \
        app/src/test/java/com/example/mypodcast/
git commit -m "Inject PodcastRepository into PlayerController"
```

---

### Task 7: Enrich `MediaMetadata` with the podcast title (artist line)

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/media/PlayerController.kt`

Strategy: build the `MediaItem` synchronously with title + artwork (current behavior). Then launch a coroutine that fetches the podcast title and, if found, calls `exoPlayer.replaceMediaItem(0, ...)` with metadata that adds the artist field. The replace happens before audio finishes loading in practice; if it lands after, the lockscreen subtitle just appears slightly later.

- [ ] **Step 1: Extract metadata-building into a helper**

In `PlayerController.kt`, locate `loadEpisode`. Replace the `MediaItem` build block:

Find:
```kotlin
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
```

Replace with:
```kotlin
        val mediaItem = buildMediaItem(episode, podcastTitle = null)
        exoPlayer.setMediaItem(mediaItem)
        loadPodcastTitleIntoMetadata(episode)
```

Then add two private helpers anywhere below `loadEpisode`:

```kotlin
    private fun buildMediaItem(episode: Episode, podcastTitle: String?): MediaItem =
        MediaItem.Builder()
            .setUri(episode.audioUrl)
            .setMediaId(episode.guid)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .apply { if (!podcastTitle.isNullOrBlank()) setArtist(podcastTitle) }
                    .setArtworkUri(episode.artworkUrl?.let { Uri.parse(it) })
                    .build()
            )
            .build()

    private fun loadPodcastTitleIntoMetadata(episode: Episode) {
        scope.launch {
            val podcast = podcastRepository.get()
                .observePodcast(episode.podcastId)
                .firstOrNull { it != null }
                ?: return@launch
            // Bail if the user has moved on to a different episode in the meantime.
            if (currentEpisode?.guid != episode.guid) return@launch
            val updated = buildMediaItem(episode, podcastTitle = podcast.title)
            exoPlayer.replaceMediaItem(0, updated)
        }
    }
```

Add the imports at the top if missing:

```kotlin
import kotlinx.coroutines.flow.firstOrNull
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/media/PlayerController.kt
git commit -m "Show podcast title as subtitle in media metadata"
```

---

### Task 8: On-device verification

The repo's standing rule (per memory): "BUILD SUCCESSFUL" is not proof — install the APK on the connected device and visually verify.

- [ ] **Step 1: Install on the connected device**

Run: `./gradlew :app:installDebug`

Expected: `Installed on 1 device`.

- [ ] **Step 2: Verify each acceptance criterion from the spec**

Open the app on the device. Then:

1. Start an episode playing. Lock the screen.
2. Confirm the lockscreen "now playing" panel shows: artwork (used as the panel's tinted backdrop on most launchers), the episode title, and the podcast show name as the subtitle line.
3. From the lockscreen, exercise: play/pause, scrub the seek bar, skip-15-back, skip-15-forward. All should work.
4. Unlock; enqueue a second episode via the in-app UI; lock again. Tap the lockscreen "next" button → playback advances to the queued episode and the panel updates with the new artwork/title/subtitle.
5. With the queue empty, the next button on the lockscreen is disabled or hidden.
6. Tap the body of the lockscreen notification → the app opens to the player screen.

If any criterion fails, capture the symptom (logcat, screenshot) and decide whether the issue is in metadata (Task 7), forwarding (Tasks 1–4), or session activity (Task 5) before proposing a fix.

- [ ] **Step 3: Commit any small fixes; otherwise this task adds no commits.**

---

## Self-Review Notes

- Spec coverage:
  - "Subtitle on lockscreen" → Task 7.
  - "Tap notification opens app" → Task 5.
  - "Next button advances queue" → Tasks 1–4.
  - "Previous keeps default behavior" → no task needed (default behavior).
  - "No wallpaper changes / no custom NotificationProvider" → enforced by absence; nothing to do.
- Type/name consistency: `QueueAwarePlayer(wrapped, hasQueueItems, onNext)` constructor used identically in tests and `PlaybackService`. `playNextInQueue()` and `hasQueueItems()` names match between `PlayerController` and call sites.
- No placeholders; every code step shows full code.
- DI: `PodcastRepository` is bound elsewhere in the project (verified by existing usage in `PodcastDetailViewModel`); injection via Hilt requires no module changes.
