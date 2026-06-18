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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN lastPositionMs INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN imageUrl TEXT")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN isHeard INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN transcriptUrl TEXT")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN isLiked INTEGER NOT NULL DEFAULT 0")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): PodcastDatabase =
        Room.databaseBuilder(ctx, PodcastDatabase::class.java, "podcast.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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
