package com.example.mypodcast.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_episodes")
data class DownloadedEpisodeEntity(
    @PrimaryKey val episodeGuid: String,
    val podcastId: Long,
    val localFilePath: String,
    val downloadedAt: Long = System.currentTimeMillis(),
    val fileSizeBytes: Long
)
