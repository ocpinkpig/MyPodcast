package com.example.mypodcast.domain.repository

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Podcast
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
}
