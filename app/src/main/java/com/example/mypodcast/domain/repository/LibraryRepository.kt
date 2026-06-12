package com.example.mypodcast.domain.repository

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.model.TranscriptStatus
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    suspend fun subscribe(podcastId: Long)
    suspend fun unsubscribe(podcastId: Long)
    fun observeSubscriptions(): Flow<List<Podcast>>
    fun observeIsSubscribed(podcastId: Long): Flow<Boolean>
    fun observeDownloadedEpisodes(): Flow<List<Episode>>
    fun observeIsDownloaded(episodeGuid: String): Flow<Boolean>
    suspend fun saveDownload(episodeGuid: String, podcastId: Long, localPath: String, sizeBytes: Long)
    suspend fun deleteDownload(episodeGuid: String)
    suspend fun cleanupOrphanedFiles()

    /** Absolute path of the downloaded audio file, or null if not downloaded. */
    suspend fun getDownloadedFilePath(episodeGuid: String): String?

    /** RSS `<language>` of the podcast (e.g. "en-US", "zh-CN"), or null if unknown. */
    suspend fun getPodcastLanguage(podcastId: Long): String?

    /** On-device transcript generation status per downloaded episode guid. */
    fun observeTranscriptStatuses(): Flow<Map<String, TranscriptStatus>>

    suspend fun setTranscriptStatus(episodeGuid: String, status: TranscriptStatus)
}
