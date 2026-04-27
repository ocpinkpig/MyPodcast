package com.example.mypodcast.domain.usecase.library

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetLibraryUseCase @Inject constructor(
    private val repository: LibraryRepository
) {
    fun observeSubscriptions(): Flow<List<Podcast>> = repository.observeSubscriptions()
    fun observeDownloads(): Flow<List<Episode>> = repository.observeDownloadedEpisodes()
}
