package com.example.mypodcast.domain.usecase.podcast

import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.repository.PodcastRepository
import javax.inject.Inject

class SearchPodcastsUseCase @Inject constructor(
    private val repository: PodcastRepository
) {
    suspend operator fun invoke(query: String): List<Podcast> = repository.search(query)
}
