package com.example.mypodcast.di

import com.example.mypodcast.work.RestoreScheduler
import com.example.mypodcast.work.WorkManagerRestoreScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {

    @Binds
    @Singleton
    abstract fun bindRestoreScheduler(impl: WorkManagerRestoreScheduler): RestoreScheduler
}
