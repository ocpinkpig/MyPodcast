package com.example.mypodcast.domain.repository

import com.example.mypodcast.domain.model.Podcast
import kotlinx.coroutines.flow.Flow

interface PodcastRepository {
    suspend fun search(query: String): List<Podcast>
    suspend fun getFeatured(category: String): List<Podcast>
    suspend fun getDetail(podcastId: Long): Podcast
    fun observePodcast(podcastId: Long): Flow<Podcast?>
}
