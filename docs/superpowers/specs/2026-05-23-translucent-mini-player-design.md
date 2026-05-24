# Translucent Mini Player Background

**Date:** 2026-05-23
**Status:** Design — pending implementation

## Goal

Give the mini player a frosted-glass background so that scrolling screen content shows through it, blurred. Visible on Android 12+ (API 31+) as a true backdrop blur; gracefully degraded to a semi-transparent tint on API 26–30.

## Non-goals

- No new third-party dependencies (no Haze, no AndroidX Blur).
- No artwork-derived dynamic tint.
- No changes to the full-screen `PlayerScreen` — only the persistent `MiniPlayerBar`.

## Architecture changes

### Current

In `MainScreen.kt`, the mini player sits inside the Scaffold's `bottomBar`:

```kotlin
Scaffold(
    bottomBar = {
        Column {
            MiniPlayerBar(...)
            CompactBottomNavigationBar(...)
        }
    }
) { padding -> Box(Modifier.padding(padding)) { content() } }
```

Scaffold reserves vertical space for the whole `bottomBar` Column, so no content actually sits behind the mini player. That makes a real backdrop blur impossible.

### Proposed

Move `MiniPlayerBar` out of `bottomBar`. The Scaffold's `bottomBar` holds only the bottom navigation bar. The whole Scaffold is wrapped in a `Box`; the mini player is rendered as a sibling overlay, aligned to the bottom of the content area, positioned just above the nav bar.

```kotlin
Box(Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = { CompactBottomNavigationBar(...) }
    ) { padding ->
        BackdropRecorder {
            Box(Modifier.fillMaxSize().padding(padding)) { content() }
        }
    }

    MiniPlayerBar(
        onOpenPlayer = ...,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = bottomNavHeight)
    )
}
```

`bottomNavHeight` is computed as `60.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` to match the existing `CompactBottomNavigationBar` layout.

## Backdrop capture

A new file `ui/player/Backdrop.kt` introduces:

```kotlin
val LocalBackdropLayer = compositionLocalOf<GraphicsLayer?> { null }
val LocalMiniPlayerInset = compositionLocalOf { 0.dp }

@Composable
fun BackdropRecorder(content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    CompositionLocalProvider(LocalBackdropLayer provides layer) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
        ) { content() }
    }
}
```

This records every frame of the wrapped content into a `GraphicsLayer` and also draws it crisply in its normal position. The layer itself has no `renderEffect` set — applying blur there would also blur the original crisp draw.

## Mini player draw logic

`MiniPlayer.kt` changes:

1. Read `LocalBackdropLayer` and the current `Density`.
2. Track its own position via `Modifier.onGloballyPositioned { coords -> position = coords.positionInParent() }`. We use `positionInParent` (not `positionInWindow`) because the recording `Box` is the immediate ancestor, so parent-local coords match the layer's coord space.
3. Own a second `rememberGraphicsLayer()` ("blur layer") whose `renderEffect` is:
   - `RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP).asComposeRenderEffect()` when `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`
   - `null` otherwise.
4. Replace `Surface(color = surfaceVariant)` with `Surface(color = Color.Transparent)` plus a `Modifier.drawBehind` step that:
   - Records the backdrop layer into the blur layer at the mini player's size, translated by `-position`, so the sampled region is exactly what sits under the mini player.
   - Calls `drawLayer(blurLayer)`.
   - Overlays a `surfaceVariant.copy(alpha = 0.55f)` rectangle for readability on API 31+, or `alpha = 0.85f` on older APIs.

Sketch:

```kotlin
val backdrop = LocalBackdropLayer.current
val blurLayer = rememberGraphicsLayer().also {
    it.renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    } else null
}
val tintAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.55f else 0.85f
var position by remember { mutableStateOf(Offset.Zero) }

Surface(
    color = Color.Transparent,
    shadowElevation = 8.dp,
    modifier = modifier
        .fillMaxWidth()
        .onGloballyPositioned { position = it.positionInParent() }
        .drawBehind {
            if (backdrop != null) {
                blurLayer.record(size = size.toIntSize()) {
                    translate(-position.x, -position.y) { drawLayer(backdrop) }
                }
                drawLayer(blurLayer)
            }
            drawRect(surfaceVariant.copy(alpha = tintAlpha))
        }
) { /* existing column with progress + row */ }
```

(`size.toIntSize()` is shorthand — the actual call is `IntSize(size.width.toInt(), size.height.toInt())`.)

## Bottom-inset propagation

Because the mini player no longer lives inside `bottomBar`, the Scaffold's `paddingValues` only reserves space for the nav bar. The mini player overlays the bottom of the content area, which can clip the last item of any scrollable.

Solution: `MainScreen` provides `LocalMiniPlayerInset` based on whether an episode is loaded:

```kotlin
val miniPlayerState by miniPlayerViewModel.playerState.collectAsStateWithLifecycle()
val miniPlayerInset = if (miniPlayerState.episode != null) 64.dp else 0.dp

CompositionLocalProvider(LocalMiniPlayerInset provides miniPlayerInset) {
    Scaffold(...) { ... }
}
```

Scrollable screens add this to their bottom `contentPadding`. Files to update:

- `ui/home/HomeScreen.kt`
- `ui/library/LibraryScreen.kt`
- `ui/queue/QueueScreen.kt`
- `ui/search/SearchScreen.kt`
- `ui/detail/PodcastDetailScreen.kt`

In each, change the relevant `LazyColumn` / `LazyVerticalGrid` `contentPadding` to include `+ LocalMiniPlayerInset.current` at the bottom.

## File touchpoints summary

| File | Change |
|---|---|
| `ui/player/Backdrop.kt` | **New.** `LocalBackdropLayer`, `LocalMiniPlayerInset`, `BackdropRecorder`. |
| `ui/player/MiniPlayer.kt` | Translucent Surface, blur draw, position tracking, API gating. |
| `ui/main/MainScreen.kt` | Move MiniPlayer out of `bottomBar`; wrap body in `BackdropRecorder`; overlay via outer `Box`; provide `LocalMiniPlayerInset`. |
| `ui/home/HomeScreen.kt` | Add `LocalMiniPlayerInset` to bottom `contentPadding`. |
| `ui/library/LibraryScreen.kt` | Same. |
| `ui/queue/QueueScreen.kt` | Same. |
| `ui/search/SearchScreen.kt` | Same. |
| `ui/detail/PodcastDetailScreen.kt` | Same. |

## Testing

- **Manual** on API 33 emulator: confirm blur is visible while scrolling Home/Library/Queue; tap the mini player; verify the bar updates correctly when an episode starts and stops.
- **Manual** on API 30 emulator: confirm fallback tint renders without crashing; mini player remains usable.
- **Compose preview** in `MiniPlayer.kt`: render `MiniPlayer` over a colored gradient backdrop so the translucent appearance is visible in Studio without running the app. (Preview won't exercise the real backdrop layer, but verifies the tint/Surface rendering.)
- **No new unit tests** — this is purely visual; logic is contained in draw-phase modifiers.

## Risks and open questions

- **Performance:** Recording the whole Scaffold body into a layer every frame has overhead. On modest devices this should still be fine — the recorded area is bounded by the screen size — but worth verifying on a low-end device. If it shows up in tracing, we can gate recording to only happen when the mini player is visible.
- **Coordinate alignment:** `positionInParent()` assumes the immediate parent is the recording `Box`. If a future refactor inserts another layout between them, the blur will be offset. A unit-style sanity guard: log/assert position once in debug builds.
- **Status bar overlap:** Not in scope — the mini player only ever sits at the bottom. The recorder Box is inside the Scaffold body, so top bars (if any) aren't recorded, which is fine.

## Out of scope (possible follow-ups)

- Animated entry/exit when the mini player appears/disappears.
- Tinting the blur with the dominant color from the current episode's artwork.
- Sharing the `BackdropRecorder` for other translucent surfaces (e.g., a future translucent top app bar).
