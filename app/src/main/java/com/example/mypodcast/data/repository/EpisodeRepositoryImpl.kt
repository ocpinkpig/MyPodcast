package com.example.mypodcast.data.repository

import com.example.mypodcast.data.local.dao.EpisodeDao
import com.example.mypodcast.data.local.entity.EpisodeEntity
import com.example.mypodcast.data.remote.rss.RssParser
import com.example.mypodcast.data.remote.rss.model.RssEpisode
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.repository.EpisodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class EpisodeRepositoryImpl @Inject constructor(
    private val rssParser: RssParser,
    private val episodeDao: EpisodeDao
) : EpisodeRepository {

    override suspend fun fetchEpisodesForPodcast(podcastId: Long, feedUrl: String): List<Episode> {
        val feed = rssParser.parse(feedUrl)
        val entities = feed.episodes.map { it.toEntity(podcastId) }
        episodeDao.upsertAll(entities)
        return entities.map { it.toDomain() }
    }

    override fun observeEpisodesForPodcast(podcastId: Long): Flow<List<Episode>> =
        episodeDao.observeByPodcast(podcastId).map { list -> list.map { it.toDomain() } }

    override suspend fun getEpisode(guid: String): Episode? =
        episodeDao.getByGuid(guid)?.toDomain()

    override suspend fun updatePlaybackPosition(guid: String, positionMs: Long) =
        episodeDao.updatePosition(guid, positionMs)

    private fun RssEpisode.toEntity(podcastId: Long) = EpisodeEntity(
        guid = guid,
        podcastId = podcastId,
        title = title,
        description = description,
        audioUrl = audioUrl,
        artworkUrl = artworkUrl,
        publishedAt = publishedAt,
        durationSeconds = durationSeconds,
        fileSizeBytes = fileSizeBytes
    )

    private fun EpisodeEntity.toDomain() = Episode(
        guid = guid,
        podcastId = podcastId,
        title = title,
        description = description,
        audioUrl = audioUrl,
        artworkUrl = artworkUrl,
        publishedAt = publishedAt,
        durationSeconds = durationSeconds,
        fileSizeBytes = fileSizeBytes,
        playbackPosition = playbackPosition
    )
}
