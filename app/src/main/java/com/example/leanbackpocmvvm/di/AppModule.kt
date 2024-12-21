package com.example.leanbackpocmvvm.di

import android.content.Context
import com.example.leanbackpocmvvm.core.Constants.BASE_URL
import com.example.leanbackpocmvvm.remote.ApiService
import com.example.leanbackpocmvvm.vastdata.network.DynamicApiServiceFactory
import com.example.leanbackpocmvvm.remote.HeaderInterceptor
import com.example.leanbackpocmvvm.repository.AdRepository
import com.example.leanbackpocmvvm.repository.MainRepository
import com.example.leanbackpocmvvm.repository.impl.AdRepositoryImpl
import com.example.leanbackpocmvvm.utils.Network
import com.example.leanbackpocmvvm.vastdata.network.NetworkConnectivity
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
    fun provideApiService(
        retrofitBuilder: Retrofit.Builder,
        okHttpClientBuilder: OkHttpClient.Builder,
        headerInterceptor: HeaderInterceptor
    ): ApiService {
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
        gson: Gson
    ): AdRepository {
        return AdRepositoryImpl(dynamicApiServiceFactory, networkConnectivity, gson)
    }
}