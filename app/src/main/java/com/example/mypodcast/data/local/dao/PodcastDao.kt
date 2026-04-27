package com.example.mypodcast.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.mypodcast.data.local.entity.PodcastEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts WHERE id = :id")
    suspend fun getById(id: Long): PodcastEntity?

    @Upsert
    suspend fun upsert(podcast: PodcastEntity)

    @Upsert
    suspend fun upsertAll(podcasts: List<PodcastEntity>)

    @Query("SELECT * FROM podcasts WHERE id IN (:ids)")
    fun observeByIds(ids: List<Long>): Flow<List<PodcastEntity>>
}
