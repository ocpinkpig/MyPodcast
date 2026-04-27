package com.example.mypodcast.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.mypodcast.data.local.dao.DownloadedEpisodeDao
import com.example.mypodcast.data.local.dao.EpisodeDao
import com.example.mypodcast.data.local.dao.PodcastDao
import com.example.mypodcast.data.local.dao.SubscriptionDao
import com.example.mypodcast.data.local.entity.DownloadedEpisodeEntity
import com.example.mypodcast.data.local.entity.EpisodeEntity
import com.example.mypodcast.data.local.entity.PodcastEntity
import com.example.mypodcast.data.local.entity.SubscriptionEntity

@Database(
    entities = [
        PodcastEntity::class,
        EpisodeEntity::class,
        SubscriptionEntity::class,
        DownloadedEpisodeEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun downloadedEpisodeDao(): DownloadedEpisodeDao
}
