package com.example.mypodcast.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.repository.EpisodeRepository
import com.example.mypodcast.domain.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class QueueTab { QUEUE, FAVORITES, HISTORY }

data class QueueUiState(
    val selectedTab: QueueTab = QueueTab.QUEUE,
    val playerState: PlayerState = PlayerState(),
    val favorites: List<Episode> = emptyList(),
    val history: List<Episode> = emptyList()
)

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    episodeRepository: EpisodeRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(QueueUiState())
    val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                playerRepository.playerState,
                episodeRepository.observeFavorites(),
                episodeRepository.observePlayedHistory()
            ) { player, favorites, history ->
                Triple(player, favorites, history)
            }.collect { (player, favorites, history) ->
                _uiState.value = _uiState.value.copy(playerState = player, favorites = favorites, history = history)
            }
        }
    }

    fun selectTab(tab: QueueTab) { _uiState.value = _uiState.value.copy(selectedTab = tab) }
    fun removeFromQueue(guid: String) = playerRepository.removeFromQueue(guid)
    fun clearQueue() = playerRepository.clearQueue()
    fun skipToQueueItem(guid: String) = playerRepository.skipToQueueItem(guid)
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = playerRepository.moveQueueItem(fromIndex, toIndex)
}
