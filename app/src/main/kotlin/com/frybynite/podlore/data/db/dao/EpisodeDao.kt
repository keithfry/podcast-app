package com.frybynite.podlore.data.db.dao

import androidx.room.*
import com.frybynite.podlore.data.db.entities.EpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE podcastFeedUrl = :feedUrl ORDER BY pubDate DESC")
    fun getForPodcast(feedUrl: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE audioUrl = :audioUrl")
    suspend fun getByAudioUrl(audioUrl: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE podcastFeedUrl = :feedUrl")
    suspend fun getForPodcastOnce(feedUrl: String): List<EpisodeEntity>

    @Query("DELETE FROM episodes WHERE podcastFeedUrl = :feedUrl")
    suspend fun deleteForPodcast(feedUrl: String)

    @Query("SELECT * FROM episodes WHERE audioUrl = :audioUrl")
    fun getByAudioUrlFlow(audioUrl: String): Flow<EpisodeEntity?>

    // Used only in tests — replaces the full row including local state.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(episodes: List<EpisodeEntity>)

    // Feed refresh: update RSS-sourced fields for existing rows; insert new rows.
    // Preserves downloadPath, downloadStatus, lastPositionMs on conflict.
    @Transaction
    suspend fun upsertFromFeed(episodes: List<EpisodeEntity>) {
        insertIgnore(episodes)
        for (ep in episodes) {
            updateRssFields(ep.audioUrl, ep.title, ep.pubDate, ep.durationSeconds, ep.chaptersUrl, ep.transcriptUrl, ep.imageUrl)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(episodes: List<EpisodeEntity>)

    @Query("""
        UPDATE episodes
        SET title = :title,
            pubDate = :pubDate,
            durationSeconds = :durationSeconds,
            chaptersUrl = :chaptersUrl,
            transcriptUrl = :transcriptUrl,
            imageUrl = :imageUrl
        WHERE audioUrl = :audioUrl
    """)
    suspend fun updateRssFields(
        audioUrl: String,
        title: String,
        pubDate: Long,
        durationSeconds: Int,
        chaptersUrl: String?,
        transcriptUrl: String?,
        imageUrl: String?
    )

    @Query("UPDATE episodes SET downloadPath = :path, downloadStatus = :status WHERE audioUrl = :audioUrl")
    suspend fun updateDownloadStatus(audioUrl: String, path: String?, status: String)

    @Query("UPDATE episodes SET lastPositionMs = :positionMs WHERE audioUrl = :audioUrl")
    suspend fun updateLastPosition(audioUrl: String, positionMs: Long)

    @Query("UPDATE episodes SET isHeard = 1 WHERE audioUrl = :audioUrl")
    suspend fun markHeard(audioUrl: String)

    @Query("UPDATE episodes SET isHeard = 0 WHERE audioUrl = :audioUrl")
    suspend fun markUnheard(audioUrl: String)

    @Query("UPDATE episodes SET isLiked = :isLiked WHERE audioUrl = :audioUrl")
    suspend fun updateIsLiked(audioUrl: String, isLiked: Boolean)
}
