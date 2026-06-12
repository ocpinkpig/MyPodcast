package com.example.mypodcast.di

import android.content.Context
import androidx.room.Room
import com.example.mypodcast.data.local.AppDatabase
import com.example.mypodcast.data.local.MIGRATION_1_2
import com.example.mypodcast.data.local.MIGRATION_2_3
import com.example.mypodcast.data.local.MIGRATION_3_4
import com.example.mypodcast.data.local.MIGRATION_4_5
import com.example.mypodcast.data.local.MIGRATION_5_6
import com.example.mypodcast.data.local.MIGRATION_6_7
import com.example.mypodcast.data.local.MIGRATION_7_8
import com.example.mypodcast.data.local.MIGRATION_8_9
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "mypodcast.db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9
            )
            .build()

    @Provides
    fun providePodcastDao(db: AppDatabase) = db.podcastDao()

    @Provides
    fun provideEpisodeDao(db: AppDatabase) = db.episodeDao()

    @Provides
    fun provideSubscriptionDao(db: AppDatabase) = db.subscriptionDao()

    @Provides
    fun provideDownloadedEpisodeDao(db: AppDatabase) = db.downloadedEpisodeDao()

    @Provides
    fun provideQueueDao(db: AppDatabase) = db.queueDao()

    @Provides
    fun provideSavedMomentDao(db: AppDatabase) = db.savedMomentDao()
}
