package com.example.mypodcast.domain.usecase.library

import com.example.mypodcast.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SubscribeToPodcastUseCase @Inject constructor(
    private val repository: LibraryRepository
) {
    suspend fun subscribe(podcastId: Long) = repository.subscribe(podcastId)
    suspend fun unsubscribe(podcastId: Long) = repository.unsubscribe(podcastId)
    fun observeIsSubscribed(podcastId: Long): Flow<Boolean> = repository.observeIsSubscribed(podcastId)
}
