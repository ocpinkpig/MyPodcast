# Translucent Mini Player Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the mini player's opaque background with a hand-rolled frosted-glass effect — a real backdrop blur on Android 12+ (API 31+) and a semi-transparent tint fallback on API 26-30.

**Architecture:** Move `MiniPlayerBar` out of the Scaffold's `bottomBar` so screen content sits behind it. Wrap the Scaffold body's content in a `BackdropRecorder` that records every frame into a shared `GraphicsLayer`. The mini player draws that layer into its own second `GraphicsLayer` (translated and clipped to its bounds) with a `RenderEffect` blur on API 31+, then overlays a translucent surface tint. A `LocalMiniPlayerInset` CompositionLocal propagates the mini player's height so scrollable screens can add it to their bottom `contentPadding`.

**Tech Stack:** Jetpack Compose (BOM 2026.03.01), Compose `GraphicsLayer` / `RenderEffect`, Material 3, Hilt, Kotlin.

**Testing note:** This is a purely visual change; no new unit tests are added. Verification is by manual run on two emulator API levels plus a Compose preview. The spec at [docs/superpowers/specs/2026-05-23-translucent-mini-player-design.md](../specs/2026-05-23-translucent-mini-player-design.md) documents this decision.

---

## File map

| File | Status | Responsibility |
|---|---|---|
| `app/src/main/java/com/example/mypodcast/ui/player/Backdrop.kt` | **New** | `LocalBackdropLayer`, `LocalMiniPlayerInset`, `BackdropRecorder` composable. |
| `app/src/main/java/com/example/mypodcast/ui/player/MiniPlayer.kt` | Modify | Translucent surface; reads `LocalBackdropLayer`; per-frame blur draw with API gating. |
| `app/src/main/java/com/example/mypodcast/ui/main/MainScreen.kt` | Modify | Move `MiniPlayerBar` out of `bottomBar`; wrap content in `BackdropRecorder`; provide `LocalMiniPlayerInset`. |
| `app/src/main/java/com/example/mypodcast/ui/home/HomeScreen.kt` | Modify | LazyColumn reads `LocalMiniPlayerInset` for bottom `contentPadding`. |
| `app/src/main/java/com/example/mypodcast/ui/search/SearchScreen.kt` | Modify | Same. |
| `app/src/main/java/com/example/mypodcast/ui/library/LibraryScreen.kt` | Modify | Apply to each LazyColumn / LazyVerticalGrid that fills the screen. |
| `app/src/main/java/com/example/mypodcast/ui/queue/QueueScreen.kt` | Modify | Apply to each LazyColumn. |
| `app/src/main/java/com/example/mypodcast/ui/detail/PodcastDetailScreen.kt` | Modify | Apply to the main LazyColumn (and any `verticalScroll` that fills the screen). |

---

## Task 1: Add Backdrop primitives

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/ui/player/Backdrop.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.example.mypodcast.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared GraphicsLayer that records the Scaffold body so the MiniPlayer
 * can sample it for a backdrop blur. `null` when no recorder is in scope.
 */
val LocalBackdropLayer = staticCompositionLocalOf<GraphicsLayer?> { null }

/**
 * Height the MiniPlayer occupies at the bottom of the content area.
 * Scrollable screens add this to their bottom contentPadding so the
 * last item is reachable when an episode is loaded.
 */
val LocalMiniPlayerInset = compositionLocalOf { 0.dp }

/**
 * Wraps content in a GraphicsLayer recorder. Each frame, the content
 * is recorded into [LocalBackdropLayer] and drawn crisply in place.
 * The MiniPlayer reads the layer and redraws (a clipped portion of) it
 * with a RenderEffect blur applied.
 */
@Composable
fun BackdropRecorder(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val layer = rememberGraphicsLayer()
    CompositionLocalProvider(LocalBackdropLayer provides layer) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
        ) {
            content()
        }
    }
}
```

- [ ] **Step 2: Build to confirm imports resolve**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/ui/player/Backdrop.kt
git commit -m "Add Backdrop primitives for mini player blur"
```

---

## Task 2: Make the MiniPlayer translucent with backdrop blur

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/player/MiniPlayer.kt`

- [ ] **Step 1: Replace the file contents**

Replace the entire file with the version below. The structural changes from the current implementation:
- Surface color becomes `Color.Transparent`.
- A second `GraphicsLayer` ("blurLayer") is created, with `renderEffect` set on API 31+, null otherwise.
- `Modifier.onGloballyPositioned` tracks the mini player's position within its parent.
- `Modifier.drawBehind` records the backdrop layer into the blur layer (translated by `-position`), draws the blur layer, then overlays a translucent `surfaceVariant` tint.

```kotlin
package com.example.mypodcast.ui.player

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.mypodcast.domain.model.PlayerState

@Composable
fun MiniPlayer(
    state: PlayerState,
    onTap: () -> Unit,
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val episode = state.episode ?: return
    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f

    val backdrop = LocalBackdropLayer.current
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blurLayer = rememberGraphicsLayer().apply {
        renderEffect = if (supportsBlur) {
            RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        } else null
    }
    val tintColor = MaterialTheme.colorScheme.surfaceVariant.copy(
        alpha = if (supportsBlur) 0.55f else 0.85f
    )
    var position by remember { mutableStateOf(Offset.Zero) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { position = it.positionInParent() }
            .drawBehind {
                if (backdrop != null) {
                    blurLayer.record(
                        size = IntSize(size.width.toInt(), size.height.toInt())
                    ) {
                        translate(left = -position.x, top = -position.y) {
                            drawLayer(backdrop)
                        }
                    }
                    drawLayer(blurLayer)
                }
                drawRect(tintColor)
            },
        shadowElevation = 8.dp,
        color = Color.Transparent
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTap)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = episode.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPlayPauseClick) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play"
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build to confirm everything resolves**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If you see "unresolved reference: drawLayer" verify the import is `androidx.compose.ui.graphics.layer.drawLayer`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/ui/player/MiniPlayer.kt
git commit -m "Render mini player with backdrop blur + translucent tint"
```

---

## Task 3: Restructure MainScreen to overlay the mini player

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/main/MainScreen.kt`

The new layout: `Scaffold`'s `bottomBar` holds only `CompactBottomNavigationBar`. Inside the body lambda, a `Box` contains the `BackdropRecorder` (with screen content) and `MiniPlayerBar` as a sibling aligned to the bottom. The `LocalMiniPlayerInset` is provided based on whether an episode is loaded.

- [ ] **Step 1: Replace MainScreen function body**

Replace `MainScreen` (lines 40-101 in the current file) and add the new imports. Final state:

```kotlin
package com.example.mypodcast.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.example.mypodcast.HomeNavKey
import com.example.mypodcast.LibraryNavKey
import com.example.mypodcast.PlayerNavKey
import com.example.mypodcast.QueueNavKey
import com.example.mypodcast.SearchNavKey
import com.example.mypodcast.ui.player.BackdropRecorder
import com.example.mypodcast.ui.player.LocalMiniPlayerInset
import com.example.mypodcast.ui.player.MiniPlayerBar

@Composable
fun MainScreen(
    backStack: MutableList<NavKey>,
    onNavigate: (NavKey) -> Unit,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable () -> Unit
) {
    val currentRoot = backStack.firstOrNull { it is HomeNavKey || it is SearchNavKey || it is LibraryNavKey || it is QueueNavKey }

    val playerViewModel: MainScreenViewModel = hiltViewModel()
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val miniPlayerInset = if (playerState.episode != null) 64.dp else 0.dp

    CompositionLocalProvider(LocalMiniPlayerInset provides miniPlayerInset) {
        Scaffold(
            contentWindowInsets = contentWindowInsets,
            bottomBar = {
                CompactBottomNavigationBar(
                    items = listOf(
                        CompactBottomNavItem(
                            icon = Icons.Default.Home,
                            contentDescription = "Home",
                            selected = currentRoot is HomeNavKey,
                            onClick = {
                                backStack.clear()
                                backStack.add(HomeNavKey)
                            }
                        ),
                        CompactBottomNavItem(
                            icon = Icons.Default.Search,
                            contentDescription = "Search",
                            selected = currentRoot is SearchNavKey,
                            onClick = {
                                backStack.clear()
                                backStack.add(SearchNavKey)
                            }
                        ),
                        CompactBottomNavItem(
                            icon = Icons.Default.LibraryMusic,
                            contentDescription = "Library",
                            selected = currentRoot is LibraryNavKey,
                            onClick = {
                                backStack.clear()
                                backStack.add(LibraryNavKey)
                            }
                        ),
                        CompactBottomNavItem(
                            icon = Icons.Default.QueueMusic,
                            contentDescription = "Queue",
                            selected = currentRoot is QueueNavKey,
                            onClick = {
                                backStack.clear()
                                backStack.add(QueueNavKey)
                            }
                        )
                    )
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                BackdropRecorder(modifier = Modifier.fillMaxSize()) {
                    content()
                }
                MiniPlayerBar(
                    onOpenPlayer = { guid -> onNavigate(PlayerNavKey(guid)) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
```

(`CompactBottomNavItem`, `CompactBottomNavigationBar`, and the rest of the file stay unchanged below.)

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/ui/main/MainScreen.kt
git commit -m "Overlay mini player as floating bar above content"
```

---

## Task 4: HomeScreen — consume mini player inset

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/home/HomeScreen.kt:47`

- [ ] **Step 1: Update the LazyColumn**

Locate the `LazyColumn(modifier = Modifier.fillMaxSize())` at line 47 and add `contentPadding`:

```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = LocalMiniPlayerInset.current)
) {
```

Add these imports near the top of the file:

```kotlin
import androidx.compose.foundation.layout.PaddingValues
import com.example.mypodcast.ui.player.LocalMiniPlayerInset
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/ui/home/HomeScreen.kt
git commit -m "Reserve bottom space for mini player in HomeScreen"
```

---

## Task 5: SearchScreen — consume mini player inset

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/search/SearchScreen.kt:64`

- [ ] **Step 1: Update the LazyColumn**

Locate `else -> LazyColumn(modifier = Modifier.fillMaxSize())` and update:

```kotlin
else -> LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = LocalMiniPlayerInset.current)
) {
```

Add imports:

```kotlin
import androidx.compose.foundation.layout.PaddingValues
import com.example.mypodcast.ui.player.LocalMiniPlayerInset
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/ui/search/SearchScreen.kt
git commit -m "Reserve bottom space for mini player in SearchScreen"
```

---

## Task 6: LibraryScreen — consume mini player inset

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/library/LibraryScreen.kt`

LibraryScreen has multiple full-screen scrollables (lines 266, 293, 359 per the survey). Each one that is the primary content scroller needs the inset added at the bottom of its existing `contentPadding`.

- [ ] **Step 1: Read the file to confirm the exact context**

Read `app/src/main/java/com/example/mypodcast/ui/library/LibraryScreen.kt`. For each of the three scrollables identified below, locate its `contentPadding` parameter (or lack thereof) before editing.

- [ ] **Step 2: Add the import once**

Near the existing layout imports add:

```kotlin
import androidx.compose.foundation.layout.PaddingValues
import com.example.mypodcast.ui.player.LocalMiniPlayerInset
```

- [ ] **Step 3: Update the LazyVerticalGrid (~line 266)**

Read the surrounding code (lines 260-280). The current `contentPadding = PaddingValues(PodcastCardGridDefaults.ContentPadding)` becomes:

```kotlin
contentPadding = PaddingValues(
    start = PodcastCardGridDefaults.ContentPadding,
    top = PodcastCardGridDefaults.ContentPadding,
    end = PodcastCardGridDefaults.ContentPadding,
    bottom = PodcastCardGridDefaults.ContentPadding + LocalMiniPlayerInset.current
),
```

- [ ] **Step 4: Update the LazyColumn at line 293**

Read lines 290-300. The current `LazyColumn(Modifier.fillMaxSize())` (no `contentPadding`) becomes:

```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = LocalMiniPlayerInset.current)
) {
```

- [ ] **Step 5: Update the LazyColumn at line 359**

Read lines 355-365. The current `contentPadding = PaddingValues(bottom = 20.dp)` becomes:

```kotlin
contentPadding = PaddingValues(bottom = 20.dp + LocalMiniPlayerInset.current)
```

- [ ] **Step 6: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/ui/library/LibraryScreen.kt
git commit -m "Reserve bottom space for mini player in LibraryScreen"
```

---

## Task 7: QueueScreen — consume mini player inset

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/queue/QueueScreen.kt`

QueueScreen has three LazyColumns (lines 193, 217, 289), each with `contentPadding = PaddingValues(bottom = 20.dp)`.

- [ ] **Step 1: Add imports**

```kotlin
import com.example.mypodcast.ui.player.LocalMiniPlayerInset
```

(`PaddingValues` is already imported.)

- [ ] **Step 2: Update all three contentPadding values**

For each of the three `LazyColumn` blocks, change:

```kotlin
contentPadding = PaddingValues(bottom = 20.dp)
```

to:

```kotlin
contentPadding = PaddingValues(bottom = 20.dp + LocalMiniPlayerInset.current)
```

Use Edit's `replace_all` since the three occurrences are identical.

- [ ] **Step 3: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/ui/queue/QueueScreen.kt
git commit -m "Reserve bottom space for mini player in QueueScreen"
```

---

## Task 8: PodcastDetailScreen — consume mini player inset

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/detail/PodcastDetailScreen.kt`

The screen has a primary `LazyColumn` at line 120, plus two other LazyRows / inner scrollables at 266 and 359 (those are horizontal carousels — leave them alone), and a `verticalScroll` at line 400.

- [ ] **Step 1: Read the file to confirm which scrollables are full-screen**

Read `app/src/main/java/com/example/mypodcast/ui/detail/PodcastDetailScreen.kt`. Identify:
- Line 120 LazyColumn — main content list, NEEDS inset.
- Line 266 LazyRow / horizontal `contentPadding = PaddingValues(horizontal = 48.dp)` — horizontal carousel, SKIP.
- Line 359 horizontal `contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)` — horizontal, SKIP.
- Line 400 `verticalScroll` — check whether it's the screen's primary scroller; if it sits inside a Box that fills the screen and is the only thing scrolling vertically, NEEDS inset (add bottom padding via `Modifier.padding(bottom = LocalMiniPlayerInset.current)` on the scrolling container).

- [ ] **Step 2: Add imports**

```kotlin
import androidx.compose.foundation.layout.PaddingValues
import com.example.mypodcast.ui.player.LocalMiniPlayerInset
```

- [ ] **Step 3: Update the LazyColumn at line 120**

Read lines 118-125. Convert `LazyColumn(modifier = Modifier.fillMaxSize())` to:

```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = LocalMiniPlayerInset.current)
) {
```

- [ ] **Step 4: Update the verticalScroll container (if applicable)**

Read lines 395-410. If the container with `.verticalScroll(rememberScrollState())` is a Column or Box that fills the available space, append `.padding(bottom = LocalMiniPlayerInset.current)` AFTER the verticalScroll modifier so the padding lives inside the scrolling viewport (so the last child can be scrolled into view):

Example before:
```kotlin
Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
```

After:
```kotlin
Column(
    modifier = Modifier
        .verticalScroll(rememberScrollState())
        .padding(bottom = LocalMiniPlayerInset.current)
) {
```

If you determine the verticalScroll isn't a primary scroller (e.g., it's a bottom sheet or dialog content), skip it and note the decision in the commit message.

- [ ] **Step 5: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/ui/detail/PodcastDetailScreen.kt
git commit -m "Reserve bottom space for mini player in PodcastDetailScreen"
```

---

## Task 9: Add a Compose preview for the translucent MiniPlayer

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/player/MiniPlayer.kt` (append preview at end)

- [ ] **Step 1: Append preview composable**

Add to the bottom of the file:

```kotlin
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun MiniPlayerPreview() {
    val sampleEpisode = com.example.mypodcast.domain.model.Episode(
        guid = "preview",
        feedId = 0L,
        title = "Sample episode title that overflows the available width",
        description = null,
        audioUrl = "",
        artworkUrl = null,
        publishedAtMs = 0L,
        durationMs = 1_800_000L
    )
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .androidx.compose.foundation.background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color.Red, Color.Blue)
                )
            )
    ) {
        MiniPlayer(
            state = PlayerState(
                episode = sampleEpisode,
                isPlaying = true,
                positionMs = 600_000L,
                durationMs = 1_800_000L
            ),
            onTap = {},
            onPlayPauseClick = {}
        )
    }
}
```

If the `Episode` constructor in this project has a different signature (check `app/src/main/java/com/example/mypodcast/domain/model/Episode.kt` first), adjust the named arguments to match — names and types must be exact. Same for `PlayerState`: check `app/src/main/java/com/example/mypodcast/domain/model/PlayerState.kt`.

Also fix the fully-qualified `androidx.compose.foundation.background` reference — that's not a valid expression. Add this import instead:

```kotlin
import androidx.compose.foundation.background
```

and use:

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .background(
        androidx.compose.ui.graphics.Brush.horizontalGradient(
            listOf(Color.Red, Color.Blue)
        )
    )
```

(Note: the preview won't exercise the real backdrop layer — `LocalBackdropLayer.current` is `null`, so the blur draw path is skipped and only the tint shows. That still verifies the tint, layout, and that the code compiles without runtime errors when no backdrop is provided.)

- [ ] **Step 2: Build (and render the preview in Android Studio if available)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

If Android Studio is open, view the preview panel for `MiniPlayer.kt` and confirm the tinted bar renders over the gradient.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/mypodcast/ui/player/MiniPlayer.kt
git commit -m "Add MiniPlayer preview with translucent tint over gradient"
```

---

## Task 10: Manual verification on emulator

This is not an automated step. Treat each verification as a discrete sub-task with a clear expected outcome.

- [ ] **Step 1: Install on an API 33 emulator**

Run: `./gradlew :app:installDebug` (with an API 33+ AVD running).
Launch the app, subscribe to a podcast if none is loaded, start an episode.

Verify:
- The mini player is visible above the bottom nav.
- When scrolling Home / Library / Queue, content visibly blurs as it passes behind the mini player.
- The bar shows a soft tint over the blur (not opaque, not transparent).
- The last list item in each scrollable can be fully scrolled into view above the mini player (no permanent clipping).
- The mini player disappears entirely when no episode is loaded, and scrollable content has no extra bottom space in that state.

- [ ] **Step 2: Install on an API 30 emulator (or set up one)**

If an API 30 AVD isn't already present, create one via `sdkmanager`/AVD Manager, then `./gradlew :app:installDebug`.

Verify:
- No crash on launch or on mini player appearance.
- The mini player renders with a visible translucent tint (no blur) — content underneath is still visible through it but not blurred.
- Bottom inset behavior matches the API 33 case.

- [ ] **Step 3: Investigate any positional drift**

While scrolling, watch the blurred region under the mini player. The blurred area should track the content immediately under the bar (no horizontal or vertical offset). If you see drift:
- Confirm the `BackdropRecorder` is the immediate parent of `MiniPlayerBar` in the layout tree (sibling inside the same `Box`).
- Check that `positionInParent()` (not `positionInWindow()`) is used in `MiniPlayer.kt`.
- Confirm both siblings start at the same origin in the `Box` (neither has an offset applied).

- [ ] **Step 4: Final commit (if any tweaks were needed)**

Only commit if verification required fixes. Otherwise this task ends without a commit.

```bash
git add -A
git commit -m "Adjust translucent mini player after manual verification"
```

---

## Self-review checklist (completed by plan author)

- [x] Every spec section (architecture move, backdrop capture, mini player draw, inset propagation, file touchpoints, testing, risks) maps to at least one task above.
- [x] No "TBD" / "TODO" / "implement later" / "add error handling" placeholders.
- [x] Type and symbol names are consistent across tasks: `LocalBackdropLayer`, `LocalMiniPlayerInset`, `BackdropRecorder`, `MiniPlayer`, `MiniPlayerBar`.
- [x] Tasks 4-8 each reference the exact existing line numbers from the codebase survey.
- [x] Task 9 anticipates the realistic risk that the preview's `Episode`/`PlayerState` constructor doesn't match what's written, and instructs the implementer to verify before editing.
- [x] Task 10 (manual verification) is broken into discrete sub-steps with clear pass/fail criteria, since this feature has no unit tests by design.
