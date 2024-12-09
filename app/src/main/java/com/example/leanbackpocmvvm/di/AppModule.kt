package com.example.leanbackpocmvvm.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.core.Constants.BASE_URL
import com.example.leanbackpocmvvm.remote.ApiService
import com.example.leanbackpocmvvm.remote.DynamicApiServiceFactory
import com.example.leanbackpocmvvm.remote.HeaderInterceptor
import com.example.leanbackpocmvvm.repository.AdRepository
import com.example.leanbackpocmvvm.repository.MainRepository
import com.example.leanbackpocmvvm.repository.impl.AdRepositoryImpl
import com.example.leanbackpocmvvm.utils.Network
import com.example.leanbackpocmvvm.utils.NetworkConnectivity
import com.example.leanbackpocmvvm.vastdata.parser.VastAdSequenceManager
import com.example.leanbackpocmvvm.vastdata.tracking.VastTrackingManager
import com.example.leanbackpocmvvm.views.exoplayer.ExoPlayerManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder().setLenient().disableHtmlEscaping().serializeNulls().create()
    }

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideMainRepository(
        @ApplicationContext context: Context,
        gson: Gson
    ): MainRepository {
        return MainRepository(context, gson)
    }

    @Provides
    @Singleton
    fun provideNetworkConnectivity(@ApplicationContext context: Context): NetworkConnectivity {
        return Network(context)
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
    fun provideHeaderInterceptor(): HeaderInterceptor {
        return HeaderInterceptor()
    }

    @Provides
    @Singleton
    fun provideOkHttpClientBuilder(): OkHttpClient.Builder {
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
    fun provideApiService(retrofitBuilder: Retrofit.Builder, okHttpClientBuilder: OkHttpClient.Builder,
                          headerInterceptor: HeaderInterceptor): ApiService {
        val client = okHttpClientBuilder
            .addInterceptor(headerInterceptor)
            .build()
        return retrofitBuilder
            .baseUrl(BASE_URL)
            .client(client)
            .build()
            .create(ApiService::class.java)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    @Singleton
    fun provideAdRepository(
        dynamicApiServiceFactory: DynamicApiServiceFactory,
        networkConnectivity: NetworkConnectivity,
        gson: Gson,
        vastAdSequenceManager: VastAdSequenceManager
    ): AdRepository {
        return AdRepositoryImpl(dynamicApiServiceFactory, networkConnectivity, gson, vastAdSequenceManager)
    }

    @UnstableApi
    @Provides
    @Singleton
    fun provideExoPlayerManager(
        @ApplicationContext context: Context, vastTrackingManager: VastTrackingManager
    ): ExoPlayerManager {
        return ExoPlayerManager(context, vastTrackingManager)
    }
}