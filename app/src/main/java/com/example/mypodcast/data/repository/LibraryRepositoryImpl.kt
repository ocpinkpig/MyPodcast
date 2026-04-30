@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.mypodcast.data.repository

import android.content.Context
import com.example.mypodcast.data.local.dao.DownloadedEpisodeDao
import com.example.mypodcast.data.local.dao.EpisodeDao
import com.example.mypodcast.data.local.dao.PodcastDao
import com.example.mypodcast.data.local.dao.SubscriptionDao
import com.example.mypodcast.data.local.entity.DownloadedEpisodeEntity
import com.example.mypodcast.data.local.entity.SubscriptionEntity
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.repository.LibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class LibraryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
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
            if (ids.isEmpty()) flowOf(emptyList())
            else podcastDao.observeByIds(ids).map { entities ->
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
            if (downloads.isEmpty()) flowOf(emptyList())
            else {
                val guids = downloads.map { it.episodeGuid }
                val pathByGuid = downloads.associate { it.episodeGuid to it.localFilePath }
                val podcastIds = downloads.map { it.podcastId }.distinct()
                episodeDao.observeByGuids(guids).combine(
                    podcastDao.observeByIds(podcastIds)
                ) { entities, podcasts ->
                    val artworkByPodcastId = podcasts.associate { it.id to it.artworkUrl }
                    entities.map { e ->
                        Episode(
                            guid = e.guid,
                            podcastId = e.podcastId,
                            title = e.title,
                            description = e.description,
                            audioUrl = pathByGuid[e.guid] ?: e.audioUrl,
                            artworkUrl = e.artworkUrl?.takeIf { it.isNotBlank() }
                                ?: artworkByPodcastId[e.podcastId],
                            publishedAt = e.publishedAt,
                            durationSeconds = e.durationSeconds,
                            fileSizeBytes = e.fileSizeBytes,
                            playbackPosition = e.playbackPosition,
                            isPlayed = e.isPlayed
                        )
                    }
                }
            }
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
        val entity = downloadedEpisodeDao.getByGuid(episodeGuid)
        if (entity != null) {
            withContext(Dispatchers.IO) {
                runCatching { File(entity.localFilePath).delete() }
            }
        }
        downloadedEpisodeDao.deleteByGuid(episodeGuid)
    }

    override suspend fun cleanupOrphanedFiles() = withContext(Dispatchers.IO) {
        val episodesDir = File(context.filesDir, "episodes")
        if (!episodesDir.exists()) return@withContext
        val files = episodesDir.listFiles().orEmpty()
        if (files.isEmpty()) return@withContext

        val dbPaths = downloadedEpisodeDao.getAll().map { it.localFilePath }.toSet()
        files.forEach { file ->
            if (file.absolutePath !in dbPaths) {
                runCatching { file.delete() }
            }
        }
    }
}
