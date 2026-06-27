package com.example.mypodcast.domain.usecase.podcast

import com.example.mypodcast.domain.model.TopShow
import com.example.mypodcast.domain.repository.EpisodeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTopShowsUseCase @Inject constructor(
    private val repository: EpisodeRepository
) {
    operator fun invoke(limit: Int = DEFAULT_LIMIT): Flow<List<TopShow>> =
        repository.observeTopShows(limit)

    companion object {
        const val DEFAULT_LIMIT = 10
    }
}
