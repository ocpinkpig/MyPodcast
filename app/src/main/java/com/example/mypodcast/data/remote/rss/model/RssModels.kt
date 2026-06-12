package com.example.mypodcast.data.remote.rss.model

data class RssFeed(
    val title: String,
    val imageUrl: String?,
    val description: String?,
    /** Channel-level `<language>` tag, e.g. "en-US" or "zh-CN". */
    val language: String? = null,
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
    val fileSizeBytes: Long,
    val transcriptUrl: String? = null,
    val transcriptType: String? = null
)
