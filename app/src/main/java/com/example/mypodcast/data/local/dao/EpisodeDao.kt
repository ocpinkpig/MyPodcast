package com.example.mypodcast.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Query(
        "SELECT * FROM episodes " +
            "WHERE lastPlayedAt > 0 " +
            "ORDER BY lastPlayedAt DESC " +
            "LIMIT 1"
    )
    suspend fun getLastPlayback(): EpisodeEntity?

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

    @Query("UPDATE episodes SET lastPlayedAt = :ts WHERE guid = :guid")
    suspend fun touchLastPlayed(guid: String, ts: Long)

    @Query(
        "SELECT * FROM episodes " +
            "WHERE lastPlayedAt > 0 OR playbackPosition > 0 OR isPlayed = 1 " +
            "ORDER BY CASE WHEN lastPlayedAt > 0 THEN lastPlayedAt ELSE publishedAt END DESC"
    )
    fun observeHistory(): Flow<List<EpisodeEntity>>

    @Query(
        """
        SELECT
            podcasts.id AS id,
            podcasts.title AS title,
            podcasts.artworkUrl AS artworkUrl,
            podcasts.artistName AS artistName,
            podcasts.feedUrl AS feedUrl,
            podcasts.description AS description,
            podcasts.genres AS genres,
            podcasts.episodeCount AS episodeCount,
            COUNT(episodes.guid) AS playedEpisodeCount,
            MAX(episodes.lastPlayedAt) AS latestPlayedAt
        FROM episodes
        INNER JOIN podcasts ON podcasts.id = episodes.podcastId
        WHERE episodes.lastPlayedAt > 0
            OR episodes.playbackPosition > 0
            OR episodes.isPlayed = 1
        GROUP BY podcasts.id
        ORDER BY playedEpisodeCount DESC, latestPlayedAt DESC, podcasts.title ASC
        LIMIT :limit
        """
    )
    fun observeTopShows(limit: Int): Flow<List<TopShowRow>>

    @Query(
        "SELECT podcastId, COUNT(*) AS count FROM episodes " +
            "WHERE publishedAt >= :threshold " +
            "AND playbackPosition < 60000 " +
            "AND isPlayed = 0 " +
            "GROUP BY podcastId"
    )
    fun observeNewEpisodeCounts(threshold: Long): Flow<List<NewEpisodeCountRow>>

    @Query(
        """
        SELECT DISTINCT episodes.* FROM episodes
        LEFT JOIN queue_items ON queue_items.episodeGuid = episodes.guid
        LEFT JOIN saved_moments ON saved_moments.episodeGuid = episodes.guid
        LEFT JOIN downloaded_episodes ON downloaded_episodes.episodeGuid = episodes.guid
        WHERE episodes.isFavorite = 1
            OR episodes.isPlayed = 1
            OR episodes.playbackPosition > 0
            OR episodes.lastPlayedAt > 0
            OR queue_items.episodeGuid IS NOT NULL
            OR saved_moments.episodeGuid IS NOT NULL
            OR downloaded_episodes.episodeGuid IS NOT NULL
        """
    )
    suspend fun getAllWithUserState(): List<EpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(episodes: List<EpisodeEntity>)

    @Query(
        """
        UPDATE episodes SET
            isFavorite = CASE WHEN :isFavorite THEN 1 ELSE isFavorite END,
            isPlayed = CASE WHEN :isPlayed THEN 1 ELSE isPlayed END,
            playbackPosition = MAX(playbackPosition, :playbackPosition),
            lastPlayedAt = MAX(lastPlayedAt, :lastPlayedAt)
        WHERE guid = :guid
        """
    )
    suspend fun mergeUserState(
        guid: String,
        isFavorite: Boolean,
        isPlayed: Boolean,
        playbackPosition: Long,
        lastPlayedAt: Long
    )
}

data class NewEpisodeCountRow(val podcastId: Long, val count: Int)

data class TopShowRow(
    val id: Long,
    val title: String,
    val artworkUrl: String,
    val artistName: String,
    val feedUrl: String,
    val description: String?,
    val genres: String,
    val episodeCount: Int,
    val playedEpisodeCount: Int,
    val latestPlayedAt: Long
)
