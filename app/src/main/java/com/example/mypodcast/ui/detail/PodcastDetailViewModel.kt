package com.example.mypodcast.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.repository.PlayerRepository
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PodcastDetailUiState(
    val podcast: Podcast? = null,
    val episodes: List<Episode> = emptyList(),
    val isSubscribed: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class PodcastDetailViewModel @Inject constructor(
    private val getPodcastDetail: GetPodcastDetailUseCase,
    private val getEpisodes: GetEpisodesForPodcastUseCase,
    private val subscribeUseCase: SubscribeToPodcastUseCase,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastDetailUiState())
    val uiState: StateFlow<PodcastDetailUiState> = _uiState.asStateFlow()

    private val podcastIdFlow = MutableStateFlow<Long?>(null)
    private var fetchJob: Job? = null
    private var loadedPodcastId: Long? = null

    init {
        @OptIn(ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            podcastIdFlow
                .flatMapLatest { id ->
                    if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
                    else getEpisodes.observe(id)
                }
                .collect { episodes ->
                    _uiState.update { it.copy(episodes = episodes) }
                }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            podcastIdFlow
                .flatMapLatest { id ->
                    if (id == null) kotlinx.coroutines.flow.flowOf(false)
                    else subscribeUseCase.observeIsSubscribed(id)
                }
                .collect { subscribed ->
                    _uiState.update { it.copy(isSubscribed = subscribed) }
                }
        }
    }

    fun load(podcastId: Long) {
        if (loadedPodcastId == podcastId && fetchJob?.isActive != true) return
        loadedPodcastId = podcastId

        _uiState.value = PodcastDetailUiState(isLoading = true)
        podcastIdFlow.value = podcastId

        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            runCatching {
                val podcast = getPodcastDetail(podcastId)
                _uiState.update { it.copy(podcast = podcast) }
                getEpisodes.fetch(podcastId, podcast.feedUrl)
                _uiState.update { it.copy(isLoading = false) }
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

    fun playEpisode(episode: Episode) = playerRepository.play(episode)
}
