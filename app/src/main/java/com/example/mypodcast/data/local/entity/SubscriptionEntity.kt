package com.example.mypodcast.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val podcastId: Long,
    val subscribedAt: Long = System.currentTimeMillis()
)
