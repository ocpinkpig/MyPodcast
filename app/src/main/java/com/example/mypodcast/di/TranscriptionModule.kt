package com.example.mypodcast.di

import android.content.Context
import com.example.mypodcast.data.transcription.GeneratedTranscriptStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TranscriptionModule {

    @Provides
    @Singleton
    fun provideGeneratedTranscriptStore(@ApplicationContext context: Context): GeneratedTranscriptStore =
        GeneratedTranscriptStore(context.filesDir)
}
