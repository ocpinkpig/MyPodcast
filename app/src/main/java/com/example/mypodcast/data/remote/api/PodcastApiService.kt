package com.example.mypodcast.data.remote.api

import com.example.mypodcast.data.remote.api.dto.ItunesSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface PodcastApiService {

    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("media") media: String = "podcast",
        @Query("limit") limit: Int = 20
    ): ItunesSearchResponse

    @GET("lookup")
    suspend fun lookup(@Query("id") id: Long): ItunesSearchResponse

    @GET("search")
    suspend fun searchByCategory(
        @Query("term") category: String,
        @Query("media") media: String = "podcast",
        @Query("entity") entity: String = "podcast",
        @Query("limit") limit: Int = 10
    ): ItunesSearchResponse
}
