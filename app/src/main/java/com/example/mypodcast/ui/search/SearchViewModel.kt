package com.example.mypodcast.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.usecase.podcast.SearchPodcastsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Podcast> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchPodcasts: SearchPodcastsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .filter { it.isNotBlank() }
                .collect { query -> search(query) }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, error = null) }
        queryFlow.value = query
        if (query.isBlank()) _uiState.update { it.copy(results = emptyList(), isSearching = false) }
    }

    private fun search(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            runCatching { searchPodcasts(query) }
                .onSuccess { results -> _uiState.update { it.copy(isSearching = false, results = results) } }
                .onFailure { err -> _uiState.update { it.copy(isSearching = false, error = err.message) } }
        }
    }
}
