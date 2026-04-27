package com.example.mypodcast.domain.usecase.episode

import android.content.Context
import com.example.mypodcast.domain.model.DownloadState
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.repository.LibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

class DownloadEpisodeUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val libraryRepository: LibraryRepository
) {
    operator fun invoke(episode: Episode): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0))
        val file = File(context.filesDir, "episodes/${episode.guid}.mp3").also {
            it.parentFile?.mkdirs()
        }
        try {
            val request = Request.Builder().url(episode.audioUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body ?: error("Empty response")
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: episode.fileSizeBytes
                var downloadedBytes = 0L
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytes: Int
                        while (input.read(buffer).also { bytes = it } != -1) {
                            output.write(buffer, 0, bytes)
                            downloadedBytes += bytes
                            if (totalBytes > 0) {
                                val progress = (downloadedBytes * 100 / totalBytes).toInt()
                                emit(DownloadState.Downloading(progress))
                            }
                        }
                    }
                }
            }
            libraryRepository.saveDownload(episode.guid, episode.podcastId, file.absolutePath, file.length())
            emit(DownloadState.Completed(file.absolutePath))
        } catch (e: Exception) {
            file.delete()
            emit(DownloadState.Failed(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)
}
