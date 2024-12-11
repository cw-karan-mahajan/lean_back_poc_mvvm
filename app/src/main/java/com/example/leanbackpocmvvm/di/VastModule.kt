package com.example.leanbackpocmvvm.di

import android.content.Context
import com.example.leanbackpocmvvm.remote.DynamicApiServiceFactory
import com.example.leanbackpocmvvm.vastdata.parser.VastParser
import com.example.leanbackpocmvvm.repository.VastRepository
import com.example.leanbackpocmvvm.repository.impl.VastRepositoryImpl
import com.example.leanbackpocmvvm.vastdata.tracking.AdEventTracker
import com.example.leanbackpocmvvm.utils.NetworkConnectivity
import com.example.leanbackpocmvvm.utils.getBandwidthBasedMaxBitrate
import com.example.leanbackpocmvvm.utils.getSupportedCodecs
import com.example.leanbackpocmvvm.utils.isAndroidVersion9Supported
import com.example.leanbackpocmvvm.vastdata.cache.SimpleVastCache
import com.example.leanbackpocmvvm.vastdata.handler.VastErrorHandler
import com.example.leanbackpocmvvm.vastdata.parser.VastAdSequenceManager
import com.example.leanbackpocmvvm.vastdata.tracking.VastTrackingManager
import com.example.leanbackpocmvvm.vastdata.validator.VastMediaSelector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VastModule {

    @Provides
    @Singleton
    fun provideVastParser(): VastParser {
        return VastParser()
    }

    @Provides
    @Singleton
    fun provideVastHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
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
    fun provideVastRepository(
        vastParser: VastParser,
        @ApplicationContext context: Context,
        networkConnectivity: NetworkConnectivity,
        okHttpClient: OkHttpClient,
        adEventTracker: AdEventTracker,
        dynamicApiServiceFactory: DynamicApiServiceFactory
    ): VastRepository {
        return VastRepositoryImpl(
            vastParser = vastParser,
            context = context,
            networkConnectivity = networkConnectivity,
            httpClient = okHttpClient,
            adEventTracker = adEventTracker,
            dynamicApiServiceFactory = dynamicApiServiceFactory
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

    /*@Provides
    @Singleton
    fun provideVastXmlValidator(): VastXmlValidator {
        return VastXmlValidator()  // No parameters needed
    }*/

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
}