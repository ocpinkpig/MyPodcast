package com.example.mypodcast.domain.model

data class Podcast(
    val id: Long,
    val title: String,
    val artworkUrl: String,
    val artistName: String,
    val feedUrl: String,
    val description: String?,
    val genres: List<String>,
    val episodeCount: Int
)
