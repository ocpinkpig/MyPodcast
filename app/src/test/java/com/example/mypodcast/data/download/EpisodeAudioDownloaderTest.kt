package com.example.mypodcast.data.download

import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.io.IOException

class EpisodeAudioDownloaderTest {
    @Test
    fun requireCompleteDownload_rejectsShortDownload() {
        val error = kotlin.runCatching {
            requireCompleteDownload(downloadedBytes = 1_024L, expectedBytes = 2_048L)
        }.exceptionOrNull()

        assertEquals(
            "Download incomplete: received 1024 of 2048 bytes",
            (error as IOException).message
        )
    }

    @Test
    fun expectedDownloadBytes_prefersContentLengthThenFallsBack() {
        assertEquals(10L, expectedDownloadBytes(contentLength = 10L, episodeFileSizeBytes = 20L))
        assertEquals(20L, expectedDownloadBytes(contentLength = -1L, episodeFileSizeBytes = 20L))
        assertEquals(null, expectedDownloadBytes(contentLength = 0L, episodeFileSizeBytes = 0L))
    }
}
