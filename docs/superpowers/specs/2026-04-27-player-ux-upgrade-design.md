# Player UX Upgrade Design

## Purpose

Upgrade the podcast player's listening experience before broader app polish. The first pass should make playback controls easier to use, make show notes readable while playback remains available, and reduce visual clutter in the current player screen.

## Scope

This design covers the full-screen player and related player controls. It does not add a queue, saved episodes, transcript search, or database schema changes.

## User Experience

The player screen uses a two-page horizontal pager.

Page 1 is the default playback page. It shows artwork, episode title, status chips, progress, large playback controls, and quick access to speed and sleep timer settings.

Page 2 is the show-notes page. It shows compact episode context at the top, readable show notes in the main content area, and compact playback controls at the bottom. Users can keep playing, pausing, and skipping while reading notes.

Page indicators make the two-page model visible. The screen opens on page 1 every time the player is entered.

## Playback Page

The playback page includes:

- Top app bar with back navigation and the "Now Playing" title.
- Square episode artwork with rounded corners.
- Episode title, limited to two lines.
- Status chips for downloaded/offline state, playback speed, and sleep timer state.
- A progress slider with elapsed and total duration labels.
- Large play/pause control.
- 30-second skip back and skip forward controls.
- Quick buttons for speed and sleep timer.

The layout should prioritize stable touch targets and avoid crowding on small screens. If vertical space is tight, artwork can scale down before controls become cramped.

## Show-Notes Page

The show-notes page includes:

- Compact episode context header with artwork thumbnail, title, and progress.
- Scrollable show notes rendered from the existing episode description after HTML cleanup.
- Compact bottom playback controls: skip back, play/pause, and skip forward.
- The same page indicator pattern as the playback page.

If an episode has no show notes, the page shows a quiet empty state instead of a blank area.

## Speed Sheet

The speed quick button opens a modal bottom sheet with the supported speed options:

- 0.5x
- 1.0x
- 1.5x
- 2.0x
- 2.5x
- 3.0x

The current speed is visibly selected. Choosing a speed updates playback through `PlayerViewModel.setSpeed` and closes the sheet.

## Sleep Timer Sheet

The sleep timer quick button opens a modal bottom sheet with:

- Off
- 15 minutes
- 30 minutes
- 60 minutes

When a timer is active, the playback page chip and sheet show the remaining time. Choosing Off cancels the timer through the existing view model behavior. Choosing a duration starts that timer and closes the sheet.

## Architecture

The implementation stays in the existing player UI boundary.

`PlayerScreen` should be decomposed into focused composables:

- `PlayerPager`
- `PlaybackPage`
- `ShowNotesPage`
- `PlayerProgress`
- `PrimaryPlaybackControls`
- `CompactPlaybackControls`
- `PlayerStatusChips`
- `SpeedSheet`
- `SleepTimerSheet`

`PlayerViewModel` continues to own playback actions through `PlayerRepository`. New sheet visibility and current pager page are local Compose state. No Room migration or repository contract change is required.

Show notes continue to use the current HTML-to-text cleanup approach. Rich clickable links, richer formatting, and source/podcast name display are out of scope until the domain model carries that data into the player.

## Error And Empty States

If the episode is not loaded yet, the player shows a loading state that preserves the main layout shape and avoids showing broken controls.

If playback reports an error, show the existing error message in a visible but compact surface near the controls.

If duration is unknown or zero, progress remains at zero and duration labels use the current formatter result.

If show notes are blank after cleanup, the notes page shows "No show notes available."

## Testing

Verification should include:

- `./gradlew :app:compileDebugKotlin` or an equivalent debug compile.
- A visual run on a small phone viewport/emulator to check that controls do not overlap or fall under system bars.
- Manual checks for speed selection, sleep timer start/cancel, progress scrubbing, page swiping, and show-notes scrolling.

Focused Compose tests can be added if the existing test setup supports stable player screen composition without broad Hilt/media setup.

## Out Of Scope

- Queue or Up Next behavior.
- Persisted player page selection.
- Transcript search or rich clickable show-note links.
- Saved episodes or mark-played state.
- App-wide visual redesign beyond player surfaces.
