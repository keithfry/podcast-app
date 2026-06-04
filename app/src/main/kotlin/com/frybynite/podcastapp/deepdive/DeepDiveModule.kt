package com.frybynite.podcastapp.deepdive

import com.frybynite.podcastapp.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DeepDiveModule {

    @Provides @Singleton
    fun provideTextSummarizer(
        gemma: GemmaTextSummarizer,
        groq: GroqTextSummarizer
    ): TextSummarizer = if (OpenClDetector.isSupported()) gemma else groq

    @Provides @Singleton
    fun provideTtsSynthesizer(
        android: AndroidTtsSynthesizer,
        kokoro: KokoroTtsSynthesizer
    ): TtsSynthesizer = if (OpenClDetector.isSupported()) android else kokoro

    @Provides @Named("groq_api_key")
    fun provideGroqApiKey(): String = BuildConfig.GROQ_API_KEY

    @Provides @Named("hf_token")
    fun provideHfToken(): String = BuildConfig.HF_TOKEN

    @Provides @Singleton @Named("kokoro_client")
    fun provideKokoroClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}
