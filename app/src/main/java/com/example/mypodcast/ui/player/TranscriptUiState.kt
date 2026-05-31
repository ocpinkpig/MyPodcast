package com.example.mypodcast.ui.player

import com.example.mypodcast.domain.model.Transcript

/** UI state for the Player's transcript page. */
sealed interface TranscriptUiState {
    data object Loading : TranscriptUiState
    data object Empty : TranscriptUiState
    data class Loaded(val transcript: Transcript) : TranscriptUiState
    data class Error(val message: String) : TranscriptUiState
}
