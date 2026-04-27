@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.mypodcast.data.repository

import com.example.mypodcast.data.local.dao.DownloadedEpisodeDao
import com.example.mypodcast.data.local.dao.EpisodeDao
import com.example.mypodcast.data.local.dao.PodcastDao
import com.example.mypodcast.data.local.dao.SubscriptionDao
import com.example.mypodcast.data.local.entity.DownloadedEpisodeEntity
import com.example.mypodcast.data.local.entity.SubscriptionEntity
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LibraryRepositoryImpl @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val downloadedEpisodeDao: DownloadedEpisodeDao
) : LibraryRepository {

    override suspend fun subscribe(podcastId: Long) =
        subscriptionDao.subscribe(SubscriptionEntity(podcastId = podcastId))

    override suspend fun unsubscribe(podcastId: Long) =
        subscriptionDao.unsubscribe(SubscriptionEntity(podcastId = podcastId))

    override fun observeSubscriptions(): Flow<List<Podcast>> =
        subscriptionDao.observeAll().flatMapLatest { subs ->
            val ids = subs.map { it.podcastId }
            podcastDao.observeByIds(ids).map { entities ->
                entities.map { e ->
                    Podcast(
                        id = e.id,
                        title = e.title,
                        artworkUrl = e.artworkUrl,
                        artistName = e.artistName,
                        feedUrl = e.feedUrl,
                        description = e.description,
                        genres = e.genres.split(",").filter { it.isNotBlank() },
                        episodeCount = e.episodeCount
                    )
                }
            }
        }

    override fun observeIsSubscribed(podcastId: Long): Flow<Boolean> =
        subscriptionDao.observeIsSubscribed(podcastId)

    override fun observeDownloadedEpisodes(): Flow<List<Episode>> =
        downloadedEpisodeDao.observeAll().flatMapLatest { downloads ->
            val guids = downloads.map { it.episodeGuid }
            combine(guids.map { guid ->
                episodeDao.observeByPodcast(0).map { episodes ->
                    episodes.firstOrNull { it.guid == guid }
                }
            }) { array -> array.filterNotNull().map { e ->
                Episode(
                    guid = e.guid,
                    podcastId = e.podcastId,
                    title = e.title,
                    description = e.description,
                    audioUrl = e.audioUrl,
                    artworkUrl = e.artworkUrl,
                    publishedAt = e.publishedAt,
                    durationSeconds = e.durationSeconds,
                    fileSizeBytes = e.fileSizeBytes,
                    playbackPosition = e.playbackPosition
                )
            }}
        }

    override fun observeIsDownloaded(episodeGuid: String): Flow<Boolean> =
        downloadedEpisodeDao.observeIsDownloaded(episodeGuid)

    override suspend fun saveDownload(episodeGuid: String, podcastId: Long, localPath: String, sizeBytes: Long) =
        downloadedEpisodeDao.insert(
            DownloadedEpisodeEntity(
                episodeGuid = episodeGuid,
                podcastId = podcastId,
                localFilePath = localPath,
                fileSizeBytes = sizeBytes
            )
        )

    override suspend fun deleteDownload(episodeGuid: String) {
        val entity = DownloadedEpisodeEntity(
            episodeGuid = episodeGuid,
            podcastId = 0,
            localFilePath = "",
            fileSizeBytes = 0
        )
        downloadedEpisodeDao.delete(entity)
    }
}
