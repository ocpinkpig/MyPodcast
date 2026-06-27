# Shows Updated Time Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show relative latest-update labels on Library Shows cards instead of podcast publisher names.

**Architecture:** Reuse the existing `LibraryViewModel` subscribed-episodes observer to derive the latest positive `Episode.publishedAt` per podcast. Add an optional metadata override to `PodcastCard`, and format compact relative labels in `LibraryScreen` before passing them to the card.

**Tech Stack:** Kotlin, Jetpack Compose, StateFlow, coroutines Flow, Robolectric/JUnit.

---

## File Structure

- Modify `app/src/main/java/com/example/mypodcast/ui/library/LibraryViewModel.kt`: add `latestEpisodePublishedAtByPodcastId` to `LibraryUiState` and update it from observed subscribed episodes.
- Modify `app/src/main/java/com/example/mypodcast/ui/library/LibraryScreen.kt`: add `formatUpdatedAgo(...)` and pass formatted metadata to Shows-tab `PodcastCard`.
- Modify `app/src/main/java/com/example/mypodcast/ui/components/PodcastCard.kt`: add an optional `metadataText` parameter that defaults to `podcast.artistName`.
- Modify `app/src/test/java/com/example/mypodcast/ui/library/LibraryViewModelTest.kt`: add tests for latest timestamp state and relative label formatting.

## Task 1: Test Library latest timestamp state

**Files:**
- Modify: `app/src/test/java/com/example/mypodcast/ui/library/LibraryViewModelTest.kt`

- [ ] **Step 1: Write the failing ViewModel test**

Add this test near `newEpisodeCounts_areExposedFromRepositoryFlow`:

```kotlin
@Test
fun latestEpisodePublishedAtByPodcastId_usesNewestSubscribedEpisodePerPodcast() = runTest {
    val firstPodcast = podcast(id = 1L)
    val secondPodcast = podcast(id = 2L)
    val libraryRepository = FakeLibraryRepository(listOf(firstPodcast, secondPodcast))
    val episodeRepository = FakeEpisodeRepository(
        existingEpisodes = mapOf(
            1L to listOf(
                episode("older", 1L).copy(publishedAt = 1_000L),
                episode("newer", 1L).copy(publishedAt = 3_000L)
            ),
            2L to listOf(
                episode("only", 2L).copy(publishedAt = 2_000L)
            )
        ),
        fetchedEpisodes = emptyMap()
    )
    val viewModel = LibraryViewModel(
        getLibrary = GetLibraryUseCase(libraryRepository),
        libraryRepository = libraryRepository,
        episodeRepository = episodeRepository,
        savedMomentRepository = FakeSavedMomentRepository(),
        downloadEpisodeUseCase = downloadEpisodeUseCase(libraryRepository),
        playerRepository = FakePlayerRepository()
    )
    advanceUntilIdle()

    assertEquals(
        mapOf(
            1L to 3_000L,
            2L to 2_000L
        ),
        viewModel.uiState.value.latestEpisodePublishedAtByPodcastId
    )
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.ui.library.LibraryViewModelTest.latestEpisodePublishedAtByPodcastId_usesNewestSubscribedEpisodePerPodcast"
```

Expected: FAIL because `latestEpisodePublishedAtByPodcastId` does not exist.

- [ ] **Step 3: Implement the state derivation**

In `LibraryUiState`, add:

```kotlin
val latestEpisodePublishedAtByPodcastId: Map<Long, Long> = emptyMap(),
```

In the subscribed-episodes collector, replace the `collect { episodes -> ... }` block with logic that stores `subscriptionEpisodes`, derives newest positive timestamps by `podcastId`, and preserves search refresh behavior:

```kotlin
.collect { episodes ->
    subscriptionEpisodes = episodes
    val latestPublishedAtByPodcastId = episodes
        .filter { it.publishedAt > 0L }
        .groupBy { it.podcastId }
        .mapValues { (_, podcastEpisodes) ->
            podcastEpisodes.maxOf { it.publishedAt }
        }
    _uiState.update { state ->
        val updatedState = state.copy(
            latestEpisodePublishedAtByPodcastId = latestPublishedAtByPodcastId
        )
        if (updatedState.isSearchActive && updatedState.searchQuery.isNotBlank()) {
            updatedState.withSearchResults(subscriptionEpisodes)
        } else {
            updatedState
        }
    }
}
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.ui.library.LibraryViewModelTest.latestEpisodePublishedAtByPodcastId_usesNewestSubscribedEpisodePerPodcast"
```

Expected: PASS.

## Task 2: Test and implement relative update formatting

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/library/LibraryScreen.kt`
- Modify: `app/src/test/java/com/example/mypodcast/ui/library/LibraryViewModelTest.kt`

- [ ] **Step 1: Write the failing formatter test**

Add this test to `LibraryViewModelTest`:

```kotlin
@Test
fun formatUpdatedAgo_returnsCompactRelativeLabels() {
    val now = 1_000_000_000L

    assertEquals("Updated just now", formatUpdatedAgo(now - 30_000L, now))
    assertEquals("Updated 30m ago", formatUpdatedAgo(now - 30 * 60_000L, now))
    assertEquals("Updated 6h ago", formatUpdatedAgo(now - 6 * 60 * 60_000L, now))
    assertEquals("Updated 1d ago", formatUpdatedAgo(now - 24 * 60 * 60_000L, now))
    assertEquals("Updated 2w ago", formatUpdatedAgo(now - 14 * 24 * 60 * 60_000L, now))
    assertEquals("Updated 3mo ago", formatUpdatedAgo(now - 90 * 24 * 60 * 60_000L, now))
    assertEquals("Updated 1y ago", formatUpdatedAgo(now - 365 * 24 * 60 * 60_000L, now))
    assertEquals("Updated just now", formatUpdatedAgo(now + 60_000L, now))
    assertEquals("", formatUpdatedAgo(0L, now))
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.ui.library.LibraryViewModelTest.formatUpdatedAgo_returnsCompactRelativeLabels"
```

Expected: FAIL because `formatUpdatedAgo` does not exist.

- [ ] **Step 3: Implement `formatUpdatedAgo`**

Add this internal helper near the other private formatting helpers in `LibraryScreen.kt`:

```kotlin
internal fun formatUpdatedAgo(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    if (epochMillis <= 0L) return ""

    val elapsedMs = (nowMillis - epochMillis).coerceAtLeast(0L)
    val minutes = elapsedMs / 60_000L
    val hours = elapsedMs / (60L * 60_000L)
    val days = elapsedMs / (24L * 60L * 60_000L)

    return when {
        minutes < 1L -> "Updated just now"
        hours < 1L -> "Updated ${minutes}m ago"
        days < 1L -> "Updated ${hours}h ago"
        days < 7L -> "Updated ${days}d ago"
        days < 30L -> "Updated ${days / 7L}w ago"
        days < 365L -> "Updated ${days / 30L}mo ago"
        else -> "Updated ${days / 365L}y ago"
    }
}
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.ui.library.LibraryViewModelTest.formatUpdatedAgo_returnsCompactRelativeLabels"
```

Expected: PASS.

## Task 3: Wire metadata into Shows cards

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/components/PodcastCard.kt`
- Modify: `app/src/main/java/com/example/mypodcast/ui/library/LibraryScreen.kt`

- [ ] **Step 1: Add `PodcastCard` metadata override**

Change the `PodcastCard` signature to:

```kotlin
fun PodcastCard(
    podcast: Podcast,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    newEpisodeCount: Int = 0,
    metadataText: String = podcast.artistName
)
```

Then change the metadata `Text` from:

```kotlin
text = podcast.artistName,
```

to:

```kotlin
text = metadataText,
```

- [ ] **Step 2: Pass update metadata from the Shows tab**

In the Shows `PodcastCard` call in `LibraryScreen.kt`, add:

```kotlin
metadataText = formatUpdatedAgo(
    state.latestEpisodePublishedAtByPodcastId[podcast.id] ?: 0L
).ifBlank { podcast.artistName }
```

- [ ] **Step 3: Run the Library tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.ui.library.LibraryViewModelTest"
```

Expected: PASS.

## Task 4: Final verification

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run focused tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.mypodcast.ui.library.LibraryViewModelTest"
```

Expected: PASS.

- [ ] **Step 2: Run a debug build compile check**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: PASS.
