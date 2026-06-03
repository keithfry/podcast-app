package com.frybynite.podcastapp.data.db.dao

import androidx.room.*
import com.frybynite.podcastapp.data.db.entities.EpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE podcastFeedUrl = :feedUrl ORDER BY pubDate DESC")
    fun getForPodcast(feedUrl: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE audioUrl = :audioUrl")
    suspend fun getByAudioUrl(audioUrl: String): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(episodes: List<EpisodeEntity>)

    @Query("UPDATE episodes SET downloadPath = :path, downloadStatus = :status WHERE audioUrl = :audioUrl")
    suspend fun updateDownloadStatus(audioUrl: String, path: String?, status: String)
}
