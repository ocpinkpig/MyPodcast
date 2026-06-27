# Your Top Shows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Home-tab `Your Top Shows` carousel ranked by local played/history episodes.

**Architecture:** Use existing Room episode playback fields to compute top shows; no database migration is needed. Expose a reactive `Flow<List<TopShow>>` from `EpisodeRepository`, wrap it in an `ObserveTopShowsUseCase`, and render it above featured rows in `HomeScreen`.

**Tech Stack:** Kotlin, Room, coroutines Flow, Hilt ViewModel, Jetpack Compose, Robolectric unit tests.

---

## File Structure

- Create `app/src/main/java/com/example/mypodcast/domain/model/TopShow.kt`: domain model containing a `Podcast`, played episode count, and latest play timestamp.
- Modify `app/src/main/java/com/example/mypodcast/data/local/dao/EpisodeDao.kt`: add aggregate `TopShowRow` query over `episodes` joined to `podcasts`.
- Modify `app/src/main/java/com/example/mypodcast/domain/repository/EpisodeRepository.kt`: expose `observeTopShows(limit: Int)`.
- Modify `app/src/main/java/com/example/mypodcast/data/repository/EpisodeRepositoryImpl.kt`: map DAO rows to `TopShow`.
- Create `app/src/main/java/com/example/mypodcast/domain/usecase/podcast/ObserveTopShowsUseCase.kt`: small use case for Home.
- Modify `app/src/main/java/com/example/mypodcast/ui/home/HomeViewModel.kt`: observe top shows and keep featured loading behavior unchanged.
- Modify `app/src/main/java/com/example/mypodcast/ui/home/HomeScreen.kt`: render the new carousel first, hidden when empty.
- Modify `app/src/test/java/com/example/mypodcast/data/local/dao/EpisodeDaoTest.kt`: prove ranking, tie-break, and history filters.
- Create `app/src/test/java/com/example/mypodcast/ui/home/HomeViewModelTest.kt`: prove Home exposes top shows and tolerates top-show failures.
- Update fake `EpisodeRepository` implementations in existing tests to satisfy the new interface method.

---

### Task 1: DAO Aggregate Query

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/data/local/dao/EpisodeDao.kt`
- Modify: `app/src/test/java/com/example/mypodcast/data/local/dao/EpisodeDaoTest.kt`

- [ ] **Step 1: Write failing DAO tests**

Add tests that insert several podcasts and episodes, then assert:

```kotlin
@Test
fun observeTopShows_ordersByPlayedCountThenLatestPlayback() = runTest {
    db.podcastDao().upsertAll(
        listOf(
            podcast(id = 1L, title = "Most Played"),
            podcast(id = 2L, title = "Recent Tie Winner"),
            podcast(id = 3L, title = "Older Tie")
        )
    )
    episodeDao.upsertAll(
        listOf(
            episode("one-a", podcastId = 1L, lastPlayedAt = 1_000L),
            episode("one-b", podcastId = 1L, playbackPosition = 60_000L),
            episode("one-c", podcastId = 1L, isPlayed = true),
            episode("two-a", podcastId = 2L, lastPlayedAt = 5_000L),
            episode("two-b", podcastId = 2L, lastPlayedAt = 4_000L),
            episode("three-a", podcastId = 3L, lastPlayedAt = 3_000L),
            episode("three-b", podcastId = 3L, lastPlayedAt = 2_000L),
            episode("ignored", podcastId = 3L)
        )
    )

    val topShows = episodeDao.observeTopShows(limit = 10).first()

    assertEquals(listOf(1L, 2L, 3L), topShows.map { it.id })
    assertEquals(listOf(3, 2, 2), topShows.map { it.playedEpisodeCount })
}
```

```kotlin
@Test
fun observeTopShows_omitsShowsWithoutPlaybackHistory() = runTest {
    db.podcastDao().upsertAll(
        listOf(
            podcast(id = 1L, title = "Played"),
            podcast(id = 2L, title = "Never Played")
        )
    )
    episodeDao.upsertAll(
        listOf(
            episode("played", podcastId = 1L, playbackPosition = 60_000L),
            episode("never", podcastId = 2L)
        )
    )

    val topShows = episodeDao.observeTopShows(limit = 10).first()

    assertEquals(listOf(1L), topShows.map { it.id })
}
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.mypodcast.data.local.dao.EpisodeDaoTest'
```

Expected: compile failure because `observeTopShows`, `TopShowRow`, and helper parameters do not exist yet.

- [ ] **Step 3: Implement DAO row and query**

Add to `EpisodeDao.kt`:

```kotlin
@Query(
    """
    SELECT
        podcasts.id AS id,
        podcasts.title AS title,
        podcasts.artworkUrl AS artworkUrl,
        podcasts.artistName AS artistName,
        podcasts.feedUrl AS feedUrl,
        podcasts.description AS description,
        podcasts.genres AS genres,
        podcasts.episodeCount AS episodeCount,
        COUNT(episodes.guid) AS playedEpisodeCount,
        MAX(episodes.lastPlayedAt) AS latestPlayedAt
    FROM episodes
    INNER JOIN podcasts ON podcasts.id = episodes.podcastId
    WHERE episodes.lastPlayedAt > 0
        OR episodes.playbackPosition > 0
        OR episodes.isPlayed = 1
    GROUP BY podcasts.id
    ORDER BY playedEpisodeCount DESC, latestPlayedAt DESC, podcasts.title ASC
    LIMIT :limit
    """
)
fun observeTopShows(limit: Int): Flow<List<TopShowRow>>
```

Add the row type:

```kotlin
data class TopShowRow(
    val id: Long,
    val title: String,
    val artworkUrl: String,
    val artistName: String,
    val feedUrl: String,
    val description: String?,
    val genres: String,
    val episodeCount: Int,
    val playedEpisodeCount: Int,
    val latestPlayedAt: Long
)
```

- [ ] **Step 4: Run DAO tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.mypodcast.data.local.dao.EpisodeDaoTest'
```

Expected: tests pass.

---

### Task 2: Repository and Use Case

**Files:**
- Create: `app/src/main/java/com/example/mypodcast/domain/model/TopShow.kt`
- Modify: `app/src/main/java/com/example/mypodcast/domain/repository/EpisodeRepository.kt`
- Modify: `app/src/main/java/com/example/mypodcast/data/repository/EpisodeRepositoryImpl.kt`
- Create: `app/src/main/java/com/example/mypodcast/domain/usecase/podcast/ObserveTopShowsUseCase.kt`
- Modify: test fakes implementing `EpisodeRepository`

- [ ] **Step 1: Add domain model**

Create:

```kotlin
package com.example.mypodcast.domain.model

data class TopShow(
    val podcast: Podcast,
    val playedEpisodeCount: Int,
    val latestPlayedAt: Long
)
```

- [ ] **Step 2: Add repository API and mapper**

Add to `EpisodeRepository`:

```kotlin
fun observeTopShows(limit: Int = 10): Flow<List<TopShow>>
```

Implement in `EpisodeRepositoryImpl`:

```kotlin
override fun observeTopShows(limit: Int): Flow<List<TopShow>> =
    episodeDao.observeTopShows(limit).map { rows ->
        rows.map { row ->
            TopShow(
                podcast = Podcast(
                    id = row.id,
                    title = row.title,
                    artworkUrl = row.artworkUrl,
                    artistName = row.artistName,
                    feedUrl = row.feedUrl,
                    description = row.description,
                    genres = row.genres.split(",").filter { it.isNotBlank() },
                    episodeCount = row.episodeCount
                ),
                playedEpisodeCount = row.playedEpisodeCount,
                latestPlayedAt = row.latestPlayedAt
            )
        }
    }
```

- [ ] **Step 3: Add use case**

Create:

```kotlin
package com.example.mypodcast.domain.usecase.podcast

import com.example.mypodcast.domain.model.TopShow
import com.example.mypodcast.domain.repository.EpisodeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTopShowsUseCase @Inject constructor(
    private val repository: EpisodeRepository
) {
    operator fun invoke(limit: Int = DEFAULT_LIMIT): Flow<List<TopShow>> =
        repository.observeTopShows(limit)

    companion object {
        const val DEFAULT_LIMIT = 10
    }
}
```

- [ ] **Step 4: Update existing test fakes**

Every fake `EpisodeRepository` in tests should add:

```kotlin
override fun observeTopShows(limit: Int): Flow<List<TopShow>> = flowOf(emptyList())
```

- [ ] **Step 5: Run compile-focused tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.mypodcast.data.local.dao.EpisodeDaoTest'
```

Expected: compile and tests pass.

---

### Task 3: Home ViewModel State

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/home/HomeViewModel.kt`
- Create: `app/src/test/java/com/example/mypodcast/ui/home/HomeViewModelTest.kt`

- [ ] **Step 1: Write failing ViewModel tests**

Add tests with fake use cases asserting:

```kotlin
@Test
fun init_exposesTopShowsFromUseCase() = runTest {
    val topShow = TopShow(podcast = podcast(id = 1L), playedEpisodeCount = 3, latestPlayedAt = 9_000L)

    val viewModel = HomeViewModel(
        getFeaturedPodcasts = GetFeaturedPodcastsUseCase(FakePodcastRepository(featured = mapOf("Technology" to emptyList()))),
        observeTopShows = ObserveTopShowsUseCase(FakeEpisodeRepository(topShows = flowOf(listOf(topShow))))
    )
    advanceUntilIdle()

    assertEquals(listOf(topShow), viewModel.uiState.value.topShows)
}
```

```kotlin
@Test
fun topShowsFailure_doesNotFailFeaturedHomeContent() = runTest {
    val featuredPodcast = podcast(id = 2L)

    val viewModel = HomeViewModel(
        getFeaturedPodcasts = GetFeaturedPodcastsUseCase(FakePodcastRepository(featured = mapOf("Technology" to listOf(featuredPodcast)))),
        observeTopShows = ObserveTopShowsUseCase(FakeEpisodeRepository(topShows = flow { error("boom") }))
    )
    advanceUntilIdle()

    assertEquals(false, viewModel.uiState.value.isLoading)
    assertEquals(mapOf("Technology" to listOf(featuredPodcast)), viewModel.uiState.value.featuredByCategory)
    assertEquals(emptyList<TopShow>(), viewModel.uiState.value.topShows)
    assertEquals(null, viewModel.uiState.value.error)
}
```

- [ ] **Step 2: Implement ViewModel state**

Update `HomeUiState`:

```kotlin
val topShows: List<TopShow> = emptyList()
```

Inject `ObserveTopShowsUseCase`, start a top-shows collector in `init`, and keep `load()` for featured content:

```kotlin
init {
    observeTopShows()
    load()
}

private fun observeTopShows() {
    viewModelScope.launch {
        runCatching {
            observeTopShowsUseCase().collect { topShows ->
                _uiState.update { it.copy(topShows = topShows) }
            }
        }.onFailure {
            _uiState.update { it.copy(topShows = emptyList()) }
        }
    }
}
```

- [ ] **Step 3: Run ViewModel tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.mypodcast.ui.home.HomeViewModelTest'
```

Expected: tests pass.

---

### Task 4: Home Carousel UI

**Files:**
- Modify: `app/src/main/java/com/example/mypodcast/ui/home/HomeScreen.kt`

- [ ] **Step 1: Add reusable carousel helper**

Extract the current header-plus-row rendering into a private composable:

```kotlin
@Composable
private fun PodcastCarousel(
    title: String,
    podcasts: List<Podcast>,
    topPadding: Dp,
    onPodcastClick: (Long) -> Unit,
    metadataText: (Podcast) -> String = { it.artistName }
)
```

The body should reuse the existing `BoxWithConstraints`, `LazyRow`, `PodcastCard`, `PodcastCardGridDefaults.cardWidthFor(maxWidth)`, and `Modifier.width(...)` behavior.

- [ ] **Step 2: Render top shows first**

In `HomeContent`, before featured categories:

```kotlin
if (state.topShows.isNotEmpty()) {
    item(key = "header_top_shows") {
        Text(
            text = "Your Top Shows",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
        )
    }
    item(key = "row_top_shows") {
        TopShowsRow(
            topShows = state.topShows,
            onPodcastClick = onPodcastClick
        )
    }
}
```

Use metadata formatting:

```kotlin
private fun formatPlayedEpisodeCount(count: Int): String =
    if (count == 1) "1 played" else "$count played"
```

- [ ] **Step 3: Keep featured spacing correct**

When top shows are present, the first featured row should use `top = 8.dp`; when top shows are absent, the first featured row should keep `top = 16.dp`.

- [ ] **Step 4: Run Home ViewModel tests again**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.mypodcast.ui.home.HomeViewModelTest'
```

Expected: tests pass.

---

### Task 5: Final Verification

**Files:**
- All files touched above

- [ ] **Step 1: Run focused tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.mypodcast.data.local.dao.EpisodeDaoTest' --tests 'com.example.mypodcast.ui.home.HomeViewModelTest'
```

Expected: both test classes pass.

- [ ] **Step 2: Run full unit test suite**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: all unit tests pass.

- [ ] **Step 3: Inspect git diff**

Run:

```bash
git diff --stat
git diff --check
git status --short
```

Expected: no whitespace errors; changed files match the plan.
