# Backup and Restore Design

## Summary

Let the listener export their library data — subscribed shows, download list, saved moments, favorites, queue, and play history — to a single JSON file saved anywhere the system file picker can reach (phone storage, Google Drive, or any other document provider). After a reinstall, importing that file merges everything back, and episodes that were downloaded before are re-downloaded automatically in the background. Audio files are never included in the backup.

## Requirements

- Export all user-owned library state to one versioned JSON file via the Storage Access Framework (`ACTION_CREATE_DOCUMENT`), so phone storage and Google Drive both work with no extra permissions or SDKs.
- Import a backup file via `ACTION_OPEN_DOCUMENT`, show a summary of its contents, and merge it into the current database after the user confirms.
- Merge, never wipe: importing must not delete or overwrite fresher local data.
- After import, re-download previously downloaded episodes in the background with WorkManager, with a progress notification and in-app progress.
- Do not export episode audio, transcript files, or artwork caches.
- Entry point: a `Backup and restore` item in the existing Library top-bar overflow menu (currently a placeholder), opening a dedicated screen.

## Backup Format

A single JSON file, default name `MyPodcast-backup-YYYY-MM-DD.json`, serialized with Gson (already a dependency):

```json
{
  "formatVersion": 1,
  "exportedAt": 1783000000000,
  "appVersion": "1.0.3",
  "podcasts": [ ... ],
  "subscriptions": [ ... ],
  "episodes": [ ... ],
  "downloads": [ ... ],
  "queue": [ ... ],
  "moments": [ ... ]
}
```

- `podcasts`: full `PodcastEntity` rows for every podcast referenced by any exported item (subscription, episode, download, queue, or moment). Including `feedUrl` lets a fresh install refresh feeds immediately.
- `subscriptions`: `podcastId` + `subscribedAt`.
- `episodes`: full `EpisodeEntity` rows, but only rows that carry user state — `isFavorite`, `isPlayed`, `playbackPosition > 0`, `lastPlayedAt > 0`, referenced by a queue item, a saved moment, or a download. This keeps the file small (metadata for tens of episodes, not thousands) while making favorites, history, moments, and the queue restorable before any RSS fetch happens.
- `downloads`: `episodeGuid` + `podcastId` + `downloadedAt`. `localFilePath`, `fileSizeBytes`, and `transcriptStatus` are machine-specific and excluded; they are regenerated on re-download.
- `queue`: `episodeGuid` + `position`.
- `moments`: `SavedMomentEntity` rows without the auto-generated `id`.

Export streams the JSON to the SAF-provided `Uri` via `ContentResolver.openOutputStream`. The data set is small, so building the DTO tree in memory is acceptable.

## Import Semantics

Import runs in three phases:

1. **Parse and validate.** Read the `Uri`, parse JSON, check `formatVersion <= supported`. On failure, show an error and change nothing. On success, show a confirmation dialog summarizing counts (shows, favorites, downloads, moments, queue items).
2. **Merge into Room** (single transaction):
   - Podcasts: insert if missing; ignore if present (local cache is fresher or equal).
   - Subscriptions: insert-or-ignore.
   - Episodes: insert full row if missing. If present, merge user-state fields only — `isFavorite = local OR backup`, `isPlayed = local OR backup`, `playbackPosition = max`, `lastPlayedAt = max`. Feed-derived fields keep local values.
   - Queue: keep existing local queue order; append backup items not already queued, in backup order, after the current tail.
   - Moments: insert unless a moment with the same `(episodeGuid, positionMs, createdAt)` exists.
   - Downloads: not written here. A `downloaded_episodes` row is only created when the audio file actually exists, so restore rows are written by the re-download worker on completion (via the existing `saveDownload` path). Backup entries whose episode is already downloaded locally are skipped.
3. **Re-download.** Enqueue one WorkManager job carrying the list of episode GUIDs to restore (see below), then kick off a normal subscription feed refresh in the background.

## Architecture

New code follows the existing clean-architecture layering:

- `data/backup/BackupSerializer.kt` — DTOs (`BackupFile`, `BackupPodcast`, …) and Gson read/write. DTOs are decoupled from Room entities so the entity schema can evolve behind `formatVersion`.
- `data/backup/BackupRepositoryImpl.kt` + `domain/repository/BackupRepository.kt` — `exportTo(uri)`, `inspect(uri): BackupSummary`, `importFrom(uri): ImportResult`. Uses the DAOs; the merge runs in a Room `withTransaction`.
- DAO additions: bulk `getAll`-style queries for export, insert-or-ignore variants, and a user-state merge update for episodes.
- `domain/usecase/backup/ExportLibraryUseCase.kt`, `InspectBackupUseCase.kt`, `ImportLibraryUseCase.kt`.
- `work/RestoreDownloadsWorker.kt` — a Hilt `CoroutineWorker` (new deps: `androidx.work:work-runtime-ktx`, `androidx.hilt:hilt-work`). Input: episode GUIDs. Downloads sequentially by reusing the same OkHttp streaming logic as `DownloadEpisodeUseCase` (refactored so both call one shared downloader), calling `saveDownload` per completed file. Runs as expedited/foreground work with a progress notification ("Restoring downloads · 3 of 12"), constraint `NetworkType.CONNECTED`, retry with backoff on failure; already-downloaded GUIDs are skipped so retries are idempotent.
- UI: `ui/backup/BackupRestoreScreen.kt` + `BackupRestoreViewModel.kt`, new `@Serializable data object BackupRestoreNavKey : NavKey`, an entry in the main `NavDisplay`, and the Library overflow `MoreVert` becomes a `DropdownMenu` with `Backup and restore`.

## UI

Entry point: Library top bar → overflow menu → `Backup and restore` (replaces the current no-op placeholder).

`Backup and restore` screen (see mockup in the design conversation):

- **Export card** — description, `Export…` button. Tapping launches `ACTION_CREATE_DOCUMENT` (`application/json`, suggested dated filename). While writing, the button shows a spinner; on success a snackbar shows "Backup saved" and a "Last export" timestamp (stored in `SharedPreferences`) updates.
- **Import card** — description noting audio re-downloads in the background, `Choose backup file…` button launching `ACTION_OPEN_DOCUMENT`. After parsing, an `AlertDialog` shows the file name and counts (shows, favorites, downloads to restore, moments, queue items) with the note "Your current library is kept. Backup items are merged in." Confirm runs the merge, shows "Backup restored" and, when applicable, "Restoring N downloads in the background".
- **Restore progress** — while `RestoreDownloadsWorker` runs, the import card shows a linear progress bar ("Restoring downloads · 3 of 12") observed via `WorkManager.getWorkInfosForUniqueWorkFlow`. The system notification shows the same progress, and the Library Downloads tab fills in as episodes complete.
- Footnote: "Backups include your library data only, not audio files."

Styling follows the existing screens: Material 3, `TopAppBar` with back arrow, the app's purple accent, cards with the app's standard shapes.

## Error Handling

- **Export write failure** (SAF stream error, no space): snackbar with the failure; a partially written document is truncated/overwritten on the next attempt, and no app state changes.
- **Unreadable or corrupt import file**: "This file isn't a MyPodcast backup" — nothing imported.
- **Backup from a newer app** (`formatVersion` unsupported): "This backup was made by a newer version of MyPodcast. Update the app to restore it."
- **Merge failure**: the Room transaction rolls back; error snackbar; nothing partially applied.
- **Download failures during restore**: the worker skips to the next episode and retries failed ones via WorkManager backoff; episodes that still fail simply remain not-downloaded (they stay restorable by re-running import or downloading manually). Import success never depends on downloads finishing.
- **User cancels the picker**: silently return to the screen.

## Testing

- Round-trip unit test: export → parse → import into empty DAOs restores subscriptions, favorites, positions, queue order, moments, and the restore-download list.
- Merge-rule unit tests: local-wins for feed fields, OR/max for user state, queue append without duplicates, moment dedupe, skip already-downloaded episodes.
- Validation tests: corrupt JSON, wrong root shape, and `formatVersion` newer than supported are rejected with the right error and no writes.
- Worker test: given GUIDs where some are already downloaded, only missing ones are fetched and `saveDownload` is called per success (using the shared downloader with a mock OkHttp client).
- Build + existing test suite to verify Room/Hilt/WorkManager wiring compiles.

## Out of Scope

- Automatic/scheduled backups and cloud sync (manual export/import only).
- Exporting audio files, transcript files, or image caches.
- OPML interchange with other podcast apps (could reuse `BackupRepository` later).
- A general Settings screen — the overflow-menu entry point keeps this YAGNI-clean, and can move into Settings if one is added later.
