# Shows Updated Time - Design

## Goal

On the Library Shows tab, replace each show card's publisher name with a relative latest-update label such as `Updated 6h ago` or `Updated 1d ago`.

## Data Source

The app does not store an update timestamp on `Podcast`. The source of truth will be the newest `Episode.publishedAt` value for each subscribed podcast. This matches the user's confirmed expectation that a show's update time means the latest published episode time.

If a subscribed podcast has no locally observed episodes yet, the card keeps the existing publisher-name fallback so the metadata line is not blank.

## UI Behavior

`PodcastCard` will continue to render artwork, title, metadata, and the new-episode badge. It will gain an optional metadata override string so the Shows tab can display relative update text while other callers preserve the current publisher-name behavior.

Relative labels use compact podcast-library copy:

- Less than one minute old: `Updated just now`
- Minutes: `Updated 30m ago`
- Hours: `Updated 6h ago`
- Days: `Updated 1d ago`
- Weeks: `Updated 2w ago`
- Months: `Updated 3mo ago`
- Years: `Updated 1y ago`

Future, missing, or invalid timestamps fall back to `Updated just now` for future values and no override for missing/invalid values.

## Architecture

`LibraryViewModel` already observes all episodes for subscribed podcasts for library search. That same observer will group episodes by `podcastId` and write the maximum positive `publishedAt` into `LibraryUiState.latestEpisodePublishedAtByPodcastId`.

`LibraryScreen` will format each timestamp at render time using a small pure helper with an injectable `nowMillis` parameter for unit tests. The Shows grid passes that formatted value to `PodcastCard(metadataText = ...)`.

No DAO or repository API changes are required.

## Testing

Focused JVM/Robolectric coverage will verify:

- `LibraryViewModel` exposes the latest episode timestamp per subscribed podcast.
- The relative formatter returns the expected compact labels for minutes, hours, days, weeks, months, years, future timestamps, and invalid timestamps.

The existing Library ViewModel test fakes already provide subscribed episodes, so this can be covered without instrumentation or network calls.
