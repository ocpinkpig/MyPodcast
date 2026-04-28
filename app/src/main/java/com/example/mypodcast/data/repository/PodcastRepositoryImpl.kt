package com.example.mypodcast.data.repository

import com.example.mypodcast.data.local.dao.PodcastDao
import com.example.mypodcast.data.local.entity.PodcastEntity
import com.example.mypodcast.data.remote.api.PodcastApiService
import com.example.mypodcast.data.remote.api.dto.ItunesResult
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.repository.PodcastRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PodcastRepositoryImpl @Inject constructor(
    private val api: PodcastApiService,
    private val podcastDao: PodcastDao
) : PodcastRepository {

    override suspend fun search(query: String): List<Podcast> {
        val response = api.search(term = query)
        val entities = response.results.mapNotNull { it.toEntityOrNull() }
        podcastDao.upsertAll(entities)
        return entities.map { it.toDomain() }
    }

    override suspend fun getFeatured(category: String): List<Podcast> {
        val response = api.searchByCategory(category = category)
        val entities = response.results.mapNotNull { it.toEntityOrNull() }
        podcastDao.upsertAll(entities)
        return entities.map { it.toDomain() }
    }

    override suspend fun getDetail(podcastId: Long): Podcast {
        val response = runCatching { api.lookup(id = podcastId) }.getOrNull()
        val fromApi = response?.results?.firstOrNull()?.toEntityOrNull()
        val existing = podcastDao.getById(podcastId)
        val entity = when {
            fromApi != null && existing != null -> fromApi.copy(
                // Preserve description backfilled from the RSS feed — iTunes
                // Search API doesn't return one for podcast lookups, so without
                // this we'd overwrite the parsed channel description with null.
                description = fromApi.description?.takeIf { it.isNotBlank() }
                    ?: existing.description
            )
            fromApi != null -> fromApi
            existing != null -> existing
            else -> error("Podcast $podcastId not found")
        }
        podcastDao.upsert(entity)
        return entity.toDomain()
    }

    override fun observePodcast(podcastId: Long): Flow<Podcast?> =
        podcastDao.observeByIds(listOf(podcastId)).map { it.firstOrNull()?.toDomain() }

    private fun ItunesResult.toEntityOrNull(): PodcastEntity? {
        val feed = feedUrl?.takeIf { it.isNotBlank() } ?: return null
        return PodcastEntity(
            id = trackId,
            title = trackName,
            artworkUrl = artworkUrl,
            artistName = artistName,
            feedUrl = feed,
            description = description,
            genres = genres.joinToString(","),
            episodeCount = trackCount
        )
    }

    private fun PodcastEntity.toDomain() = Podcast(
        id = id,
        title = title,
        artworkUrl = artworkUrl,
        artistName = artistName,
        feedUrl = feedUrl,
        description = description,
        genres = genres.split(",").filter { it.isNotBlank() },
        episodeCount = episodeCount
    )
}
