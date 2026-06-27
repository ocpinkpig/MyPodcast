# Clickable Show Notes Timestamps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make timestamps in an episode's show notes tappable so a tap seeks the player to that position and starts playback.

**Architecture:** A pure Kotlin function detects timestamp substrings in the already-flattened show-notes text and returns their ranges + millisecond positions. `ShowNotesPage` builds an `AnnotatedString` that wraps each detected range in a styled `LinkAnnotation.Clickable`, whose listener calls a new `PlayerViewModel.seekToAndPlay`, which starts/resumes playback then seeks.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), JUnit4. Module: `app`. Tests run with `./gradlew :app:testDebugUnitTest`.

---

## File Structure

- **Create** `app/src/main/java/com/example/mypodcast/ui/player/ShowNotesTimestamps.kt` — pure detector: `TimestampLink` data class + `findTimestampLinks(text)`.
- **Create** `app/src/test/java/com/example/mypodcast/ui/player/ShowNotesTimestampsTest.kt` — unit tests for the detector.
- **Modify** `app/src/main/java/com/example/mypodcast/ui/player/PlayerViewModel.kt` — add `seekToAndPlay(positionMs)`.
- **Modify** `app/src/test/java/com/example/mypodcast/ui/player/PlayerViewModelTest.kt` — track `resume`/`seekTo` in the fake; test `seekToAndPlay`.
- **Modify** `app/src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt` — thread `onSeekAndPlay` through `PlayerScreen` → `PlayerPager` → `ShowNotesPage`; render the annotated string with clickable links.
- **Modify** `app/src/test/java/com/example/mypodcast/ui/player/PlayerScreenSourceTest.kt` — assert the clickable-link wiring exists.

---

## Task 1: Timestamp detector (pure)

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/ui/player/ShowNotesTimestamps.kt`
- Test: `app/src/test/java/com/example/mypodcast/ui/player/ShowNotesTimestampsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/mypodcast/ui/player/ShowNotesTimestampsTest.kt`:

```kotlin
package com.example.mypodcast.ui.player

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class ShowNotesTimestampsTest {

    @Test
    fun detectsMinuteSecond() {
        val text = "Intro 12:34 next"
        val links = findTimestampLinks(text)
        assertEquals(1, links.size)
        val link = links[0]
        assertEquals("12:34", text.substring(link.start, link.endExclusive))
        assertEquals((12 * 60 + 34) * 1000L, link.positionMs)
    }

    @Test
    fun detectsHourMinuteSecond() {
        val text = "Deep dive at 1:02:03 here"
        val links = findTimestampLinks(text)
        assertEquals(1, links.size)
        assertEquals((1 * 3600 + 2 * 60 + 3) * 1000L, links[0].positionMs)
    }

    @Test
    fun detectsZeroPaddedHour() {
        val links = findTimestampLinks("01:02:03")
        assertEquals(1, links.size)
        assertEquals((3600 + 2 * 60 + 3) * 1000L, links[0].positionMs)
    }

    @Test
    fun allowsLargeMinutesInMinuteSecondForm() {
        val links = findTimestampLinks("Marathon 90:00 mark")
        assertEquals(1, links.size)
        assertEquals(90 * 60 * 1000L, links[0].positionMs)
    }

    @Test
    fun excludesSurroundingParensAndBrackets() {
        val text = "Topic (12:34) and [1:00] done"
        val links = findTimestampLinks(text)
        assertEquals(2, links.size)
        assertEquals("12:34", text.substring(links[0].start, links[0].endExclusive))
        assertEquals("1:00", text.substring(links[1].start, links[1].endExclusive))
    }

    @Test
    fun rejectsInvalidSeconds() {
        assertTrue(findTimestampLinks("bad 12:99 value").isEmpty())
    }

    @Test
    fun rejectsTimestampGluedToLargerDigits() {
        // The colon is anchored to a longer digit run, so no valid minute field
        // can be isolated without a preceding digit (rejected by the lookbehind).
        assertTrue(findTimestampLinks("1234:56").isEmpty())
        assertTrue(findTimestampLinks("ref 12:345 here").isEmpty())
    }

    @Test
    fun returnsMultipleInOrder() {
        val links = findTimestampLinks("0:30 intro, 5:00 middle, 1:00:00 end")
        assertEquals(3, links.size)
        assertEquals(30 * 1000L, links[0].positionMs)
        assertEquals(5 * 60 * 1000L, links[1].positionMs)
        assertEquals(3600 * 1000L, links[2].positionMs)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.ui.player.ShowNotesTimestampsTest"`
Expected: FAIL — compile error, `findTimestampLinks` / `TimestampLink` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/example/mypodcast/ui/player/ShowNotesTimestamps.kt`:

```kotlin
package com.example.mypodcast.ui.player

/** A timestamp span found in show-notes text, with its parsed playback position. */
data class TimestampLink(
    val start: Int,
    val endExclusive: Int,
    val positionMs: Long
)

// Matches H:MM:SS / HH:MM:SS or M:SS / MM:SS. Seconds and the middle field of the
// HMS form are constrained to 00-59; the leading field allows 1+ digits so 90:00
// works. Surrounding parens/brackets are tolerated by the boundary guards but are
// not part of the captured range.
private val TIMESTAMP_REGEX =
    Regex("""(?<![\d:])(?:(\d{1,2}):)?(\d{1,3}):([0-5]\d)(?![\d:])""")

/** Finds clickable timestamps in already-flattened (plain-text) show notes. */
fun findTimestampLinks(text: String): List<TimestampLink> =
    TIMESTAMP_REGEX.findAll(text).mapNotNull { match ->
        val hours = match.groupValues[1].toLongOrNull() ?: 0L
        val minutes = match.groupValues[2].toLong()
        val seconds = match.groupValues[3].toLong()
        // In the HH:MM:SS form the minutes field must be a real minute count.
        if (match.groupValues[1].isNotEmpty() && minutes > 59) return@mapNotNull null
        val positionMs = ((hours * 3600) + (minutes * 60) + seconds) * 1000L
        TimestampLink(
            start = match.range.first,
            endExclusive = match.range.last + 1,
            positionMs = positionMs
        )
    }.toList()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.ui.player.ShowNotesTimestampsTest"`
Expected: PASS (all 8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/ui/player/ShowNotesTimestamps.kt \
        app/src/test/java/com/example/mypodcast/ui/player/ShowNotesTimestampsTest.kt
git commit -m "feat: detect timestamps in show notes text"
```

---

## Task 2: `seekToAndPlay` on the ViewModel

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/player/PlayerViewModel.kt` (near line 114, after `seekTo`)
- Test: `app/src/test/java/com/example/mypodcast/ui/player/PlayerViewModelTest.kt`

- [ ] **Step 1: Add tracking to the fake repository, then write the failing tests**

In `PlayerViewModelTest.kt`, find `internal class FakePlayerRepository` (around line 188). Replace its `resume()` and `seekTo()` overrides:

```kotlin
    override fun resume() = Unit
    override fun seekTo(positionMs: Long) = Unit
```

with tracking versions, and add two `var` fields next to `var paused = false`:

```kotlin
    var resumed = false
    var seekedToMs: Long? = null
```

```kotlin
    override fun resume() {
        resumed = true
    }
    override fun seekTo(positionMs: Long) {
        seekedToMs = positionMs
    }
```

Then add these tests to `class PlayerViewModelTest` (place after the existing `playPause_*` tests):

```kotlin
    @Test
    fun seekToAndPlay_resumesPausedCurrentEpisodeThenSeeks() {
        val current = episode("current")
        val repository = FakePlayerRepository(
            PlayerState(episode = current, isPlaying = false)
        )
        val viewModel = PlayerViewModel(
            repository,
            GetTranscriptUseCase(FakeTranscriptRepository()),
            PlayerViewFakeSavedMomentRepository(),
            FakeTranscriptionMonitor(),
            FakeTranscriptionLibraryRepository()
        )

        viewModel.seekToAndPlay(75_000L)

        assertEquals(true, repository.resumed)
        assertEquals(75_000L, repository.seekedToMs)
        assertEquals(null, repository.playedEpisode)
    }

    @Test
    fun seekToAndPlay_startsPreviewEpisodeThenSeeks() {
        val current = episode("current")
        val preview = episode("preview")
        val repository = FakePlayerRepository(
            PlayerState(episode = current, previewEpisode = preview, isPlaying = true)
        )
        val viewModel = PlayerViewModel(
            repository,
            GetTranscriptUseCase(FakeTranscriptRepository()),
            PlayerViewFakeSavedMomentRepository(),
            FakeTranscriptionMonitor(),
            FakeTranscriptionLibraryRepository()
        )

        viewModel.seekToAndPlay(40_000L)

        assertEquals(preview, repository.playedEpisode)
        assertEquals(40_000L, repository.seekedToMs)
    }

    @Test
    fun seekToAndPlay_seeksWithoutResumeWhenAlreadyPlaying() {
        val current = episode("current")
        val repository = FakePlayerRepository(
            PlayerState(episode = current, isPlaying = true)
        )
        val viewModel = PlayerViewModel(
            repository,
            GetTranscriptUseCase(FakeTranscriptRepository()),
            PlayerViewFakeSavedMomentRepository(),
            FakeTranscriptionMonitor(),
            FakeTranscriptionLibraryRepository()
        )

        viewModel.seekToAndPlay(10_000L)

        assertEquals(false, repository.resumed)
        assertEquals(10_000L, repository.seekedToMs)
        assertEquals(null, repository.playedEpisode)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.ui.player.PlayerViewModelTest"`
Expected: FAIL — compile error, `seekToAndPlay` unresolved.

- [ ] **Step 3: Implement `seekToAndPlay`**

In `PlayerViewModel.kt`, immediately after the `seekTo` line (`fun seekTo(positionMs: Long) = playerRepository.seekTo(positionMs)`), add:

```kotlin
    fun seekToAndPlay(positionMs: Long) {
        val state = playerRepository.playerState.value
        val previewEpisode = state.previewEpisode
        if (previewEpisode != null && previewEpisode.guid != state.episode?.guid) {
            playerRepository.play(previewEpisode)
        } else if (!state.isPlaying) {
            playerRepository.resume()
        }
        playerRepository.seekTo(positionMs)
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.ui.player.PlayerViewModelTest"`
Expected: PASS (existing tests + 3 new).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/ui/player/PlayerViewModel.kt \
        app/src/test/java/com/example/mypodcast/ui/player/PlayerViewModelTest.kt
git commit -m "feat: add seekToAndPlay to player view model"
```

---

## Task 3: Render clickable timestamps in `ShowNotesPage`

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt`
- Test: `app/src/test/java/com/example/mypodcast/ui/player/PlayerScreenSourceTest.kt`

- [ ] **Step 1: Write the failing source test**

Add to `class PlayerScreenSourceTest` in `PlayerScreenSourceTest.kt`:

```kotlin
    @Test
    fun showNotesTimestamps_areClickableAndStartPlayback() {
        val source = File("src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt").readText()

        assertTrue(source.contains("findTimestampLinks"))
        assertTrue(source.contains("LinkAnnotation.Clickable"))
        assertTrue(source.contains("onSeekAndPlay"))
        assertTrue(source.contains("viewModel::seekToAndPlay"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.ui.player.PlayerScreenSourceTest"`
Expected: FAIL — assertions false (strings absent).

- [ ] **Step 3: Add imports**

In `PlayerScreen.kt`, add these imports alongside the existing `androidx.compose.ui.*` / `androidx.compose.material3.*` imports (keep the file's existing import grouping):

```kotlin
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
```

- [ ] **Step 4: Thread `onSeekAndPlay` through `PlayerPager` and `ShowNotesPage`**

4a. In the `PlayerPager` call (around line 179), add the parameter right after `onSeek = viewModel::seekTo,`:

```kotlin
            onSeek = viewModel::seekTo,
            onSeekAndPlay = viewModel::seekToAndPlay,
```

4b. In the `PlayerPager` composable signature (around line 231), add after `onSeek: (Long) -> Unit,`:

```kotlin
    onSeek: (Long) -> Unit,
    onSeekAndPlay: (Long) -> Unit,
```

4c. In the `ShowNotesPage` call inside `PlayerPager` (the `1 -> ShowNotesPage(...)` branch, around line 270), add after `onSeek = onSeek,`:

```kotlin
                1 -> ShowNotesPage(
                    state = state,
                    onSeek = onSeek,
                    onSeekAndPlay = onSeekAndPlay,
                    onPlayPause = onPlayPause,
                    onSkipBack = onSkipBack,
                    onSkipForward = onSkipForward
                )
```

4d. In the `ShowNotesPage` composable signature (around line 805), add after `onSeek: (Long) -> Unit,`:

```kotlin
private fun ShowNotesPage(
    state: PlayerState,
    onSeek: (Long) -> Unit,
    onSeekAndPlay: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit
) {
```

- [ ] **Step 5: Build the annotated string and render it**

In `ShowNotesPage`, the non-empty branch currently reads (around lines 899-909):

```kotlin
            } else {
                SelectionContainer(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = showNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
```

Replace that whole `else` block with:

```kotlin
            } else {
                val linkColor = MaterialTheme.colorScheme.primary
                val annotated = remember(showNotes, linkColor) {
                    buildShowNotesAnnotatedString(showNotes, linkColor, onSeekAndPlay)
                }
                SelectionContainer(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
```

- [ ] **Step 6: Add the `buildShowNotesAnnotatedString` helper**

Add this private function in `PlayerScreen.kt` immediately after the `ShowNotesPage` composable (after its closing brace, around line 922):

```kotlin
private fun buildShowNotesAnnotatedString(
    text: String,
    linkColor: Color,
    onSeekAndPlay: (Long) -> Unit
) = buildAnnotatedString {
    val links = findTimestampLinks(text)
    var cursor = 0
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = linkColor,
            textDecoration = TextDecoration.Underline
        )
    )
    for (link in links) {
        if (link.start > cursor) {
            append(text.substring(cursor, link.start))
        }
        withLink(
            LinkAnnotation.Clickable(
                tag = "ts-${link.positionMs}",
                styles = linkStyles,
                linkInteractionListener = { onSeekAndPlay(link.positionMs) }
            )
        ) {
            append(text.substring(link.start, link.endExclusive))
        }
        cursor = link.endExclusive
    }
    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}
```

> `Color` is already imported in this file (used elsewhere). If the build reports it unresolved, add `import androidx.compose.ui.graphics.Color`.

- [ ] **Step 7: Run the source test + full module tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.ui.player.PlayerScreenSourceTest"`
Expected: PASS.

Then run the whole module to confirm nothing else broke and the file compiles:

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt \
        app/src/test/java/com/example/mypodcast/ui/player/PlayerScreenSourceTest.kt
git commit -m "feat: make show notes timestamps clickable to seek and play"
```

---

## Task 4: Manual verification (optional but recommended)

- [ ] **Step 1: Build and install on a device/emulator**

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Verify behavior**

Open an episode whose show notes contain timestamps (e.g. `(12:34)`, `1:02:03`),
swipe to the Show Notes page in the player, and tap a timestamp. Confirm:
- The timestamp is colored + underlined.
- Tapping seeks to that position and playback starts (even if it was paused).
- Plain text without timestamps renders unchanged; text can still be selected.

---

## Self-Review Notes

- **Spec coverage:** detector (Task 1), seek+play behavior (Task 2), render/wiring + link style (Task 3), formats `MM:SS`/`H:MM:SS` with paren tolerance and boundary guards (Task 1 tests), testing conventions (`*SourceTest` + JVM unit tests) all covered. `PodcastDetailScreen` intentionally untouched.
- **Type consistency:** `TimestampLink(start, endExclusive, positionMs)`, `findTimestampLinks(text)`, `seekToAndPlay(positionMs)`, and `onSeekAndPlay` callback name are used identically across all tasks.
- **No placeholders:** every code step shows complete code.
