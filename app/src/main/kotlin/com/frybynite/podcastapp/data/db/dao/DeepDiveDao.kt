package com.frybynite.podcastapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.frybynite.podcastapp.data.db.entities.DeepDiveEntity

@Dao
interface DeepDiveDao {
    @Query("SELECT * FROM deep_dives WHERE episodeAudioUrl = :episodeAudioUrl AND chapterUrl = :chapterUrl")
    suspend fun get(episodeAudioUrl: String, chapterUrl: String): DeepDiveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeepDiveEntity)

    @Query("DELETE FROM deep_dives WHERE episodeAudioUrl = :episodeAudioUrl")
    suspend fun deleteForEpisode(episodeAudioUrl: String)
}
