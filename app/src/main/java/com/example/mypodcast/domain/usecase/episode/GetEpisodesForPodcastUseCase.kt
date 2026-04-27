package com.example.mypodcast.domain.usecase.episode

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.repository.EpisodeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetEpisodesForPodcastUseCase @Inject constructor(
    private val repository: EpisodeRepository
) {
    suspend fun fetch(podcastId: Long, feedUrl: String): List<Episode> =
        repository.fetchEpisodesForPodcast(podcastId, feedUrl)

    fun observe(podcastId: Long): Flow<List<Episode>> =
        repository.observeEpisodesForPodcast(podcastId)
}
