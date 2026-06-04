package com.frybynite.podcastapp.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.frybynite.podcastapp.data.db.PodcastDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deep_dives` (" +
                "`episodeAudioUrl` TEXT NOT NULL, " +
                "`chapterUrl` TEXT NOT NULL, " +
                "`filePath` TEXT NOT NULL, " +
                "`summaryText` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`episodeAudioUrl`, `chapterUrl`))"
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): PodcastDatabase =
        Room.databaseBuilder(ctx, PodcastDatabase::class.java, "podcast.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun providePodcastDao(db: PodcastDatabase) = db.podcastDao()

    @Provides
    fun provideEpisodeDao(db: PodcastDatabase) = db.episodeDao()

    @Provides
    fun provideChapterDao(db: PodcastDatabase) = db.chapterDao()

    @Provides
    fun provideDeepDiveDao(db: PodcastDatabase) = db.deepDiveDao()

    @Provides
    @Singleton
    @javax.inject.Named("podcasts_dir")
    fun providePodcastsDir(@ApplicationContext ctx: Context): java.io.File =
        java.io.File(ctx.filesDir, "podcasts")
}
