package com.example.mypodcast.ui.main

import androidx.lifecycle.ViewModel
import com.example.mypodcast.domain.model.PlayerState
import com.example.mypodcast.domain.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainScreenViewModel @Inject constructor(
    playerRepository: PlayerRepository
) : ViewModel() {
    val playerState: StateFlow<PlayerState> = playerRepository.playerState
}
