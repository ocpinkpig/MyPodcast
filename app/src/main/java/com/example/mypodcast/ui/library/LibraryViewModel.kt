package com.example.mypodcast.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.repository.EpisodeRepository
import com.example.mypodcast.domain.repository.LibraryRepository
import com.example.mypodcast.domain.repository.PlayerRepository
import com.example.mypodcast.domain.usecase.library.GetLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab { SUBSCRIPTIONS, DOWNLOADS }

data class LibraryUiState(
    val subscriptions: List<Podcast> = emptyList(),
    val downloads: List<Episode> = emptyList(),
    val selectedTab: LibraryTab = LibraryTab.SUBSCRIPTIONS,
    val isRefreshingSubscriptions: Boolean = false,
    val newEpisodeCounts: Map<Long, Int> = emptyMap()
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getLibrary: GetLibraryUseCase,
    private val libraryRepository: LibraryRepository,
    private val episodeRepository: EpisodeRepository,
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

    fun refreshSubscriptions() {
        if (_uiState.value.isRefreshingSubscriptions) return

        val subscriptions = _uiState.value.subscriptions
        _uiState.update { it.copy(isRefreshingSubscriptions = true) }
        viewModelScope.launch {
            val newCounts = subscriptions.mapNotNull { podcast ->
                runCatching {
                    val existingGuids = episodeRepository
                        .observeEpisodesForPodcast(podcast.id)
                        .first()
                        .map { it.guid }
                        .toSet()
                    val refreshed = episodeRepository.fetchEpisodesForPodcast(
                        podcastId = podcast.id,
                        feedUrl = podcast.feedUrl
                    )
                    val newCount = refreshed.count { it.guid !in existingGuids }
                    if (newCount > 0) podcast.id to newCount else null
                }.getOrNull()
            }.toMap()

            _uiState.update {
                it.copy(
                    isRefreshingSubscriptions = false,
                    newEpisodeCounts = newCounts
                )
            }
        }
    }

    /**
     * Loads the episode into the player without auto-starting playback.
     * The user kicks off playback from the PlayerScreen.
     */
    fun playEpisode(episode: Episode) = playerRepository.prepare(episode)
    fun enqueueEpisode(episode: Episode) = playerRepository.enqueue(episode)
    fun enqueueNextEpisode(episode: Episode) = playerRepository.enqueueNext(episode)

    fun deleteDownload(episode: Episode) {
        viewModelScope.launch { libraryRepository.deleteDownload(episode.guid) }
    }
}
