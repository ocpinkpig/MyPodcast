package com.example.mypodcast.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "queue_items",
    indices = [Index(value = ["position"], unique = true)]
)
data class QueueItemEntity(
    @PrimaryKey val episodeGuid: String,
    val position: Int
)
