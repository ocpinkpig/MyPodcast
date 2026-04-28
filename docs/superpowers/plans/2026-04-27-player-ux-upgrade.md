# Player UX Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a two-page full-screen podcast player with a focused playback page, a swipeable show-notes page, and bottom-sheet controls for speed and sleep timer.

**Architecture:** Keep the work inside the existing `ui/player` boundary. Extract pure formatting helpers into a small testable file, then decompose `PlayerScreen.kt` into focused private composables without changing repositories, Room entities, navigation keys, or media playback ownership.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose Foundation Pager, Hilt ViewModel, AndroidX Media3 state exposed through the existing `PlayerViewModel`.

---

## File Structure

- Create `app/src/main/java/com/example/mypodcast/ui/player/PlayerUiFormatters.kt`
  - Holds pure UI formatting helpers for playback time, speed labels, sleep timer labels, and progress fraction.
- Create `app/src/test/java/com/example/mypodcast/ui/player/PlayerUiFormattersTest.kt`
  - Unit tests for the pure formatting helpers.
- Modify `app/src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt`
  - Replace the current single-column player with a two-page pager.
  - Add private composables: `PlayerPager`, `PlaybackPage`, `ShowNotesPage`, `PlayerProgress`, `PrimaryPlaybackControls`, `CompactPlaybackControls`, `PlayerStatusChips`, `SpeedSheet`, `SleepTimerSheet`, and `PageIndicator`.
  - Keep the existing `HtmlCompat.fromHtml(...).toString().trim()` show-notes cleanup because the spec intentionally avoids rich formatting for this pass.
- Leave `app/src/main/java/com/example/mypodcast/ui/player/PlayerViewModel.kt` unchanged. It already exposes `playPause`, `seekTo`, `skipForward`, `skipBack`, `setSpeed`, `setSleepTimer`, and `cancelSleepTimer`.

## Task 1: Add Pure Player UI Formatters

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/ui/player/PlayerUiFormatters.kt`
- Create: `app/src/test/java/com/example/mypodcast/ui/player/PlayerUiFormattersTest.kt`

- [ ] **Step 1: Write the failing formatter tests**

Create `app/src/test/java/com/example/mypodcast/ui/player/PlayerUiFormattersTest.kt`:

```kotlin
package com.example.mypodcast.ui.player

import junit.framework.TestCase.assertEquals
import org.junit.Test

class PlayerUiFormattersTest {
    @Test
    fun formatPlaybackTime_formatsMinutesAndSeconds() {
        assertEquals("0:00", formatPlaybackTime(0L))
        assertEquals("1:05", formatPlaybackTime(65_000L))
        assertEquals("59:59", formatPlaybackTime(3_599_000L))
    }

    @Test
    fun formatPlaybackTime_formatsHours() {
        assertEquals("1:00:00", formatPlaybackTime(3_600_000L))
        assertEquals("2:03:04", formatPlaybackTime(7_384_000L))
    }

    @Test
    fun formatProgressFraction_handlesUnknownDuration() {
        assertEquals(0f, formatProgressFraction(positionMs = 5_000L, durationMs = 0L))
        assertEquals(0.5f, formatProgressFraction(positionMs = 5_000L, durationMs = 10_000L))
        assertEquals(1f, formatProgressFraction(positionMs = 12_000L, durationMs = 10_000L))
        assertEquals(0f, formatProgressFraction(positionMs = -1_000L, durationMs = 10_000L))
    }

    @Test
    fun formatSpeedLabel_usesSingleDecimal() {
        assertEquals("1.0x", formatSpeedLabel(1.0f))
        assertEquals("1.5x", formatSpeedLabel(1.5f))
        assertEquals("3.0x", formatSpeedLabel(3.0f))
    }

    @Test
    fun formatSleepTimerLabel_handlesInactiveAndActiveTimer() {
        assertEquals("Timer off", formatSleepTimerLabel(0L))
        assertEquals("14:59 left", formatSleepTimerLabel(899_000L))
        assertEquals("1:00:00 left", formatSleepTimerLabel(3_600_000L))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.ui.player.PlayerUiFormattersTest"
```

Expected: FAIL because `formatPlaybackTime`, `formatProgressFraction`, `formatSpeedLabel`, and `formatSleepTimerLabel` do not exist.

- [ ] **Step 3: Implement the formatter helpers**

Create `app/src/main/java/com/example/mypodcast/ui/player/PlayerUiFormatters.kt`:

```kotlin
package com.example.mypodcast.ui.player

import kotlin.math.roundToInt

internal fun formatPlaybackTime(ms: Long): String {
    val totalSecs = (ms.coerceAtLeast(0L) / 1000).toInt()
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

internal fun formatProgressFraction(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

internal fun formatSpeedLabel(speed: Float): String {
    val tenths = (speed * 10f).roundToInt() / 10f
    return "%.1fx".format(tenths)
}

internal fun formatSleepTimerLabel(remainingMs: Long): String {
    if (remainingMs <= 0L) return "Timer off"
    return "${formatPlaybackTime(remainingMs)} left"
}
```

- [ ] **Step 4: Run the formatter tests again**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.ui.player.PlayerUiFormattersTest"
```

Expected: PASS.

- [ ] **Step 5: Commit the formatter task**

Run:

```bash
git add app/src/main/java/com/example/mypodcast/ui/player/PlayerUiFormatters.kt app/src/test/java/com/example/mypodcast/ui/player/PlayerUiFormattersTest.kt
git commit -m "Add player UI formatters"
```

## Task 2: Build The Two-Page Player Shell

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt`

- [ ] **Step 1: Replace the top-level screen structure**

In `PlayerScreen.kt`, update imports to include these APIs and remove imports no longer used by the old single-column speed/timer chip rows:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.mypodcast.domain.model.PlayerState
```

Replace the body of `PlayerScreen` with:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    episodeGuid: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSleepTimerSheet = true }) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = "More player options")
                    }
                }
            )
        }
    ) { padding ->
        PlayerPager(
            state = state,
            contentPadding = padding,
            onSeek = viewModel::seekTo,
            onPlayPause = viewModel::playPause,
            onSkipBack = viewModel::skipBack,
            onSkipForward = viewModel::skipForward,
            onSpeedClick = { showSpeedSheet = true },
            onSleepTimerClick = { showSleepTimerSheet = true }
        )
    }

    if (showSpeedSheet) {
        SpeedSheet(
            currentSpeed = state.speed,
            onSpeedSelected = { speed ->
                viewModel.setSpeed(speed)
                showSpeedSheet = false
            },
            onDismiss = { showSpeedSheet = false }
        )
    }

    if (showSleepTimerSheet) {
        SleepTimerSheet(
            remainingMs = state.sleepTimerRemainingMs,
            onTimerSelected = { minutes ->
                viewModel.setSleepTimer(minutes)
                showSleepTimerSheet = false
            },
            onCancelTimer = {
                viewModel.cancelSleepTimer()
                showSleepTimerSheet = false
            },
            onDismiss = { showSleepTimerSheet = false }
        )
    }
}
```

- [ ] **Step 2: Add the pager composable**

Add this private composable below `PlayerScreen`:

```kotlin
@Composable
private fun PlayerPager(
    state: PlayerState,
    contentPadding: PaddingValues,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onSpeedClick: () -> Unit,
    onSleepTimerClick: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> PlaybackPage(
                    state = state,
                    onSeek = onSeek,
                    onPlayPause = onPlayPause,
                    onSkipBack = onSkipBack,
                    onSkipForward = onSkipForward,
                    onSpeedClick = onSpeedClick,
                    onSleepTimerClick = onSleepTimerClick
                )
                1 -> ShowNotesPage(
                    state = state,
                    onSeek = onSeek,
                    onPlayPause = onPlayPause,
                    onSkipBack = onSkipBack,
                    onSkipForward = onSkipForward
                )
            }
        }

        PageIndicator(
            selectedPage = pagerState.currentPage,
            pageCount = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )
    }
}
```

- [ ] **Step 3: Add minimal page and sheet bodies for this task**

Add these composables at the bottom of `PlayerScreen.kt` so Task 2 compiles before the detailed layouts are added:

```kotlin
@Composable
private fun PlaybackPage(
    state: PlayerState,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onSpeedClick: () -> Unit,
    onSleepTimerClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(state.episode?.title ?: "Loading...", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ShowNotesPage(
    state: PlayerState,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Show notes", style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSheet(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Playback speed", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { onSpeedSelected(currentSpeed) }) {
                Text(formatSpeedLabel(currentSpeed))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    remainingMs: Long,
    onTimerSelected: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge)
            Text(formatSleepTimerLabel(remainingMs))
            TextButton(onClick = onCancelTimer) { Text("Off") }
            TextButton(onClick = { onTimerSelected(30) }) { Text("30 minutes") }
        }
    }
}

@Composable
private fun PageIndicator(
    selectedPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == selectedPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(6.dp)
                    .width(if (selected) 18.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}
```

- [ ] **Step 4: Run a compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: PASS. If it fails because `HorizontalPager` API signatures differ in the installed Compose BOM, adjust only the pager state creation and rerun the same command.

- [ ] **Step 5: Commit the pager shell**

Run:

```bash
git add app/src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt
git commit -m "Add two-page player shell"
```

## Task 3: Implement Playback Page Components

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt`

- [ ] **Step 1: Replace `PlaybackPage` with the real playback layout**

Replace the Task 2 `PlaybackPage` composable with:

```kotlin
@Composable
private fun PlaybackPage(
    state: PlayerState,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onSpeedClick: () -> Unit,
    onSleepTimerClick: () -> Unit
) {
    val episode = state.episode

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        val artworkSize = if (maxHeight < 620.dp) 132.dp else 176.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            AsyncImage(
                model = episode?.artworkUrl,
                contentDescription = episode?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(artworkSize)
                    .clip(RoundedCornerShape(18.dp))
            )

            Text(
                text = episode?.title ?: "Loading...",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )

            PlayerStatusChips(
                speed = state.speed,
                sleepTimerRemainingMs = state.sleepTimerRemainingMs,
                isDownloaded = episode?.audioUrl?.startsWith("/") == true
            )

            if (state.error != null) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.weight(1f))

            PlayerProgress(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = onSeek
            )

            PrimaryPlaybackControls(
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
                onPlayPause = onPlayPause,
                onSkipBack = onSkipBack,
                onSkipForward = onSkipForward
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ElevatedButton(
                    onClick = onSpeedClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(formatSpeedLabel(state.speed))
                }
                ElevatedButton(
                    onClick = onSleepTimerClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.sleepTimerRemainingMs > 0L) "Timer" else "Sleep")
                }
            }

            Text(
                text = "Swipe for show notes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 2: Add the shared playback components**

Add these composables below `PlaybackPage`:

```kotlin
@Composable
private fun PlayerStatusChips(
    speed: Float,
    sleepTimerRemainingMs: Long,
    isDownloaded: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isDownloaded) {
            AssistChip(
                onClick = {},
                label = { Text("Downloaded") },
                leadingIcon = { Icon(Icons.Default.DownloadDone, contentDescription = null) }
            )
        }
        AssistChip(
            onClick = {},
            label = { Text(formatSpeedLabel(speed)) },
            leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) }
        )
        AssistChip(
            onClick = {},
            label = { Text(formatSleepTimerLabel(sleepTimerRemainingMs)) },
            leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) }
        )
    }
}

@Composable
private fun PlayerProgress(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = formatProgressFraction(positionMs, durationMs),
            onValueChange = { fraction -> onSeek((fraction * durationMs).toLong()) },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatPlaybackTime(positionMs), style = MaterialTheme.typography.labelSmall)
            Text(formatPlaybackTime(durationMs), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PrimaryPlaybackControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onSkipBack, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Default.Replay30,
                contentDescription = "Skip back 30 seconds",
                modifier = Modifier.size(34.dp)
            )
        }
        Surface(
            onClick = onPlayPause,
            modifier = Modifier.size(76.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(34.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
        }
        IconButton(onClick = onSkipForward, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Default.Forward30,
                contentDescription = "Skip forward 30 seconds",
                modifier = Modifier.size(34.dp)
            )
        }
    }
}
```

- [ ] **Step 3: Run tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.ui.player.PlayerUiFormattersTest"
./gradlew :app:compileDebugKotlin
```

Expected: both commands PASS.

- [ ] **Step 4: Commit the playback page**

Run:

```bash
git add app/src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt
git commit -m "Build redesigned playback page"
```

## Task 4: Implement Show-Notes Page

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt`

- [ ] **Step 1: Replace `ShowNotesPage` with the real show-notes layout**

Replace the Task 2 `ShowNotesPage` with:

```kotlin
@Composable
private fun ShowNotesPage(
    state: PlayerState,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit
) {
    val episode = state.episode
    val showNotes = episode?.description
        ?.takeIf { it.isNotBlank() }
        ?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim() }
        .orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = episode?.artworkUrl,
                contentDescription = episode?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode?.title ?: "Loading...",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${formatPlaybackTime(state.positionMs)} of ${formatPlaybackTime(state.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        PlayerProgress(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            onSeek = onSeek,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (showNotes.isBlank()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No show notes available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Text(
                    text = showNotes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }

        CompactPlaybackControls(
            isPlaying = state.isPlaying,
            isBuffering = state.isBuffering,
            onPlayPause = onPlayPause,
            onSkipBack = onSkipBack,
            onSkipForward = onSkipForward,
            modifier = Modifier.padding(vertical = 10.dp)
        )
    }
}
```

- [ ] **Step 2: Add compact playback controls**

Add this composable below `PrimaryPlaybackControls`:

```kotlin
@Composable
private fun CompactPlaybackControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSkipBack) {
                Icon(Icons.Default.Replay30, contentDescription = "Skip back 30 seconds")
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(52.dp)) {
                if (isBuffering) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            IconButton(onClick = onSkipForward) {
                Icon(Icons.Default.Forward30, contentDescription = "Skip forward 30 seconds")
            }
        }
    }
}
```

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 4: Commit the show-notes page**

Run:

```bash
git add app/src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt
git commit -m "Add swipeable player show notes"
```

## Task 5: Add Speed And Sleep Timer Bottom Sheets

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt`

- [ ] **Step 1: Replace `SpeedSheet` with the full selector**

Replace the Task 2 `SpeedSheet` composable with:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSheet(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val speedOptions = listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Playback speed", style = MaterialTheme.typography.titleLarge)
            speedOptions.forEach { speed ->
                TextButton(
                    onClick = { onSpeedSelected(speed) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatSpeedLabel(speed))
                        if (speed == currentSpeed) {
                            Text("Selected", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
```

- [ ] **Step 2: Replace `SleepTimerSheet` with the full selector**

Replace the Task 2 `SleepTimerSheet` composable with:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    remainingMs: Long,
    onTimerSelected: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timerOptions = listOf(15, 30, 60)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge)
            Text(
                text = formatSleepTimerLabel(remainingMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onCancelTimer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Off")
                    if (remainingMs <= 0L) Text("Selected", color = MaterialTheme.colorScheme.primary)
                }
            }
            timerOptions.forEach { minutes ->
                TextButton(
                    onClick = { onTimerSelected(minutes) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "$minutes minutes",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
```

- [ ] **Step 3: Remove the old private `formatMs` function**

Delete this old helper from `PlayerScreen.kt` because `PlayerUiFormatters.kt` replaces it:

```kotlin
private fun formatMs(ms: Long): String {
    val totalSecs = ms / 1000
    val h = totalSecs / 3600
    val m = (totalSecs % 3600) / 60
    val s = totalSecs % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
```

- [ ] **Step 4: Run tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.ui.player.PlayerUiFormattersTest"
./gradlew :app:compileDebugKotlin
```

Expected: both commands PASS.

- [ ] **Step 5: Commit the bottom sheets**

Run:

```bash
git add app/src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt app/src/main/java/com/example/mypodcast/ui/player/PlayerUiFormatters.kt app/src/test/java/com/example/mypodcast/ui/player/PlayerUiFormattersTest.kt
git commit -m "Add player speed and sleep timer sheets"
```

## Task 6: Final Polish And Verification

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt`
- Optional modify: `.gitignore`

- [ ] **Step 1: Review layout imports and remove unused imports**

Open `PlayerScreen.kt` and remove imports that the compiler flags as unused. Keep the imports needed by these final responsibilities:

```kotlin
// Required categories:
// - foundation layout, pager, scrolling, background, shapes
// - material icons: ArrowBack, DownloadDone, Forward30, GraphicEq, MoreHoriz, Pause, PlayArrow, Replay30, Timer
// - material3: AssistChip, buttons, sheets, progress indicators, Scaffold, Slider, Surface, Text, TopAppBar
// - runtime state: getValue, mutableStateOf, remember, setValue
// - HtmlCompat, hiltViewModel, collectAsStateWithLifecycle, AsyncImage
```

- [ ] **Step 2: Add `.superpowers/` to `.gitignore` if the visual companion directory is still untracked**

If `git status --short` shows `?? .superpowers/`, append this line to `.gitignore`:

```gitignore
.superpowers/
```

Do not stage or delete `.superpowers/` content.

- [ ] **Step 3: Run full verification**

Run:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugKotlin
```

Expected: both commands PASS.

- [ ] **Step 4: Inspect changed files**

Run:

```bash
git status --short
git diff -- app/src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt app/src/main/java/com/example/mypodcast/ui/player/PlayerUiFormatters.kt app/src/test/java/com/example/mypodcast/ui/player/PlayerUiFormattersTest.kt .gitignore
```

Expected: only the player UI, formatter/test files, and optional `.gitignore` changes are part of this implementation. Existing unrelated modifications such as `.claude/settings.local.json`, `RssParser.kt`, and pre-existing `PlayerScreen.kt` baseline changes must not be reverted.

- [ ] **Step 5: Commit final polish**

If Step 2 changed `.gitignore`, include it. Run:

```bash
git add app/src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt app/src/main/java/com/example/mypodcast/ui/player/PlayerUiFormatters.kt app/src/test/java/com/example/mypodcast/ui/player/PlayerUiFormattersTest.kt .gitignore
git commit -m "Polish player UX upgrade"
```

If `.gitignore` did not change, run:

```bash
git add app/src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt app/src/main/java/com/example/mypodcast/ui/player/PlayerUiFormatters.kt app/src/test/java/com/example/mypodcast/ui/player/PlayerUiFormattersTest.kt
git commit -m "Polish player UX upgrade"
```

## Self-Review Notes

- Spec coverage: The plan implements the two-page pager, default playback page, show-notes page, page indicators, status chips, speed sheet, sleep timer sheet, loading/error handling, compact notes-page controls, and no database/repository changes.
- Scope guard: Queue, saved episodes, transcripts, rich links, source/podcast name display, and app-wide redesign remain out of scope.
- Type consistency: The plan uses existing `PlayerState`, `Episode`, and `PlayerViewModel` members: `state.episode`, `state.isPlaying`, `state.positionMs`, `state.durationMs`, `state.speed`, `state.sleepTimerRemainingMs`, `state.isBuffering`, `state.error`, `viewModel.playPause`, `viewModel.seekTo`, `viewModel.skipBack`, `viewModel.skipForward`, `viewModel.setSpeed`, `viewModel.setSleepTimer`, and `viewModel.cancelSleepTimer`.
