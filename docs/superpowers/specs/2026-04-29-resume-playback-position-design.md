# Resume playback position + played indicator

**Date:** 2026-04-29
**Status:** Approved (design phase)

## Goal

When the user revisits an episode (streamed or downloaded), playback resumes from the position where they last paused. When an episode plays to its end, it is marked as played, its position is reset to 0, and the episode list shows a checkmark indicator.

## Why this is non-trivial

The schema and DAO method for `playbackPosition` already exist, and `PlayerController.loadEpisode()` already seeks to `episode.playbackPosition` on load. Two pieces are missing:

1. **Position is never persisted.** `EpisodeRepository.updatePlaybackPosition` exists but has no callers — positions live only in `PlayerState` and are lost when the app process dies.
2. **RSS refresh wipes positions.** `RssEpisode.toEntity()` does not carry `playbackPosition`, so `episodeDao.upsertAll(entities)` on re-fetch resets the column to its `0L` default.

We also want to distinguish "never played" from "finished" so the UI can show a played indicator.

## Design

### Data layer

**`EpisodeEntity`** — add column:
```kotlin
val isPlayed: Boolean = false
```

**`AppDatabase`** — bump Room version, add a migration that runs:
```sql
ALTER TABLE episodes ADD COLUMN isPlayed INTEGER NOT NULL DEFAULT 0
```

**`EpisodeDao`** — replace `updatePosition` with a method that writes both fields atomically, and add a batch lookup for the RSS-merge fix:
```kotlin
@Query("UPDATE episodes SET playbackPosition = :positionMs, isPlayed = :isPlayed WHERE guid = :guid")
suspend fun updateProgress(guid: String, positionMs: Long, isPlayed: Boolean)

@Query("SELECT * FROM episodes WHERE guid IN (:guids)")
suspend fun getByGuids(guids: List<String>): List<EpisodeEntity>
```

**`Episode` (domain model)** — add `val isPlayed: Boolean = false`.

**`EpisodeRepository`** — replace `updatePlaybackPosition` with `updateProgress(guid, positionMs, isPlayed)`.

**`EpisodeRepositoryImpl.fetchEpisodesForPodcast`** — before upsert, fetch existing entities by GUID and merge their `playbackPosition` and `isPlayed` into the new entities so a feed refresh does not wipe progress:
```kotlin
val parsedEntities = feed.episodes.map { it.toEntity(podcastId) }
val existing = episodeDao.getByGuids(parsedEntities.map { it.guid })
    .associateBy { it.guid }
val merged = parsedEntities.map { e ->
    val prior = existing[e.guid]
    if (prior == null) e
    else e.copy(playbackPosition = prior.playbackPosition, isPlayed = prior.isPlayed)
}
episodeDao.upsertAll(merged)
```

Both `EpisodeRepositoryImpl.toDomain()` and `LibraryRepositoryImpl.observeDownloadedEpisodes` already map `EpisodeEntity` → `Episode`; both must propagate `isPlayed`.

### Persistence wiring (`PlayerController`)

Inject `EpisodeRepository`. Persist position at six trigger points; all writes are fire-and-forget on the existing `scope`:

1. **Periodic** — inside `startPositionUpdates()`, write every ~5s (every 10th 500 ms tick) using the latest `currentPosition` of the currently loaded episode.
2. **On pause** — when `onIsPlayingChanged(false)` fires for the loaded episode.
3. **On manual seek** — inside `seekTo(positionMs)`.
4. **On episode switch** — at the top of `loadEpisode()`, before swapping `currentEpisode`, persist the outgoing episode's current position.
5. **On `STATE_ENDED`** — write `playbackPosition = 0, isPlayed = true`.
6. **On `release()`** — persist the final position so a forced kill still saves.

Guard:
- Skip writes when `currentEpisode == null`.
- Skip writes when `positionMs < 1_000` unless the trigger is `STATE_ENDED` (avoids stomping a fresh seek-to-0 with a stale earlier value).
- The periodic and pause writes always carry `isPlayed = currentEpisode.isPlayed` — they should not flip it; only the `STATE_ENDED` path sets `isPlayed = true`. If the user replays a played episode, manual seek/pause writes preserve `isPlayed = true` until the user makes any seek that moves position > 0, at which point we clear `isPlayed = false`. (Decision: we **clear** `isPlayed` on seek-to-non-zero so a partially re-listened episode doesn't keep its checkmark stale.)

### UI

**`EpisodeListItem`** — when `episode.isPlayed`, render a small `Icons.Filled.CheckCircle` next to the duration/date row, tinted with `MaterialTheme.colorScheme.primary`. Size ~16dp, with content description "Played".

No other UI changes. The mini-player and full player already display position via `PlayerState.positionMs`, which `loadEpisode` initializes from `episode.playbackPosition`.

## Files touched

| File | Change |
|---|---|
| `data/local/entity/EpisodeEntity.kt` | + `isPlayed` column |
| `data/local/AppDatabase.kt` | version bump + migration |
| `data/local/dao/EpisodeDao.kt` | `updateProgress`, `getByGuids` |
| `domain/model/Episode.kt` | + `isPlayed` field |
| `domain/repository/EpisodeRepository.kt` | rename method to `updateProgress` |
| `data/repository/EpisodeRepositoryImpl.kt` | RSS-merge fix; `isPlayed` in mapper |
| `data/repository/LibraryRepositoryImpl.kt` | `isPlayed` in mapper |
| `media/PlayerController.kt` | inject repo, persistence at 6 triggers |
| `ui/components/EpisodeListItem.kt` | checkmark icon |

## Out of scope

- A "mark as played / unplayed" user action (could be added later by exposing `updateProgress` through a use case).
- Filtering or sorting the episode list by played state.
- Per-podcast played counts in the library.
- Cross-device sync of progress.

## Testing

- Manual: play an episode for ~10 s, force-stop the app, relaunch, reopen the episode — playback resumes near the saved point.
- Manual: same as above for a downloaded episode.
- Manual: let an episode play to the end — checkmark appears in the list, position resets to 0, tapping play restarts from the beginning.
- Manual: pull-to-refresh the episode list for a podcast that has saved positions — positions and `isPlayed` survive the refresh (RSS-merge fix).
- Build: `BUILD SUCCESSFUL` is necessary but not sufficient. Install on a real device and verify each scenario above.
