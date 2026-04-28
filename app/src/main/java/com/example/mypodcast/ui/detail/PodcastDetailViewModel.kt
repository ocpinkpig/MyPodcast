package com.example.mypodcast.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypodcast.domain.model.DownloadState
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.repository.LibraryRepository
import com.example.mypodcast.domain.repository.PlayerRepository
import com.example.mypodcast.domain.usecase.episode.DownloadEpisodeUseCase
import com.example.mypodcast.domain.usecase.episode.GetEpisodesForPodcastUseCase
import com.example.mypodcast.domain.usecase.library.SubscribeToPodcastUseCase
import com.example.mypodcast.domain.usecase.podcast.GetPodcastDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PodcastDetailUiState(
    val podcast: Podcast? = null,
    val episodes: List<Episode> = emptyList(),
    val isSubscribed: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    val downloadedGuids: Set<String> = emptySet()
)

@HiltViewModel
class PodcastDetailViewModel @Inject constructor(
    private val getPodcastDetail: GetPodcastDetailUseCase,
    private val getEpisodes: GetEpisodesForPodcastUseCase,
    private val subscribeUseCase: SubscribeToPodcastUseCase,
    private val downloadEpisodeUseCase: DownloadEpisodeUseCase,
    private val libraryRepository: LibraryRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastDetailUiState())
    val uiState: StateFlow<PodcastDetailUiState> = _uiState.asStateFlow()

    private val podcastIdFlow = MutableStateFlow<Long?>(null)
    private var fetchJob: Job? = null
    private val downloadJobs = mutableMapOf<String, Job>()
    private var loadedPodcastId: Long? = null

    init {
        @OptIn(ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            podcastIdFlow
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList()) else getEpisodes.observe(id)
                }
                .collect { episodes -> _uiState.update { it.copy(episodes = episodes) } }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            podcastIdFlow
                .flatMapLatest { id ->
                    if (id == null) flowOf(false) else subscribeUseCase.observeIsSubscribed(id)
                }
                .collect { subscribed -> _uiState.update { it.copy(isSubscribed = subscribed) } }
        }

        viewModelScope.launch {
            libraryRepository.observeDownloadedEpisodes().collect { downloads ->
                _uiState.update { it.copy(downloadedGuids = downloads.map { e -> e.guid }.toSet()) }
            }
        }
    }

    fun load(podcastId: Long) {
        if (loadedPodcastId == podcastId && fetchJob?.isActive != true) return
        loadedPodcastId = podcastId

        _uiState.update {
            PodcastDetailUiState(
                isLoading = true,
                downloadedGuids = it.downloadedGuids
            )
        }
        podcastIdFlow.value = podcastId

        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            runCatching {
                val podcast = getPodcastDetail(podcastId)
                _uiState.update { it.copy(podcast = podcast) }
                getEpisodes.fetch(podcastId, podcast.feedUrl)
                // Re-load — fetch may have backfilled the description from the RSS feed.
                val refreshed = getPodcastDetail(podcastId)
                _uiState.update { it.copy(isLoading = false, podcast = refreshed) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun toggleSubscription(podcastId: Long) {
        viewModelScope.launch {
            if (_uiState.value.isSubscribed) subscribeUseCase.unsubscribe(podcastId)
            else subscribeUseCase.subscribe(podcastId)
        }
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

    fun deleteDownload(episode: Episode) {
        viewModelScope.launch {
            libraryRepository.deleteDownload(episode.guid)
        }
    }

    fun playEpisode(episode: Episode) = playerRepository.play(episode)
}
