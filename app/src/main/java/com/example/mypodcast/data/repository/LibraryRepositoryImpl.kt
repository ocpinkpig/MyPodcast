@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.mypodcast.data.repository

import android.content.Context
import com.example.mypodcast.data.local.dao.DownloadedEpisodeDao
import com.example.mypodcast.data.local.dao.EpisodeDao
import com.example.mypodcast.data.local.dao.PodcastDao
import com.example.mypodcast.data.local.dao.SubscriptionDao
import com.example.mypodcast.data.local.entity.DownloadedEpisodeEntity
import com.example.mypodcast.data.local.entity.SubscriptionEntity
import com.example.mypodcast.data.transcription.GeneratedTranscriptStore
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.model.Podcast
import com.example.mypodcast.domain.model.TranscriptStatus
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
    private val downloadedEpisodeDao: DownloadedEpisodeDao,
    private val generatedTranscriptStore: GeneratedTranscriptStore
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
                            // Keep the feed URL: PlayerController swaps in the
                            // downloaded file itself (see playbackSource).
                            audioUrl = e.audioUrl,
                            artworkUrl = e.artworkUrl?.takeIf { it.isNotBlank() }
                                ?: artworkByPodcastId[e.podcastId],
                            publishedAt = e.publishedAt,
                            durationSeconds = e.durationSeconds,
                            fileSizeBytes = e.fileSizeBytes,
                            playbackPosition = e.playbackPosition,
                            isPlayed = e.isPlayed,
                            isFavorite = e.isFavorite
                        )
                    }.sortedByDescending { it.publishedAt }
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
        withContext(Dispatchers.IO) {
            runCatching { generatedTranscriptStore.delete(episodeGuid) }
        }
        downloadedEpisodeDao.deleteByGuid(episodeGuid)
    }

    override suspend fun getDownloadedFilePath(episodeGuid: String): String? =
        downloadedEpisodeDao.getByGuid(episodeGuid)?.localFilePath

    override suspend fun getPodcastLanguage(podcastId: Long): String? =
        podcastDao.getById(podcastId)?.language

    override fun observeTranscriptStatuses(): Flow<Map<String, TranscriptStatus>> =
        downloadedEpisodeDao.observeAll().map { downloads ->
            downloads.associate { d ->
                d.episodeGuid to runCatching { TranscriptStatus.valueOf(d.transcriptStatus) }
                    .getOrDefault(TranscriptStatus.NONE)
            }
        }

    override suspend fun setTranscriptStatus(episodeGuid: String, status: TranscriptStatus) =
        downloadedEpisodeDao.updateTranscriptStatus(episodeGuid, status.name)

    /**
     * Deletes files under `episodes/` that no download row points at.
     *
     * Runs at app start, possibly alongside a download (e.g. the restore
     * worker), which writes `<guid>.mp3.part`, renames it and only then inserts
     * its row. Anything modified within [ORPHAN_MIN_AGE_MS] is therefore left
     * for a later launch: an in-flight `.part` file keeps being written to, and
     * a just-renamed file keeps its recent mtime until its row exists.
     *
     * Paths are derived from the feed's guid, so URL-style guids nest files in
     * subdirectories; the walk covers those and removes old empty directories.
     * (Emptying a directory refreshes its mtime, so it goes on a later launch.)
     */
    override suspend fun cleanupOrphanedFiles() = withContext(Dispatchers.IO) {
        val episodesDir = File(context.filesDir, "episodes")
        if (!episodesDir.exists()) return@withContext

        // Read rows before walking: a download recorded after this read is
        // still protected by its recent mtime.
        val recordedPaths = downloadedEpisodeDao.getAll()
            .mapNotNull { runCatching { File(it.localFilePath).canonicalPath }.getOrNull() }
            .toSet()
        val cutoff = System.currentTimeMillis() - ORPHAN_MIN_AGE_MS
        episodesDir.walkBottomUp().forEach { file ->
            runCatching {
                when {
                    file == episodesDir -> Unit
                    file.lastModified() > cutoff -> Unit
                    // delete() only succeeds on an empty directory.
                    file.isDirectory -> file.delete()
                    file.canonicalPath !in recordedPaths -> file.delete()
                    else -> Unit
                }
            }
        }
    }

    private companion object {
        const val ORPHAN_MIN_AGE_MS = 60 * 60 * 1000L
    }
}
