package com.example.mypodcast.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_moments",
    foreignKeys = [ForeignKey(
        entity = EpisodeEntity::class,
        parentColumns = ["guid"],
        childColumns = ["episodeGuid"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("episodeGuid"),
        Index("createdAt")
    ]
)
data class SavedMomentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val episodeGuid: String,
    val positionMs: Long,
    val clipStartMs: Long,
    val clipEndMs: Long,
    val transcriptText: String?,
    val createdAt: Long
)
