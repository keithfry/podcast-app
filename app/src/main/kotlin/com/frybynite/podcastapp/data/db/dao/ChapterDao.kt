package com.frybynite.podcastapp.data.db.dao

import androidx.room.*
import com.frybynite.podcastapp.data.db.entities.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE episodeAudioUrl = :audioUrl ORDER BY startTimeMs ASC")
    fun getForEpisode(audioUrl: String): Flow<List<ChapterEntity>>

    @Query("SELECT COUNT(*) FROM chapters WHERE episodeAudioUrl = :audioUrl")
    suspend fun countForEpisode(audioUrl: String): Int

    @Query("DELETE FROM chapters WHERE episodeAudioUrl = :audioUrl")
    suspend fun deleteForEpisode(audioUrl: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Transaction
    suspend fun replaceChaptersForEpisode(audioUrl: String, chapters: List<ChapterEntity>) {
        deleteForEpisode(audioUrl)
        insertAll(chapters)
    }
}
