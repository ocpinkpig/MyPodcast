package com.example.mypodcast.domain.usecase.podcast

import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.repository.PodcastRepository
import javax.inject.Inject

class GetPodcastDetailUseCase @Inject constructor(
    private val repository: PodcastRepository
) {
    suspend operator fun invoke(podcastId: Long): Podcast = repository.getDetail(podcastId)
}
