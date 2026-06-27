package com.example.mypodcast.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.model.TopShow
import com.example.mypodcast.domain.usecase.podcast.GetFeaturedPodcastsUseCase
import com.example.mypodcast.domain.usecase.podcast.ObserveTopShowsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val topShows: List<TopShow> = emptyList(),
    val featuredByCategory: Map<String, List<Podcast>> = emptyMap(),
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getFeaturedPodcasts: GetFeaturedPodcastsUseCase,
    private val observeTopShows: ObserveTopShowsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        collectTopShows()
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { getFeaturedPodcasts() }
                .onSuccess { map -> _uiState.update { it.copy(isLoading = false, featuredByCategory = map) } }
                .onFailure { err -> _uiState.update { it.copy(isLoading = false, error = err.message) } }
        }
    }

    private fun collectTopShows() {
        viewModelScope.launch {
            runCatching {
                observeTopShows().collect { topShows ->
                    _uiState.update { it.copy(topShows = topShows) }
                }
            }.onFailure {
                _uiState.update { it.copy(topShows = emptyList()) }
            }
        }
    }
}
