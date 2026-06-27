# Clickable Show Notes Timestamps — Design

**Date:** 2026-06-27
**Status:** Approved

## Goal

In an episode's show notes, timestamps (e.g. `12:34`, `1:02:03`) should be
tappable. Tapping a timestamp seeks the player to that position and starts
playback from there.

## Context

Episode show notes are rendered in exactly one place: `ShowNotesPage` in
`app/src/main/java/com/example/mypodcast/ui/player/PlayerScreen.kt`. Today it
flattens the episode's HTML `description` to a plain `String` via
`HtmlCompat.fromHtml(...).toString()` and displays it inside a
`SelectionContainer` + single `Text`.

The seek path is already plumbed: `ShowNotesPage` receives `onSeek: (Long) -> Unit`,
wired from `viewModel::seekTo`. The player exposes `seekTo`, `resume`, and `play`
via `PlayerRepository`; `PlayerViewModel.playPause` already distinguishes the
active episode from a `previewEpisode`.

`PodcastDetailScreen` renders the *podcast* description, not episode show notes,
and is out of scope.

## Decisions

- **Click behavior:** seek **and** start playing, even if currently paused.
- **Formats detected:** `MM:SS` and `H:MM:SS` (see Detection rules). Surrounding
  `()` / `[]` tolerated. No labeled formats (`1h2m3s`).
- **Link style:** theme `primary` color + underline.

## Approach

Chosen: build an `AnnotatedString` with `LinkAnnotation.Clickable` spans over the
detected timestamps. Detection is a pure, Compose-free function; the Composable
maps matches to clickable spans. (Rejected: WebView + JS bridge — heavy, breaks
theming/selection consistency. Rejected: manual `pointerInput` hit-testing —
reinvents `LinkAnnotation`.)

The key seam is **detection (pure) vs. rendering (Compose)**, keeping the
regex/parsing unit-testable without a UI harness, matching the repo's existing
`*SourceTest` convention.

## Components

### 1. New pure helper — `ui/player/ShowNotesTimestamps.kt`

```kotlin
data class TimestampLink(val start: Int, val endExclusive: Int, val positionMs: Long)

fun findTimestampLinks(text: String): List<TimestampLink>
```

Scans the already-flattened plain text and returns matches in order.

**Detection rules:**
- `H:MM:SS` / `HH:MM:SS`: hours 1–2 digits, minutes `00–59`, seconds `00–59`.
- `M:SS` / `MM:SS`: minutes 1+ digits (so `90:00` works), seconds `00–59`.
- Word-boundary guarded: must not fire inside a larger number, date, or URL
  (e.g. `1234:56`, `192.168:80`-style fragments, timestamps glued to other
  digits). Leading/trailing context like spaces, `(`, `[`, `-` is allowed.
- Surrounding `()` / `[]` are tolerated but **not** included in the link range —
  only the timestamp text itself is the link.
- `positionMs` is computed directly from captured groups (no dependency on the
  data-layer `parseCueTimeMs`).

### 2. Render change — `ShowNotesPage` (`PlayerScreen.kt`)

- Build an `AnnotatedString` inside `remember(showNotes)` from
  `findTimestampLinks(showNotes)`: append non-timestamp text verbatim; wrap each
  timestamp range in `LinkAnnotation.Clickable` styled `primary` + underline,
  whose listener invokes the new `onSeekAndPlay(positionMs)`.
- Keep the `SelectionContainer` wrapper (links stay selectable and tappable).
- The "Loading show notes…", "No show notes available.", and `episode == null`
  branches are unchanged.

### 3. Click behavior — `PlayerViewModel`

```kotlin
fun seekToAndPlay(positionMs: Long)
```

- If `previewEpisode != null` and it differs from the active episode
  (`state.episode`), start it via `play(previewEpisode)`.
- Else if not currently playing, `resume()`.
- Then `seekTo(positionMs)`.

Ordering: ensure the target episode is the active one **before** seeking, so a
`play()` that resets position cannot clobber the seek.

Wire `ShowNotesPage`'s new callback to `viewModel::seekToAndPlay` (the existing
`onSeek = viewModel::seekTo` for the progress slider is unchanged).

## Data flow

1. `description` (HTML) → flattened to plain `String` `showNotes` (existing).
2. `findTimestampLinks(showNotes)` → `List<TimestampLink>` (pure).
3. Composable builds `AnnotatedString` with clickable spans.
4. Tap → `LinkAnnotation` listener → `onSeekAndPlay(positionMs)` →
   `viewModel.seekToAndPlay` → repository `play`/`resume` + `seekTo`.

## Error / edge handling

- No timestamps found → annotated string is plain text, identical to today.
- Blank / null description → existing empty-state branches unchanged.
- Malformed near-timestamps (e.g. `12:99`, `1234:56`) → not matched, rendered as
  plain text.
- Overlapping/adjacent matches → regex scans left-to-right, non-overlapping.

## Testing (TDD)

- `ShowNotesTimestampsTest` (pure JVM):
  - Accepts `12:34`, `1:02:03`, `01:02:03`, `90:00`, `(12:34)`, `[1:00]`.
  - Rejects `12:99`, `1234:56`, timestamps embedded in URLs/larger numbers.
  - Correct `positionMs` for representative inputs.
  - Correct `start`/`endExclusive` ranges (parens excluded).
  - Multiple timestamps in one string returned in order.
- Source test (extend `PlayerScreenSourceTest` or add alongside): assert the
  show-notes rendering uses the annotated-string + `LinkAnnotation` path and that
  the click callback is wired to `seekToAndPlay`.

## Out of scope (YAGNI)

- Labeled duration formats (`1h2m3s`, `5m30s`).
- Chapter-list UI or jump menu.
- Changes to `PodcastDetailScreen`.
