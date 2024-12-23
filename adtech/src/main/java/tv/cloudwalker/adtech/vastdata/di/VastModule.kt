package tv.cloudwalker.adtech.vastdata.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.google.gson.Gson
import tv.cloudwalker.adtech.vastdata.network.DynamicApiServiceFactory
import tv.cloudwalker.adtech.vastdata.parser.VastParser
import tv.cloudwalker.adtech.vastdata.tracking.AdEventTracker
import tv.cloudwalker.adtech.vastdata.cache.SimpleVastCache
import tv.cloudwalker.adtech.vastdata.validator.VastErrorHandler
import tv.cloudwalker.adtech.vastdata.network.NetworkConnectivity
import tv.cloudwalker.adtech.vastdata.parser.VastAdSequenceManager
import tv.cloudwalker.adtech.vastdata.tracking.VastTrackingManager
import tv.cloudwalker.adtech.vastdata.validator.VastMediaSelector
import tv.cloudwalker.adtech.player.ExoPlayerManager
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
import tv.cloudwalker.adtech.utils.getBandwidthBasedMaxBitrate
import tv.cloudwalker.adtech.utils.getSupportedCodecs
import tv.cloudwalker.adtech.utils.isAndroidVersion9Supported
import tv.cloudwalker.adtech.vastdata.repository.VastRepository
import tv.cloudwalker.adtech.vastdata.repository.impl.VastRepositoryImpl
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VastModule {

    @Provides
    @Singleton
    fun provideOkHttpClientBuilder(): OkHttpClient.Builder {
        return OkHttpClient.Builder()
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
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
    }

    @Provides
    @Singleton
    fun provideRetrofitBuilder(gson: Gson): Retrofit.Builder {
        return Retrofit.Builder()
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
    }

    @Provides
    @Singleton
    fun provideVastHttpClient(builder: OkHttpClient.Builder): OkHttpClient {
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideDynamicApiServiceFactory(
        retrofitBuilder: Retrofit.Builder,
        okHttpClientBuilder: OkHttpClient.Builder
    ): DynamicApiServiceFactory {
        return DynamicApiServiceFactory(retrofitBuilder, okHttpClientBuilder)
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