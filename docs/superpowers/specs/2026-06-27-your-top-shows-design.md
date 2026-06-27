# Your Top Shows Design

## Summary

Add a `Your Top Shows` carousel at the top of the Home tab. It shows the podcasts the listener has played most often, using local playback history already stored on episode rows. The section is hidden until the app has enough playback history to rank at least one show.

## Requirements

- Display `Your Top Shows` before the existing featured category carousels on Home.
- Rank shows by the number of locally played/history episodes for each podcast.
- Break ties by the most recent local play timestamp.
- Reuse the existing podcast card layout and podcast-detail navigation.
- Show concise metadata on each card, such as `8 played`.
- Hide the section when there is no local playback history.

## Data Definition

A podcast counts toward `Your Top Shows` when at least one of its episodes is in local playback history:

- `lastPlayedAt > 0`
- `playbackPosition > 0`
- `isPlayed = 1`

The ranking query groups matching episode rows by `podcastId`, counts those rows, records the latest `lastPlayedAt`, joins the matching podcast records, then orders by play count descending and latest play descending.

This uses existing Room state and does not require a new table or migration.

## Architecture

- Add a Room query that returns top-show stats keyed by podcast.
- Expose those stats through the episode or podcast repository layer as a domain-friendly model.
- Add a use case for loading top played shows.
- Extend `HomeViewModel` state with the top-shows list and load it alongside featured podcasts.
- Keep the Home UI declarative by rendering the top carousel only when the list is non-empty.

## UI

The section uses the same horizontal carousel pattern as featured categories:

- Header text: `Your Top Shows`
- Cards: existing `PodcastCard`
- Metadata: pluralized play count, for example `1 played` or `8 played`
- Tap behavior: open the existing podcast detail destination

## Error Handling

If the top-shows query fails, Home should still load the featured category rows. The top section can be omitted rather than showing an additional error state. The existing full-page Home error remains reserved for featured content loading failures.

## Testing

- Unit test ranking behavior: count descending, recency tie-break, and history filters.
- Unit test Home state loading so top shows and featured categories can both populate.
- Build or unit-test the app to verify Room query compilation and Compose integration.
