package com.example.mypodcast.di

import android.content.Context
import com.example.mypodcast.data.transcription.GeneratedTranscriptStore
import com.example.mypodcast.data.transcription.MlKitSpeechEngine
import com.example.mypodcast.data.transcription.Mp3PcmStreamer
import com.example.mypodcast.data.transcription.PcmSourceFactory
import com.example.mypodcast.data.transcription.SpeechTranscriptionEngine
import com.example.mypodcast.data.transcription.TranscriptionSessionManager
import com.example.mypodcast.domain.transcription.TranscriptionMonitor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** Directory under which transcript caches and generated transcripts live. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TranscriptFilesDir

@Module
@InstallIn(SingletonComponent::class)
abstract class TranscriptionModule {

    @Binds
    @Singleton
    abstract fun bindSpeechEngine(impl: MlKitSpeechEngine): SpeechTranscriptionEngine

    @Binds
    @Singleton
    abstract fun bindPcmSourceFactory(impl: Mp3PcmStreamer.Factory): PcmSourceFactory

    @Binds
    abstract fun bindTranscriptionMonitor(impl: TranscriptionSessionManager): TranscriptionMonitor

    companion object {
        @Provides
        @Singleton
        fun provideGeneratedTranscriptStore(@ApplicationContext context: Context): GeneratedTranscriptStore =
            GeneratedTranscriptStore(context.filesDir)

        @Provides
        @Singleton
        @TranscriptFilesDir
        fun provideTranscriptFilesDir(@ApplicationContext context: Context): java.io.File =
            context.filesDir
    }
}
