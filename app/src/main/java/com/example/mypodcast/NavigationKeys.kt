package com.example.mypodcast

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object HomeNavKey : NavKey
@Serializable data object SearchNavKey : NavKey
@Serializable data class PodcastDetailNavKey(val podcastId: Long) : NavKey
@Serializable data class PlayerNavKey(val episodeGuid: String) : NavKey
@Serializable data object LibraryNavKey : NavKey
