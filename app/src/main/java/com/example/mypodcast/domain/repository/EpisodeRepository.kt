package com.example.mypodcast.domain.repository

import com.example.mypodcast.domain.model.Episode
import kotlinx.coroutines.flow.Flow

interface EpisodeRepository {
    suspend fun fetchEpisodesForPodcast(podcastId: Long, feedUrl: String): List<Episode>
    fun observeEpisodesForPodcast(podcastId: Long): Flow<List<Episode>>
    suspend fun getEpisode(guid: String): Episode?
    suspend fun updateProgress(guid: String, positionMs: Long, isPlayed: Boolean)
    suspend fun updateFavorite(guid: String, isFavorite: Boolean)
}
