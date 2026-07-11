package com.example.mypodcast.domain.model.backup

data class LibraryBackup(
    val formatVersion: Int,
    val exportedAt: Long,
    val podcasts: List<BackupPodcast>,
    val subscriptions: List<BackupSubscription>,
    val episodes: List<BackupEpisode>,
    val downloads: List<BackupDownload>,
    val queue: List<BackupQueueItem>,
    val moments: List<BackupMoment>
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

data class BackupPodcast(
    val id: Long,
    val title: String,
    val artworkUrl: String,
    val artistName: String,
    val feedUrl: String,
    val description: String?,
    val genres: String,
    val episodeCount: Int,
    val language: String?
)

data class BackupSubscription(val podcastId: Long, val subscribedAt: Long)

data class BackupEpisode(
    val guid: String,
    val podcastId: Long,
    val title: String,
    val description: String?,
    val audioUrl: String,
    val artworkUrl: String?,
    val publishedAt: Long,
    val durationSeconds: Int,
    val fileSizeBytes: Long,
    val playbackPosition: Long,
    val isPlayed: Boolean,
    val isFavorite: Boolean,
    val lastPlayedAt: Long,
    val transcriptUrl: String?,
    val transcriptType: String?
)

data class BackupDownload(val episodeGuid: String, val podcastId: Long, val downloadedAt: Long)

data class BackupQueueItem(val episodeGuid: String, val position: Int)

data class BackupMoment(
    val episodeGuid: String,
    val positionMs: Long,
    val clipStartMs: Long,
    val clipEndMs: Long,
    val transcriptText: String?,
    val createdAt: Long
)

data class BackupSummary(
    val exportedAt: Long,
    val shows: Int,
    val favorites: Int,
    val downloadsToRestore: Int,
    val moments: Int,
    val queueItems: Int
)

data class FeedTarget(val podcastId: Long, val feedUrl: String)

data class ImportResult(
    val downloadsToRestore: List<String>,
    val feedTargets: List<FeedTarget>
)

/** The file is not parseable as a MyPodcast backup. */
class InvalidBackupException(message: String) : Exception(message)

/** The backup was written by a newer app with an unknown format. */
class UnsupportedBackupVersionException(val version: Int) :
    Exception("Unsupported backup format version $version")
