package com.example.mypodcast.data.repository

import com.example.mypodcast.data.local.dao.EpisodeDao
import com.example.mypodcast.data.local.dao.PodcastDao
import com.example.mypodcast.data.local.entity.EpisodeEntity
import com.example.mypodcast.data.remote.rss.RssParser
import com.example.mypodcast.data.remote.rss.model.RssEpisode
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.repository.EpisodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class EpisodeRepositoryImpl @Inject constructor(
    private val rssParser: RssParser,
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao
) : EpisodeRepository {

    override suspend fun fetchEpisodesForPodcast(podcastId: Long, feedUrl: String): List<Episode> {
        val feed = rssParser.parse(feedUrl)
        val parsed = feed.episodes.map { it.toEntity(podcastId) }

        // Preserve any saved progress before upsert — RSS doesn't carry it.
        val priorByGuid = episodeDao.getByGuids(parsed.map { it.guid }).associateBy { it.guid }
        val merged = parsed.map { e ->
            val prior = priorByGuid[e.guid]
            if (prior == null) e
            else e.copy(playbackPosition = prior.playbackPosition, isPlayed = prior.isPlayed)
        }
        episodeDao.upsertAll(merged)

        // Backfill the podcast's description from the RSS feed channel-level
        // <description> / <itunes:summary>. iTunes Search doesn't return one,
        // so this is the only way to populate it.
        val feedDescription = feed.description?.takeIf { it.isNotBlank() }
        val existingPodcast = podcastDao.getById(podcastId)
        if (feedDescription != null && existingPodcast != null && existingPodcast.description.isNullOrBlank()) {
            podcastDao.upsert(existingPodcast.copy(description = feedDescription))
        }

        val podcastArtwork = existingPodcast?.artworkUrl
        return merged.map { it.toDomain(podcastArtwork) }
    }

    override fun observeEpisodesForPodcast(podcastId: Long): Flow<List<Episode>> {
        val podcastArtworkFlow = podcastDao.observeByIds(listOf(podcastId))
            .map { it.firstOrNull()?.artworkUrl }
        return episodeDao.observeByPodcast(podcastId).combine(podcastArtworkFlow) { entities, artwork ->
            entities.map { it.toDomain(artwork) }
        }
    }

    override suspend fun getEpisode(guid: String): Episode? {
        val entity = episodeDao.getByGuid(guid) ?: return null
        val podcastArtwork = podcastDao.getById(entity.podcastId)?.artworkUrl
        return entity.toDomain(podcastArtwork)
    }

    override suspend fun updateProgress(guid: String, positionMs: Long, isPlayed: Boolean) =
        episodeDao.updateProgress(guid, positionMs, isPlayed)

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
        isPlayed = isPlayed
    )
}
