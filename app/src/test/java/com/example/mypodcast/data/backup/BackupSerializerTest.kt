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
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupSerializerTest {
    private val serializer = BackupSerializer()

    private fun sampleBackup() = LibraryBackup(
        formatVersion = LibraryBackup.FORMAT_VERSION,
        exportedAt = 123L,
        podcasts = listOf(
            BackupPodcast(
                id = 1L,
                title = "Show",
                artworkUrl = "https://art.png",
                artistName = "Artist",
                feedUrl = "https://feed.xml",
                description = "desc",
                genres = "Tech",
                episodeCount = 3,
                language = "en-US"
            )
        ),
        subscriptions = listOf(BackupSubscription(1L, 42L)),
        episodes = listOf(
            BackupEpisode(
                guid = "e1",
                podcastId = 1L,
                title = "Episode",
                description = null,
                audioUrl = "https://audio.mp3",
                artworkUrl = null,
                publishedAt = 10L,
                durationSeconds = 60,
                fileSizeBytes = 1_024L,
                playbackPosition = 5L,
                isPlayed = true,
                isFavorite = true,
                lastPlayedAt = 99L,
                transcriptUrl = null,
                transcriptType = null
            )
        ),
        downloads = listOf(BackupDownload("e1", 1L, 7L)),
        queue = listOf(BackupQueueItem("e1", 0)),
        moments = listOf(BackupMoment("e1", 1_000L, 0L, 2_000L, "text", 5L))
    )

    private fun read(json: String) = serializer.read(ByteArrayInputStream(json.toByteArray()))

    @Test
    fun roundTrip_preservesEverything() {
        val out = ByteArrayOutputStream()
        serializer.write(sampleBackup(), out)

        val parsed = serializer.read(ByteArrayInputStream(out.toByteArray()))

        assertEquals(sampleBackup(), parsed)
    }

    @Test(expected = InvalidBackupException::class)
    fun read_rejectsNonJson() {
        read("this is not json {{{")
    }

    @Test(expected = InvalidBackupException::class)
    fun read_rejectsJsonWithoutFormatVersion() {
        read("""{"exportedAt": 1}""")
    }

    @Test(expected = InvalidBackupException::class)
    fun read_rejectsEpisodeMissingGuid() {
        read(
            """{"formatVersion": 1, "episodes": [{"podcastId": 1, "title": "t", "audioUrl": "u"}]}"""
        )
    }

    @Test
    fun read_rejectsNewerFormatVersion() {
        val error = runCatching { read("""{"formatVersion": 99}""") }.exceptionOrNull()

        assertTrue(error is UnsupportedBackupVersionException)
        assertEquals(99, (error as UnsupportedBackupVersionException).version)
    }

    @Test
    fun read_defaultsMissingSectionsToEmpty() {
        val parsed = read("""{"formatVersion": 1, "exportedAt": 5}""")

        assertEquals(0, parsed.podcasts.size)
        assertEquals(0, parsed.subscriptions.size)
        assertEquals(0, parsed.episodes.size)
        assertEquals(0, parsed.downloads.size)
        assertEquals(0, parsed.queue.size)
        assertEquals(0, parsed.moments.size)
    }
}
