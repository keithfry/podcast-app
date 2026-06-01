package com.podcastapp.data.db.dao

import androidx.room.*
import com.podcastapp.data.db.entities.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE episodeAudioUrl = :audioUrl ORDER BY startTimeMs ASC")
    fun getForEpisode(audioUrl: String): Flow<List<ChapterEntity>>

    @Query("SELECT COUNT(*) FROM chapters WHERE episodeAudioUrl = :audioUrl")
    suspend fun countForEpisode(audioUrl: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)
}
