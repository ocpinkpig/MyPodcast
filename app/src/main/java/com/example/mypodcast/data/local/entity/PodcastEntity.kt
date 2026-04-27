package com.example.mypodcast.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artworkUrl: String,
    val artistName: String,
    val feedUrl: String,
    val description: String?,
    val genres: String,
    val episodeCount: Int,
    val cachedAt: Long = System.currentTimeMillis()
)
