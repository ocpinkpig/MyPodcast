package com.example.mypodcast.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.mypodcast.data.local.entity.QueueItemEntity

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    suspend fun getQueueItemsOrdered(): List<QueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: QueueItemEntity)

    @Query("DELETE FROM queue_items WHERE episodeGuid = :guid")
    suspend fun remove(guid: String)

    @Query("DELETE FROM queue_items")
    suspend fun clear()

    @Query("UPDATE queue_items SET position = position + 1")
    suspend fun shiftAllRight()

    @Query("UPDATE queue_items SET position = position - 1 WHERE position > :removedPos")
    suspend fun collapseFrom(removedPos: Int)

    @Query("SELECT position FROM queue_items WHERE episodeGuid = :guid")
    suspend fun findPosition(guid: String): Int?

    @Query("SELECT COALESCE(MAX(position), -1) FROM queue_items")
    suspend fun maxPosition(): Int

    @Transaction
    suspend fun replaceOrdered(guids: List<String>) {
        clear()
        guids.forEachIndexed { index, guid -> insert(QueueItemEntity(guid, index)) }
    }
}
