package com.example.mypodcast.domain.model

data class SavedMoment(
    val id: Long = 0L,
    val episode: Episode,
    val positionMs: Long,
    val clipStartMs: Long,
    val clipEndMs: Long,
    val transcriptText: String?,
    val createdAt: Long
)
