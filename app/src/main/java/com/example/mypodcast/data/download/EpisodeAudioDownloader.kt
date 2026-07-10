package com.example.mypodcast.data.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Streams an episode's audio to app-private storage. */
interface EpisodeAudioDownloader {
    /**
     * Downloads [audioUrl] to `files/episodes/<episodeGuid>.mp3`, reporting percent
     * progress via [onProgress]. Returns the finished file or throws [IOException].
     */
    suspend fun download(
        episodeGuid: String,
        audioUrl: String,
        expectedFileSizeBytes: Long,
        onProgress: (Int) -> Unit = {}
    ): File
}

class OkHttpEpisodeAudioDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) : EpisodeAudioDownloader {

    override suspend fun download(
        episodeGuid: String,
        audioUrl: String,
        expectedFileSizeBytes: Long,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "episodes/$episodeGuid.mp3").also {
            it.parentFile?.mkdirs()
        }
        val tempFile = File(file.parentFile, "${file.name}.part")
        try {
            tempFile.delete()
            val request = Request.Builder().url(audioUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: error("Empty response")
                val totalBytes = expectedDownloadBytes(
                    contentLength = body.contentLength(),
                    episodeFileSizeBytes = expectedFileSizeBytes
                )
                var downloadedBytes = 0L
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytes: Int
                        while (input.read(buffer).also { bytes = it } != -1) {
                            output.write(buffer, 0, bytes)
                            downloadedBytes += bytes
                            if (totalBytes != null) {
                                onProgress((downloadedBytes * 100 / totalBytes).toInt())
                            }
                        }
                    }
                }
                requireCompleteDownload(downloadedBytes, totalBytes)
            }
            if (file.exists() && !file.delete()) {
                throw IOException("Could not replace existing download")
            }
            if (!tempFile.renameTo(file)) {
                throw IOException("Could not finalize download")
            }
            file
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }
}

internal fun expectedDownloadBytes(contentLength: Long, episodeFileSizeBytes: Long): Long? =
    contentLength.takeIf { it > 0 } ?: episodeFileSizeBytes.takeIf { it > 0 }

@Throws(IOException::class)
internal fun requireCompleteDownload(downloadedBytes: Long, expectedBytes: Long?) {
    if (expectedBytes != null && downloadedBytes < expectedBytes) {
        throw IOException("Download incomplete: received $downloadedBytes of $expectedBytes bytes")
    }
}
