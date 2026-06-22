package com.frybynite.podlore.deepdive

import android.content.Context
import com.frybynite.podlore.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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
        @ApplicationContext context: Context,
        litert: LiteRtTextSummarizer,
        gemma: GemmaTextSummarizer,
        groq: GroqTextSummarizer
    ): TextSummarizer {
        val isAutomotive = context.packageManager.hasSystemFeature("android.hardware.type.automotive")
        return if (isAutomotive) groq else FallbackTextSummarizer(litert, gemma, groq)
    }

    @Provides @Singleton
    fun provideTtsSynthesizer(
        kokoro: KokoroTtsSynthesizer,
        android: AndroidTtsSynthesizer
    ): TtsSynthesizer = FallbackTtsSynthesizer(kokoro, android)

    @Provides @Named("groq_api_key")
    fun provideGroqApiKey(): String = BuildConfig.GROQ_API_KEY

    @Provides @Named("hf_token")
    fun provideHfToken(): String = BuildConfig.HF_TOKEN

    @Provides @Named("modal_tts_url")
    fun provideModalTtsUrl(): String = BuildConfig.MODAL_TTS_URL

    @Provides @Singleton @Named("kokoro_client")
    fun provideKokoroClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}
