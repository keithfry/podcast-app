package com.frybynite.podcastapp.deepdive

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DeepDiveModule {
    @Binds @Singleton
    abstract fun bindTextSummarizer(impl: GemmaTextSummarizer): TextSummarizer

    @Binds @Singleton
    abstract fun bindTtsSynthesizer(impl: AndroidTtsSynthesizer): TtsSynthesizer
}
