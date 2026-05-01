package com.example.mypodcast.domain.model

data class Episode(
    val guid: String,
    val podcastId: Long,
    val title: String,
    val description: String?,
    val audioUrl: String,
    val artworkUrl: String?,
    val publishedAt: Long,
    val durationSeconds: Int,
    val fileSizeBytes: Long,
    val playbackPosition: Long = 0L,
    val isPlayed: Boolean = false,
    val isFavorite: Boolean = false
)
