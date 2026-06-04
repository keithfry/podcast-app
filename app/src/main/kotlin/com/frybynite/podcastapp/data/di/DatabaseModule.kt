package com.frybynite.podcastapp.data.di

import android.content.Context
import androidx.room.Room
import com.frybynite.podcastapp.data.db.PodcastDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): PodcastDatabase =
        Room.databaseBuilder(ctx, PodcastDatabase::class.java, "podcast.db").build()

    @Provides
    fun providePodcastDao(db: PodcastDatabase) = db.podcastDao()

    @Provides
    fun provideEpisodeDao(db: PodcastDatabase) = db.episodeDao()

    @Provides
    fun provideChapterDao(db: PodcastDatabase) = db.chapterDao()

    @Provides
    @Singleton
    @javax.inject.Named("podcasts_dir")
    fun providePodcastsDir(@ApplicationContext ctx: Context): java.io.File =
        java.io.File(ctx.filesDir, "podcasts")
}
