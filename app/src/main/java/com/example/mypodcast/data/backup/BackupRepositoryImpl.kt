package com.example.mypodcast.data.backup

import androidx.room.withTransaction
import com.example.mypodcast.data.local.AppDatabase
import com.example.mypodcast.data.local.dao.DownloadedEpisodeDao
import com.example.mypodcast.data.local.dao.EpisodeDao
import com.example.mypodcast.data.local.dao.PodcastDao
import com.example.mypodcast.data.local.dao.QueueDao
import com.example.mypodcast.data.local.dao.SavedMomentDao
import com.example.mypodcast.data.local.dao.SubscriptionDao
import com.example.mypodcast.data.local.entity.EpisodeEntity
import com.example.mypodcast.data.local.entity.PodcastEntity
import com.example.mypodcast.data.local.entity.QueueItemEntity
import com.example.mypodcast.data.local.entity.SavedMomentEntity
import com.example.mypodcast.data.local.entity.SubscriptionEntity
import com.example.mypodcast.domain.model.backup.BackupDownload
import com.example.mypodcast.domain.model.backup.BackupEpisode
import com.example.mypodcast.domain.model.backup.BackupMoment
import com.example.mypodcast.domain.model.backup.BackupPodcast
import com.example.mypodcast.domain.model.backup.BackupQueueItem
import com.example.mypodcast.domain.model.backup.BackupSubscription
import com.example.mypodcast.domain.model.backup.BackupSummary
import com.example.mypodcast.domain.model.backup.FeedTarget
import com.example.mypodcast.domain.model.backup.ImportResult
import com.example.mypodcast.domain.model.backup.LibraryBackup
import com.example.mypodcast.domain.repository.BackupRepository
import javax.inject.Inject

class BackupRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val subscriptionDao: SubscriptionDao,
    private val downloadedEpisodeDao: DownloadedEpisodeDao,
    private val queueDao: QueueDao,
    private val savedMomentDao: SavedMomentDao
) : BackupRepository {

    override suspend fun createBackup(): LibraryBackup {
        val subscriptions = subscriptionDao.getAll()
        val episodes = episodeDao.getAllWithUserState()
        val downloads = downloadedEpisodeDao.getAll()
        val queue = queueDao.getQueueItemsOrdered()
        val moments = savedMomentDao.getAll()

        val podcastIds = buildSet {
            subscriptions.forEach { add(it.podcastId) }
            episodes.forEach { add(it.podcastId) }
            downloads.forEach { add(it.podcastId) }
        }
        // Chunked to stay under SQLite's bound-variable limit (999).
        val podcasts = podcastIds.toList().chunked(500).flatMap { podcastDao.getByIds(it) }

        return LibraryBackup(
            formatVersion = LibraryBackup.FORMAT_VERSION,
            exportedAt = System.currentTimeMillis(),
            podcasts = podcasts.map { it.toBackup() },
            subscriptions = subscriptions.map { BackupSubscription(it.podcastId, it.subscribedAt) },
            episodes = episodes.map { it.toBackup() },
            downloads = downloads.map { BackupDownload(it.episodeGuid, it.podcastId, it.downloadedAt) },
            queue = queue.map { BackupQueueItem(it.episodeGuid, it.position) },
            moments = moments.map {
                BackupMoment(it.episodeGuid, it.positionMs, it.clipStartMs, it.clipEndMs, it.transcriptText, it.createdAt)
            }
        )
    }

    override suspend fun summarize(backup: LibraryBackup): BackupSummary {
        val downloadsToRestore = backup.downloads.count { downloadedEpisodeDao.getByGuid(it.episodeGuid) == null }
        return BackupSummary(
            exportedAt = backup.exportedAt,
            shows = backup.subscriptions.size,
            favorites = backup.episodes.count { it.isFavorite },
            downloadsToRestore = downloadsToRestore,
            moments = backup.moments.size,
            queueItems = backup.queue.size
        )
    }

    override suspend fun import(backup: LibraryBackup): ImportResult = db.withTransaction {
        podcastDao.insertAllIgnore(backup.podcasts.map { it.toEntity() })

        backup.subscriptions.forEach {
            subscriptionDao.subscribe(SubscriptionEntity(podcastId = it.podcastId, subscribedAt = it.subscribedAt))
        }

        // Episodes whose podcast is unknown even after the podcast merge would
        // violate the FK on episodes.podcastId; drop them instead of failing.
        val referencedIds = backup.episodes.map { it.podcastId }.distinct()
        val knownPodcastIds = referencedIds.chunked(500)
            .flatMap { podcastDao.getByIds(it) }
            .map { it.id }
            .toSet()
        val importableEpisodes = backup.episodes.filter { it.podcastId in knownPodcastIds }

        val existingGuids = importableEpisodes.map { it.guid }
            .chunked(500)
            .flatMap { episodeDao.getByGuids(it) }
            .map { it.guid }
            .toSet()
        episodeDao.insertAllIgnore(importableEpisodes.filter { it.guid !in existingGuids }.map { it.toEntity() })
        importableEpisodes.filter { it.guid in existingGuids }.forEach {
            episodeDao.mergeUserState(
                guid = it.guid,
                isFavorite = it.isFavorite,
                isPlayed = it.isPlayed,
                playbackPosition = it.playbackPosition,
                lastPlayedAt = it.lastPlayedAt
            )
        }

        val importedGuids = importableEpisodes.map { it.guid }.toSet()

        backup.queue.sortedBy { it.position }.forEach { item ->
            val episodeExists = item.episodeGuid in importedGuids ||
                episodeDao.getByGuid(item.episodeGuid) != null
            if (episodeExists && queueDao.findPosition(item.episodeGuid) == null) {
                queueDao.insert(QueueItemEntity(item.episodeGuid, queueDao.maxPosition() + 1))
            }
        }

        backup.moments.forEach { moment ->
            val episodeExists = moment.episodeGuid in importedGuids ||
                episodeDao.getByGuid(moment.episodeGuid) != null
            if (episodeExists && !savedMomentDao.exists(moment.episodeGuid, moment.positionMs, moment.createdAt)) {
                savedMomentDao.insert(
                    SavedMomentEntity(
                        episodeGuid = moment.episodeGuid,
                        positionMs = moment.positionMs,
                        clipStartMs = moment.clipStartMs,
                        clipEndMs = moment.clipEndMs,
                        transcriptText = moment.transcriptText,
                        createdAt = moment.createdAt
                    )
                )
            }
        }

        val downloadsToRestore = backup.downloads
            .map { it.episodeGuid }
            .filter { guid ->
                guid in importedGuids && downloadedEpisodeDao.getByGuid(guid) == null
            }

        val podcastsById = backup.podcasts.associateBy { it.id }
        val feedTargets = backup.subscriptions.mapNotNull { sub ->
            podcastsById[sub.podcastId]?.let { FeedTarget(it.id, it.feedUrl) }
        }

        ImportResult(downloadsToRestore = downloadsToRestore, feedTargets = feedTargets)
    }
}

private fun PodcastEntity.toBackup() = BackupPodcast(
    id = id,
    title = title,
    artworkUrl = artworkUrl,
    artistName = artistName,
    feedUrl = feedUrl,
    description = description,
    genres = genres,
    episodeCount = episodeCount,
    language = language
)

private fun EpisodeEntity.toBackup() = BackupEpisode(
    guid = guid,
    podcastId = podcastId,
    title = title,
    description = description,
    audioUrl = audioUrl,
    artworkUrl = artworkUrl,
    publishedAt = publishedAt,
    durationSeconds = durationSeconds,
    fileSizeBytes = fileSizeBytes,
    playbackPosition = playbackPosition,
    isPlayed = isPlayed,
    isFavorite = isFavorite,
    lastPlayedAt = lastPlayedAt,
    transcriptUrl = transcriptUrl,
    transcriptType = transcriptType
)

private fun BackupPodcast.toEntity() = PodcastEntity(
    id = id,
    title = title,
    artworkUrl = artworkUrl,
    artistName = artistName,
    feedUrl = feedUrl,
    description = description,
    genres = genres,
    episodeCount = episodeCount,
    language = language
)

private fun BackupEpisode.toEntity() = EpisodeEntity(
    guid = guid,
    podcastId = podcastId,
    title = title,
    description = description,
    audioUrl = audioUrl,
    artworkUrl = artworkUrl,
    publishedAt = publishedAt,
    durationSeconds = durationSeconds,
    fileSizeBytes = fileSizeBytes,
    playbackPosition = playbackPosition,
    isPlayed = isPlayed,
    isFavorite = isFavorite,
    lastPlayedAt = lastPlayedAt,
    transcriptUrl = transcriptUrl,
    transcriptType = transcriptType
)
