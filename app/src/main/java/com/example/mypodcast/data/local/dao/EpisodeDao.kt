package com.example.mypodcast.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.mypodcast.data.local.entity.EpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC")
    fun observeByPodcast(podcastId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE guid = :guid")
    suspend fun getByGuid(guid: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE guid IN (:guids)")
    suspend fun getByGuids(guids: List<String>): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE guid IN (:guids)")
    fun observeByGuids(guids: List<String>): Flow<List<EpisodeEntity>>

    @Upsert
    suspend fun upsertAll(episodes: List<EpisodeEntity>)

    @Query("UPDATE episodes SET playbackPosition = :positionMs, isPlayed = :isPlayed WHERE guid = :guid")
    suspend fun updateProgress(guid: String, positionMs: Long, isPlayed: Boolean)

    @Query("UPDATE episodes SET isFavorite = :isFavorite WHERE guid = :guid")
    suspend fun updateFavorite(guid: String, isFavorite: Boolean)

    @Query("SELECT * FROM episodes WHERE isFavorite = 1 ORDER BY publishedAt DESC")
    fun observeFavorites(): Flow<List<EpisodeEntity>>
}
