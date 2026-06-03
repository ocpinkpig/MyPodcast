package com.example.mypodcast.domain.repository

import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.SavedMoment
import kotlinx.coroutines.flow.Flow

interface SavedMomentRepository {
    fun observeSavedMoments(): Flow<List<SavedMoment>>
    fun observeHasSavedMoments(episodeGuid: String): Flow<Boolean>
    suspend fun saveMoment(
        episode: Episode,
        positionMs: Long,
        clipStartMs: Long,
        clipEndMs: Long,
        transcriptText: String?
    )
    suspend fun deleteMoment(id: Long)
}
