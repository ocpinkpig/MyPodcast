package com.example.mypodcast.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mypodcast.data.local.entity.SavedMomentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedMomentDao {
    @Query("SELECT * FROM saved_moments ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedMomentEntity>>

    @Query("SELECT COUNT(*) FROM saved_moments WHERE episodeGuid = :episodeGuid")
    fun observeCountForEpisode(episodeGuid: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(moment: SavedMomentEntity)

    @Query("DELETE FROM saved_moments WHERE id = :id")
    suspend fun deleteById(id: Long)
}
