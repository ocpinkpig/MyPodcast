package com.example.mypodcast.data.remote.api.dto

import com.google.gson.annotations.SerializedName

data class ItunesSearchResponse(
    val resultCount: Int,
    val results: List<ItunesResult>
)

data class ItunesResult(
    val trackId: Long,
    val trackName: String,
    @SerializedName("artworkUrl600") val artworkUrl: String,
    val artistName: String,
    val feedUrl: String?,
    val trackCount: Int,
    val genres: List<String>,
    val description: String?
)
