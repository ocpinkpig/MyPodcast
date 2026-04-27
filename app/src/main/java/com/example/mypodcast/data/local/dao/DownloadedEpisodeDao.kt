package com.example.mypodcast.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mypodcast.data.local.entity.DownloadedEpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedEpisodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadedEpisodeEntity)

    @Delete
    suspend fun delete(download: DownloadedEpisodeEntity)

    @Query("SELECT * FROM downloaded_episodes ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<DownloadedEpisodeEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_episodes WHERE episodeGuid = :guid)")
    fun observeIsDownloaded(guid: String): Flow<Boolean>
}
