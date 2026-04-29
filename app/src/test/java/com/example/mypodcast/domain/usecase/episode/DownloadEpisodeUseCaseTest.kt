package com.example.mypodcast.domain.usecase.episode

import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.io.IOException

class DownloadEpisodeUseCaseTest {
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
}
