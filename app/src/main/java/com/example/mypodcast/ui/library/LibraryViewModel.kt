package com.example.mypodcast.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.repository.LibraryRepository
import com.example.mypodcast.domain.repository.PlayerRepository
import com.example.mypodcast.domain.usecase.library.GetLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab { SUBSCRIPTIONS, DOWNLOADS }

data class LibraryUiState(
    val subscriptions: List<Podcast> = emptyList(),
    val downloads: List<Episode> = emptyList(),
    val selectedTab: LibraryTab = LibraryTab.SUBSCRIPTIONS
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getLibrary: GetLibraryUseCase,
    private val libraryRepository: LibraryRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getLibrary.observeSubscriptions().collect { list ->
                _uiState.update { it.copy(subscriptions = list) }
            }
        }
        viewModelScope.launch {
            getLibrary.observeDownloads().collect { list ->
                _uiState.update { it.copy(downloads = list) }
            }
        }
    }

    fun selectTab(tab: LibraryTab) = _uiState.update { it.copy(selectedTab = tab) }

    fun playEpisode(episode: Episode) = playerRepository.play(episode)

    fun deleteDownload(episode: Episode) {
        viewModelScope.launch { libraryRepository.deleteDownload(episode.guid) }
    }
}
