package com.example.mypodcast.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "episodes",
    foreignKeys = [ForeignKey(
        entity = PodcastEntity::class,
        parentColumns = ["id"],
        childColumns = ["podcastId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("podcastId")]
)
data class EpisodeEntity(
    @PrimaryKey val guid: String,
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
    val isFavorite: Boolean = false,
    val lastPlayedAt: Long = 0L
)
