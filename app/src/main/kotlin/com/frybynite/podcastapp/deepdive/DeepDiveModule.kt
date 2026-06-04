package com.frybynite.podcastapp.deepdive

import com.frybynite.podcastapp.BuildConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DeepDiveModule {
    @Binds @Singleton
    abstract fun bindTextSummarizer(impl: GroqTextSummarizer): TextSummarizer

    @Binds @Singleton
    abstract fun bindTtsSynthesizer(impl: AndroidTtsSynthesizer): TtsSynthesizer

    companion object {
        @Provides @Named("groq_api_key")
        fun provideGroqApiKey(): String = BuildConfig.GROQ_API_KEY

        @Provides @Named("hf_token")
        fun provideHfToken(): String = BuildConfig.HF_TOKEN
    }
}
