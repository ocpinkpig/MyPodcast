package com.example.mypodcast.data.backup

import com.example.mypodcast.domain.model.backup.BackupDownload
import com.example.mypodcast.domain.model.backup.BackupEpisode
import com.example.mypodcast.domain.model.backup.BackupMoment
import com.example.mypodcast.domain.model.backup.BackupPodcast
import com.example.mypodcast.domain.model.backup.BackupQueueItem
import com.example.mypodcast.domain.model.backup.BackupSubscription
import com.example.mypodcast.domain.model.backup.InvalidBackupException
import com.example.mypodcast.domain.model.backup.LibraryBackup
import com.example.mypodcast.domain.model.backup.UnsupportedBackupVersionException
import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject

class BackupSerializer @Inject constructor() {
    private val gson = Gson()

    fun write(backup: LibraryBackup, output: OutputStream) {
        OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
            gson.toJson(backup, writer)
        }
    }

    fun read(input: InputStream): LibraryBackup {
        val raw = try {
            InputStreamReader(input, Charsets.UTF_8).use { reader ->
                gson.fromJson(reader, RawBackup::class.java)
            }
        } catch (e: JsonSyntaxException) {
            throw InvalidBackupException("Not a MyPodcast backup file")
        } catch (e: JsonIOException) {
            throw InvalidBackupException("Could not read the backup file")
        } ?: throw InvalidBackupException("The backup file is empty")

        val version = raw.formatVersion
            ?: throw InvalidBackupException("Not a MyPodcast backup file")
        if (version > LibraryBackup.FORMAT_VERSION) {
            throw UnsupportedBackupVersionException(version)
        }

        return LibraryBackup(
            formatVersion = version,
            exportedAt = raw.exportedAt ?: 0L,
            podcasts = raw.podcasts.orEmpty().map { it.validated() },
            subscriptions = raw.subscriptions.orEmpty().map { it.validated() },
            episodes = raw.episodes.orEmpty().map { it.validated() },
            downloads = raw.downloads.orEmpty().map { it.validated() },
            queue = raw.queue.orEmpty().map { it.validated() },
            moments = raw.moments.orEmpty().map { it.validated() }
        )
    }
}

private fun missing(field: String): Nothing =
    throw InvalidBackupException("Backup entry is missing required field: $field")

private class RawBackup(
    val formatVersion: Int? = null,
    val exportedAt: Long? = null,
    val podcasts: List<RawPodcast?>? = null,
    val subscriptions: List<RawSubscription?>? = null,
    val episodes: List<RawEpisode?>? = null,
    val downloads: List<RawDownload?>? = null,
    val queue: List<RawQueueItem?>? = null,
    val moments: List<RawMoment?>? = null
)

private class RawPodcast(
    val id: Long? = null,
    val title: String? = null,
    val artworkUrl: String? = null,
    val artistName: String? = null,
    val feedUrl: String? = null,
    val description: String? = null,
    val genres: String? = null,
    val episodeCount: Int? = null,
    val language: String? = null
)

private fun RawPodcast?.validated() = BackupPodcast(
    id = this?.id ?: missing("podcast.id"),
    title = this.title ?: missing("podcast.title"),
    artworkUrl = this.artworkUrl ?: "",
    artistName = this.artistName ?: "",
    feedUrl = this.feedUrl ?: missing("podcast.feedUrl"),
    description = this.description,
    genres = this.genres ?: "",
    episodeCount = this.episodeCount ?: 0,
    language = this.language
)

private class RawSubscription(val podcastId: Long? = null, val subscribedAt: Long? = null)

private fun RawSubscription?.validated() = BackupSubscription(
    podcastId = this?.podcastId ?: missing("subscription.podcastId"),
    subscribedAt = this.subscribedAt ?: 0L
)

private class RawEpisode(
    val guid: String? = null,
    val podcastId: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val audioUrl: String? = null,
    val artworkUrl: String? = null,
    val publishedAt: Long? = null,
    val durationSeconds: Int? = null,
    val fileSizeBytes: Long? = null,
    val playbackPosition: Long? = null,
    val isPlayed: Boolean? = null,
    val isFavorite: Boolean? = null,
    val lastPlayedAt: Long? = null,
    val transcriptUrl: String? = null,
    val transcriptType: String? = null
)

private fun RawEpisode?.validated() = BackupEpisode(
    guid = this?.guid ?: missing("episode.guid"),
    podcastId = this.podcastId ?: missing("episode.podcastId"),
    title = this.title ?: missing("episode.title"),
    description = this.description,
    audioUrl = this.audioUrl ?: missing("episode.audioUrl"),
    artworkUrl = this.artworkUrl,
    publishedAt = this.publishedAt ?: 0L,
    durationSeconds = this.durationSeconds ?: 0,
    fileSizeBytes = this.fileSizeBytes ?: 0L,
    playbackPosition = this.playbackPosition ?: 0L,
    isPlayed = this.isPlayed ?: false,
    isFavorite = this.isFavorite ?: false,
    lastPlayedAt = this.lastPlayedAt ?: 0L,
    transcriptUrl = this.transcriptUrl,
    transcriptType = this.transcriptType
)

private class RawDownload(
    val episodeGuid: String? = null,
    val podcastId: Long? = null,
    val downloadedAt: Long? = null
)

private fun RawDownload?.validated() = BackupDownload(
    episodeGuid = this?.episodeGuid ?: missing("download.episodeGuid"),
    podcastId = this.podcastId ?: missing("download.podcastId"),
    downloadedAt = this.downloadedAt ?: 0L
)

private class RawQueueItem(val episodeGuid: String? = null, val position: Int? = null)

private fun RawQueueItem?.validated() = BackupQueueItem(
    episodeGuid = this?.episodeGuid ?: missing("queue.episodeGuid"),
    position = this.position ?: 0
)

private class RawMoment(
    val episodeGuid: String? = null,
    val positionMs: Long? = null,
    val clipStartMs: Long? = null,
    val clipEndMs: Long? = null,
    val transcriptText: String? = null,
    val createdAt: Long? = null
)

private fun RawMoment?.validated() = BackupMoment(
    episodeGuid = this?.episodeGuid ?: missing("moment.episodeGuid"),
    positionMs = this.positionMs ?: 0L,
    clipStartMs = this.clipStartMs ?: 0L,
    clipEndMs = this.clipEndMs ?: 0L,
    transcriptText = this.transcriptText,
    createdAt = this.createdAt ?: 0L
)
