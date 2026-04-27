package com.example.mypodcast.domain.usecase.podcast

import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.repository.PodcastRepository
import javax.inject.Inject

class GetFeaturedPodcastsUseCase @Inject constructor(
    private val repository: PodcastRepository
) {
    val categories = listOf("Technology", "Comedy", "News", "True Crime", "Business", "Health")

    suspend operator fun invoke(): Map<String, List<Podcast>> {
        return categories.associateWith { category ->
            runCatching { repository.getFeatured(category) }.getOrDefault(emptyList())
        }
    }
}
