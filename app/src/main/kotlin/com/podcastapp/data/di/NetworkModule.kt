package com.podcastapp.data.di

import com.podcastapp.data.network.FeedApi
import com.podcastapp.data.network.RssParser
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
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
}
