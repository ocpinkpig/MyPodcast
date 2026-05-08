# Lockscreen Media Controls

## Goal

When an episode is playing, the system media notification — which Android renders on the lockscreen with artwork as a backdrop and transport controls — reflects the current episode and lets the user control playback without unlocking the device.

This is the standard Android "now playing" experience surfaced via `MediaSession`. The app is not modifying the device's lockscreen wallpaper.

## Current state

- `PlaybackService` is a Media3 `MediaSessionService`; the system media notification is auto-built from the bound `MediaSession`.
- `PlayerController.loadEpisode` sets `MediaMetadata` with `title` and `artworkUri`.
- Gaps:
  - No subtitle line on the lockscreen (no `artist` set).
  - Tapping the notification does not open the app.
  - The lockscreen "next" button is a no-op: the ExoPlayer timeline only contains the currently-loaded item, so it is disconnected from the in-app queue (`PlayerController` swaps `MediaItem`s on episode end via `playNextInQueue` instead of using a multi-item timeline).

## Changes

### 1. `MediaSession` session activity

In `PlaybackService.onCreate`, build a `PendingIntent` that launches `MainActivity` and pass it to `MediaSession.Builder.setSessionActivity(...)`. Tapping the lockscreen notification then opens the in-app player.

### 2. Richer `MediaMetadata`

In `PlayerController.loadEpisode`, extend the `MediaMetadata.Builder`:

- Keep `setTitle(episode.title)` and `setArtworkUri(...)`.
- Add `setArtist(episode.podcastTitle)` (the podcast show name) so the lockscreen displays a subtitle under the title. The exact field name on `Episode` will be confirmed at implementation time; if the model exposes a different name (e.g. `feedTitle`, `showTitle`), use that.

Artwork loading relies on Media3's default `DataSourceBitmapLoader` to fetch the URI. No manual Bitmap pre-load.

### 3. Wire lockscreen "next" to the in-app queue

Wrap `exoPlayer` in a `ForwardingPlayer` (call it `QueueAwarePlayer`) that:

- Overrides `getAvailableCommands()` to include `COMMAND_SEEK_TO_NEXT_MEDIA_ITEM` whenever the in-app queue is non-empty.
- Overrides `seekToNextMediaItem()` and `seekToNext()` to invoke `PlayerController.playNextInQueue()` instead of delegating to the underlying ExoPlayer.
- Leaves all other methods delegating to the wrapped ExoPlayer.

Pass the `ForwardingPlayer` to `MediaSession.Builder` in `PlaybackService`. `PlayerController` continues to hold and use the raw `ExoPlayer` for in-app calls (play, pause, seekTo, etc.).

`playNextInQueue` is currently `private`; it will be promoted to `internal` (or made package-visible) so the forwarding player can call it. Its behavior — pop head of queue, persist, `loadEpisode(next, autoPlay = true)` — is unchanged.

"Previous" keeps Media3's default behavior (seek to start of current item). No additional work.

### 4. Out of scope

- No custom `MediaNotification.Provider` — default Media3 styling is fine.
- No modification of the device wallpaper or system lockscreen background.
- No changes to the existing notification channel or foreground-service plumbing.
- No changes to the queue persistence model.

## Verification

Install on the connected device and:

1. Start an episode, lock the screen.
2. Confirm artwork appears, title is correct, and a subtitle (podcast show name) appears beneath it.
3. From the lockscreen: play/pause toggles state, seek bar scrubs, skip-15 buttons work.
4. Enqueue another episode, then on the lockscreen tap "next" — playback advances to the queued episode and the notification updates.
5. Tap the notification body — `MainActivity` opens to the player screen.
6. With the queue empty, the "next" button on the lockscreen is disabled or absent.
