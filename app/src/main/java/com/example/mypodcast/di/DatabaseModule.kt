package com.example.mypodcast.di

import android.content.Context
import androidx.room.Room
import com.example.mypodcast.data.local.AppDatabase
import com.example.mypodcast.data.local.MIGRATION_1_2
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
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun providePodcastDao(db: AppDatabase) = db.podcastDao()

    @Provides
    fun provideEpisodeDao(db: AppDatabase) = db.episodeDao()

    @Provides
    fun provideSubscriptionDao(db: AppDatabase) = db.subscriptionDao()

    @Provides
    fun provideDownloadedEpisodeDao(db: AppDatabase) = db.downloadedEpisodeDao()
}
