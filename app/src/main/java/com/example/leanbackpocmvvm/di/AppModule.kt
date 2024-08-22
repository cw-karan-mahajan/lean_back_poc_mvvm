package com.example.leanbackpocmvvm.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.core.Constants.BASE_URL
import com.example.leanbackpocmvvm.remote.AdApiService
import com.example.leanbackpocmvvm.remote.ApiService
import com.example.leanbackpocmvvm.remote.HeaderInterceptor
import com.example.leanbackpocmvvm.repository.AdRepository
import com.example.leanbackpocmvvm.repository.MainRepository
import com.example.leanbackpocmvvm.repository.impl.AdRepositoryImpl
import com.example.leanbackpocmvvm.utils.Network
import com.example.leanbackpocmvvm.utils.NetworkConnectivity
import com.example.leanbackpocmvvm.views.exoplayer.ExoPlayerManager
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
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().setLenient().create()

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
    fun provideHeaderInterceptor(): HeaderInterceptor {
        return HeaderInterceptor()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(headerInterceptor: HeaderInterceptor): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .hostnameVerifier { _, _ -> true }
           // .addInterceptor(headerInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

//    @Provides
//    @Singleton
//    fun providesFetchApi(okHttpClient: OkHttpClient, gson: Gson): ApiService = Retrofit
//        .Builder()
//        .client(okHttpClient)
//        .baseUrl(BASE_URL)
//        .addConverterFactory(GsonConverterFactory.create(gson))
//        .build()
//        .create(ApiService::class.java)

    @UnstableApi
    @Provides
    @Singleton
    fun provideExoPlayerManager(
        @ApplicationContext context: Context
    ): ExoPlayerManager {
        return ExoPlayerManager(context)
    }

    @Provides
    @Singleton
    @Named("adRetrofit")
    fun provideAdRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://tiles.springserve.com/nv/")
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideAdApiService(@Named("adRetrofit") retrofit: Retrofit): AdApiService {
        return retrofit.create(AdApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAdRepository(
        adApiService: AdApiService,
        networkConnectivity: NetworkConnectivity,
        gson: Gson
    ): AdRepository {
        return AdRepositoryImpl(adApiService, networkConnectivity, gson)
    }
}