package com.example.mypodcast.domain.model

data class PlayerState(
    val episode: Episode? = null,
    val previewEpisode: Episode? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val sleepTimerRemainingMs: Long = 0L,
    val isBuffering: Boolean = false,
    val error: String? = null
)
