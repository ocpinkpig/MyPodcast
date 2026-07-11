package com.example.mypodcast.domain.usecase.episode

import com.example.mypodcast.data.download.EpisodeAudioDownloader
import com.example.mypodcast.domain.model.DownloadState
import com.example.mypodcast.domain.model.Episode
import com.example.mypodcast.domain.repository.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class DownloadEpisodeUseCase @Inject constructor(
    private val downloader: EpisodeAudioDownloader,
    private val libraryRepository: LibraryRepository
) {
    operator fun invoke(episode: Episode): Flow<DownloadState> = channelFlow {
        send(DownloadState.Downloading(0))
        try {
            val file = downloader.download(
                episodeGuid = episode.guid,
                audioUrl = episode.audioUrl,
                expectedFileSizeBytes = episode.fileSizeBytes
            ) { progress ->
                trySend(DownloadState.Downloading(progress))
            }
            libraryRepository.saveDownload(episode.guid, episode.podcastId, file.absolutePath, file.length())
            send(DownloadState.Completed(file.absolutePath))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            send(DownloadState.Failed(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)
}
