package com.example.mypodcast.domain.model

data class TopShow(
    val podcast: Podcast,
    val playedEpisodeCount: Int,
    val latestPlayedAt: Long
)
