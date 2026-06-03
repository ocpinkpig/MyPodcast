package com.example.mypodcast.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.model.SavedMoment
import com.example.mypodcast.domain.repository.EpisodeRepository
import com.example.mypodcast.domain.repository.LibraryRepository
import com.example.mypodcast.domain.repository.PlayerRepository
import com.example.mypodcast.domain.repository.SavedMomentRepository
import com.example.mypodcast.domain.model.DownloadState
import com.example.mypodcast.domain.usecase.episode.DownloadEpisodeUseCase
import com.example.mypodcast.domain.usecase.library.GetLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000

enum class LibraryTab(val title: String) {
    SUBSCRIPTIONS("Shows"),
    DOWNLOADS("Downloads"),
    MOMENTS("Moments")
}

sealed interface LibrarySearchResult {
    val key: String

    data class Subscription(val podcast: Podcast) : LibrarySearchResult {
        override val key: String = "subscription-${podcast.id}"
    }

    data class Download(val episode: Episode) : LibrarySearchResult {
        override val key: String = "download-${episode.guid}"
    }
}

data class LibraryUiState(
    val subscriptions: List<Podcast> = emptyList(),
    val downloads: List<Episode> = emptyList(),
    val savedMoments: List<SavedMoment> = emptyList(),
    val selectedTab: LibraryTab = LibraryTab.SUBSCRIPTIONS,
    val isRefreshingSubscriptions: Boolean = false,
    val newEpisodeCounts: Map<Long, Int> = emptyMap(),
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<LibrarySearchResult> = emptyList(),
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    val downloadedGuids: Set<String> = emptySet()
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getLibrary: GetLibraryUseCase,
    private val libraryRepository: LibraryRepository,
    private val episodeRepository: EpisodeRepository,
    private val savedMomentRepository: SavedMomentRepository,
    private val downloadEpisodeUseCase: DownloadEpisodeUseCase,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    private val downloadJobs = mutableMapOf<String, Job>()
    private var subscriptionEpisodes: List<Episode> = emptyList()

    init {
        viewModelScope.launch {
            getLibrary.observeSubscriptions().collect { list ->
                _uiState.update { it.copy(subscriptions = list).withSearchResults(subscriptionEpisodes) }
            }
        }
        viewModelScope.launch {
            getLibrary.observeDownloads().collect { list ->
                _uiState.update {
                    it.copy(
                        downloads = list,
                        downloadedGuids = list.map { episode -> episode.guid }.toSet()
                    ).withSearchResults(subscriptionEpisodes)
                }
            }
        }
        viewModelScope.launch {
            savedMomentRepository.observeSavedMoments().collect { moments ->
                _uiState.update { it.copy(savedMoments = moments) }
            }
        }
        @OptIn(ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            getLibrary.observeSubscriptions()
                .flatMapLatest { subscriptions ->
                    val flows = subscriptions.map { episodeRepository.observeEpisodesForPodcast(it.id) }
                    if (flows.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(flows) { episodeLists ->
                            episodeLists.flatMap { it }
                        }
                    }
                }
                .collect { episodes ->
                    subscriptionEpisodes = episodes
                    _uiState.update { state ->
                        if (state.isSearchActive && state.searchQuery.isNotBlank()) {
                            state.withSearchResults(subscriptionEpisodes)
                        } else {
                            state
                        }
                    }
                }
        }
        viewModelScope.launch {
            val threshold = System.currentTimeMillis() - SEVEN_DAYS_MS
            episodeRepository.observeNewEpisodeCounts(threshold).collect { counts ->
                _uiState.update { it.copy(newEpisodeCounts = counts) }
            }
        }
    }

    fun selectTab(tab: LibraryTab) = _uiState.update { it.copy(selectedTab = tab) }

    fun openSearch() = _uiState.update { it.copy(isSearchActive = true) }

    fun closeSearch() = _uiState.update {
        it.copy(
            isSearchActive = false,
            searchQuery = "",
            searchResults = emptyList()
        )
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query).withSearchResults(subscriptionEpisodes) }
    }

    fun refreshSubscriptions() {
        if (_uiState.value.isRefreshingSubscriptions) return

        val subscriptions = _uiState.value.subscriptions
        _uiState.update { it.copy(isRefreshingSubscriptions = true) }
        viewModelScope.launch {
            subscriptions.forEach { podcast ->
                runCatching {
                    episodeRepository.fetchEpisodesForPodcast(
                        podcastId = podcast.id,
                        feedUrl = podcast.feedUrl
                    )
                }
            }
            _uiState.update { it.copy(isRefreshingSubscriptions = false) }
        }
    }

    /**
     * Loads the episode into the player without auto-starting playback.
     * The user kicks off playback from the PlayerScreen.
     */
    fun playEpisode(episode: Episode) = playerRepository.prepare(episode)

    fun playMoment(moment: SavedMoment) {
        playerRepository.play(moment.episode.copy(playbackPosition = moment.positionMs))
    }

    fun deleteDownload(episode: Episode) {
        viewModelScope.launch { libraryRepository.deleteDownload(episode.guid) }
    }

    fun deleteMoment(moment: SavedMoment) {
        viewModelScope.launch { savedMomentRepository.deleteMoment(moment.id) }
    }

    fun downloadEpisode(episode: Episode) {
        if (downloadJobs[episode.guid]?.isActive == true) return
        if (_uiState.value.downloadedGuids.contains(episode.guid)) return

        downloadJobs[episode.guid] = viewModelScope.launch {
            try {
                downloadEpisodeUseCase(episode).collect { state ->
                    _uiState.update { ui ->
                        val map = ui.downloadStates.toMutableMap()
                        if (state is DownloadState.Completed || state is DownloadState.Failed) {
                            map.remove(episode.guid)
                        } else {
                            map[episode.guid] = state
                        }
                        ui.copy(downloadStates = map)
                    }
                }
            } finally {
                _uiState.update { ui ->
                    val map = ui.downloadStates.toMutableMap()
                    map.remove(episode.guid)
                    ui.copy(downloadStates = map)
                }
                downloadJobs.remove(episode.guid)
            }
        }
    }

    fun cancelDownload(episodeGuid: String) {
        downloadJobs[episodeGuid]?.cancel()
    }
}

private fun LibraryUiState.withSearchResults(subscriptionEpisodes: List<Episode>): LibraryUiState {
    val query = searchQuery.trim()
    if (query.isBlank()) return copy(searchResults = emptyList())

    val searchableEpisodes = (subscriptionEpisodes + downloads)
        .associateBy { it.guid }
        .values
        .sortedByDescending { it.publishedAt }
    val matchingSubscriptions = subscriptions
        .filter { it.matches(query) }
        .map(LibrarySearchResult::Subscription)
    val matchingEpisodes = searchableEpisodes
        .filter { it.matches(query) }
        .map(LibrarySearchResult::Download)

    return copy(searchResults = matchingSubscriptions + matchingEpisodes)
}

private fun Podcast.matches(query: String): Boolean =
    listOfNotNull(title, artistName, description, genres.joinToString(" "))
        .any { it.contains(query, ignoreCase = true) }

private fun Episode.matches(query: String): Boolean =
    listOfNotNull(title, description)
        .any { it.contains(query, ignoreCase = true) }
