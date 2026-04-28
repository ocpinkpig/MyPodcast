package com.example.mypodcast.data.remote.rss.model

data class RssFeed(
    val title: String,
    val imageUrl: String?,
    val description: String?,
    val episodes: List<RssEpisode>
)

data class RssEpisode(
    val guid: String,
    val title: String,
    val description: String?,
    val audioUrl: String,
    val artworkUrl: String?,
    val publishedAt: Long,
    val durationSeconds: Int,
    val fileSizeBytes: Long
)
