package com.example.leanbackpocmvvm.vastdata.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.vastdata.network.DynamicApiServiceFactory
import com.example.leanbackpocmvvm.vastdata.parser.VastParser
import com.example.leanbackpocmvvm.vastdata.tracking.AdEventTracker
import com.example.leanbackpocmvvm.utils.getBandwidthBasedMaxBitrate
import com.example.leanbackpocmvvm.utils.getSupportedCodecs
import com.example.leanbackpocmvvm.utils.isAndroidVersion9Supported
import com.example.leanbackpocmvvm.vastdata.cache.SimpleVastCache
import com.example.leanbackpocmvvm.vastdata.validator.VastErrorHandler
import com.example.leanbackpocmvvm.vastdata.network.NetworkConnectivity
import com.example.leanbackpocmvvm.vastdata.parser.VastAdSequenceManager
import com.example.leanbackpocmvvm.vastdata.tracking.VastTrackingManager
import com.example.leanbackpocmvvm.vastdata.validator.VastMediaSelector
import com.example.leanbackpocmvvm.vastdata.player.ExoPlayerManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VastModule {
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder().setLenient().disableHtmlEscaping().serializeNulls().create()
    }

    @Provides
    @Singleton
    fun provideVastHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Accept-Encoding", "identity")
                    .header("Accept", "application/json")
                chain.proceed(requestBuilder.build())
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideDynamicApiServiceFactory(): DynamicApiServiceFactory {
        val retrofitBuilder = Retrofit.Builder()
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(provideGson()))

        return DynamicApiServiceFactory(retrofitBuilder, provideVastHttpClient().newBuilder())
    }

    @Provides
    @Singleton
    fun provideVastParser(): VastParser {
        return VastParser()
    }

    @Provides
    @Singleton
    fun provideAdEventTracker(
        okHttpClient: OkHttpClient,
        networkConnectivity: NetworkConnectivity
    ): AdEventTracker {
        return AdEventTracker(
            httpClient = okHttpClient,
            networkConnectivity = networkConnectivity,
            retryAttempts = 3,
            retryDelayMs = 1000L
        )
    }

    @Provides
    @Singleton
    fun provideVastCache(
        @ApplicationContext context: Context
    ): SimpleVastCache {
        val cacheSize = if (isAndroidVersion9Supported()) {
            50 * 1024 * 1024L // 50MB for Android 9
        } else {
            100 * 1024 * 1024L // 100MB for other versions
        }

        return SimpleVastCache(
            context = context,
            maxCacheSize = cacheSize,
            cacheDir = context.cacheDir.resolve("vast_cache"),
            expirationTimeMs = 24 * 60 * 60 * 1000L // 24 hours
        )
    }

    @Provides
    @Singleton
    fun provideVastTrackingManager(
        adEventTracker: AdEventTracker,
        vastCache: SimpleVastCache
    ): VastTrackingManager {
        return VastTrackingManager(
            adEventTracker = adEventTracker,
            vastCache = vastCache,
            maxRetryAttempts = 3,
            retryDelayMs = 1000L,
            maxConcurrentTracking = 5
        )
    }

    @Provides
    @Singleton
    fun provideVastErrorHandler(): VastErrorHandler {
        return VastErrorHandler()
    }

    @Provides
    @Singleton
    fun provideVastMediaSelector(
        @ApplicationContext context: Context
    ): VastMediaSelector {
        return VastMediaSelector(
            context = context,
            preferredMimeTypes = listOf(
                "video/mp4",
                "video/webm",
                "application/x-mpegURL"
            ),
            maxBitrate = getBandwidthBasedMaxBitrate(context),
            supportedCodecs = getSupportedCodecs()
        )
    }

    @Provides
    @Singleton
    fun provideVastAdSequenceManager(
        vastParser: VastParser,
        vastMediaSelector: VastMediaSelector,
        vastTrackingManager: VastTrackingManager
    ): VastAdSequenceManager {
        return VastAdSequenceManager(
            vastParser = vastParser,
            vastMediaSelector = vastMediaSelector,
            vastTrackingManager = vastTrackingManager
        )
    }

    @UnstableApi
    @Provides
    @Singleton
    fun provideExoPlayerManager(
        @ApplicationContext context: Context,
        vastTrackingManager: VastTrackingManager
    ): ExoPlayerManager {
        return ExoPlayerManager(context, vastTrackingManager)
    }
}