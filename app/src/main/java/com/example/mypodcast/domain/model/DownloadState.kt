package com.example.mypodcast.domain.model

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progressPercent: Int) : DownloadState
    data class Completed(val localPath: String) : DownloadState
    data class Failed(val message: String) : DownloadState
}
