package com.frybynite.podcastapp.data.di

import android.content.Context
import com.frybynite.podcastapp.data.network.FeedApi
import com.frybynite.podcastapp.data.network.RssParser
import com.frybynite.podcastapp.data.repository.TranscriptRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Provides
    @Singleton
    fun provideFeedApi(okHttp: OkHttpClient, moshi: Moshi): FeedApi = FeedApi(okHttp, moshi)

    @Provides
    @Singleton
    fun provideRssParser(): RssParser = RssParser()

    @Provides
    @Singleton
    fun provideTranscriptRepository(
        feedApi: FeedApi,
        moshi: Moshi,
        @ApplicationContext context: Context
    ): TranscriptRepository = TranscriptRepository(feedApi, moshi, File(context.filesDir, "transcripts"))
}
