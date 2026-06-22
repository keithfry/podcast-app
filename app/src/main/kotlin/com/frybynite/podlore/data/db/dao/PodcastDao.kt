package com.frybynite.podlore.data.db.dao

import androidx.room.*
import com.frybynite.podlore.data.db.entities.PodcastEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts ORDER BY title COLLATE NOCASE ASC")
    fun getAll(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun getByUrl(feedUrl: String): PodcastEntity?

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl")
    fun observeByUrl(feedUrl: String): Flow<PodcastEntity?>

    @Query("SELECT COUNT(*) > 0 FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun existsByUrl(feedUrl: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(podcast: PodcastEntity)

    @Delete
    suspend fun delete(podcast: PodcastEntity)
}
