package com.example.leanbackpocmvvm.di

import android.content.Context
import com.example.leanbackpocmvvm.repository.MainRepository
import com.example.leanbackpocmvvm.utils.ExoPlayerManager
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

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
    fun provideExoPlayerManager(
        @ApplicationContext context: Context,
        coroutineScope: CoroutineScope
    ): ExoPlayerManager {
        return ExoPlayerManager(context, coroutineScope)
    }

    // For example, if using Retrofit for network calls:
    /*
    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }
    */
}