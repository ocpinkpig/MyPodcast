package com.example.mypodcast.data.repository

import com.example.mypodcast.data.local.dao.EpisodeDao
import com.example.mypodcast.data.local.dao.PodcastDao
import com.example.mypodcast.data.local.dao.SavedMomentDao
import com.example.mypodcast.data.local.entity.EpisodeEntity
import com.example.mypodcast.data.local.entity.SavedMomentEntity
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.SavedMoment
import com.example.mypodcast.domain.repository.SavedMomentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SavedMomentRepositoryImpl @Inject constructor(
    private val savedMomentDao: SavedMomentDao,
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao
) : SavedMomentRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSavedMoments(): Flow<List<SavedMoment>> =
        savedMomentDao.observeAll().flatMapLatest { moments ->
            val episodeGuids = moments.map { it.episodeGuid }.distinct()
            if (episodeGuids.isEmpty()) {
                flowOf(emptyList())
            } else {
                episodeDao.observeByGuids(episodeGuids).flatMapLatest { episodes ->
                    val podcastIds = episodes.map { it.podcastId }.distinct()
                    if (podcastIds.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        podcastDao.observeByIds(podcastIds).map { podcasts ->
                            val artworkByPodcastId = podcasts.associate { it.id to it.artworkUrl }
                            val episodesByGuid = episodes.associateBy { it.guid }
                            moments.mapNotNull { moment ->
                                val episode = episodesByGuid[moment.episodeGuid] ?: return@mapNotNull null
                                moment.toDomain(episode.toDomain(artworkByPodcastId[episode.podcastId]))
                            }
                        }
                    }
                }
            }
        }

    override fun observeHasSavedMoments(episodeGuid: String): Flow<Boolean> =
        savedMomentDao.observeCountForEpisode(episodeGuid).map { it > 0 }

    override suspend fun saveMoment(
        episode: Episode,
        positionMs: Long,
        clipStartMs: Long,
        clipEndMs: Long,
        transcriptText: String?
    ) {
        savedMomentDao.insert(
            SavedMomentEntity(
                episodeGuid = episode.guid,
                positionMs = positionMs,
                clipStartMs = clipStartMs,
                clipEndMs = clipEndMs,
                transcriptText = transcriptText,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteMoment(id: Long) = savedMomentDao.deleteById(id)

    private fun SavedMomentEntity.toDomain(episode: Episode) = SavedMoment(
        id = id,
        episode = episode,
        positionMs = positionMs,
        clipStartMs = clipStartMs,
        clipEndMs = clipEndMs,
        transcriptText = transcriptText,
        createdAt = createdAt
    )

    private fun EpisodeEntity.toDomain(podcastArtworkFallback: String?) = Episode(
        guid = guid,
        podcastId = podcastId,
        title = title,
        description = description,
        audioUrl = audioUrl,
        artworkUrl = artworkUrl?.takeIf { it.isNotBlank() } ?: podcastArtworkFallback,
        publishedAt = publishedAt,
        durationSeconds = durationSeconds,
        fileSizeBytes = fileSizeBytes,
        playbackPosition = playbackPosition,
        isPlayed = isPlayed,
        isFavorite = isFavorite,
        transcriptUrl = transcriptUrl,
        transcriptType = transcriptType
    )
}
