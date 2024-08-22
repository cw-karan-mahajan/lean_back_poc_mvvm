package com.example.leanbackpocmvvm.di

import com.example.leanbackpocmvvm.repository.AdRepository
import com.example.leanbackpocmvvm.repository.MainRepository
import com.example.leanbackpocmvvm.repository.MainRepository1
import com.example.leanbackpocmvvm.repository.impl.AdRepositoryImpl
import com.example.leanbackpocmvvm.repository.impl.MainRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {

    @Binds
    fun providesAuthRepository(authRepositoryImpl: MainRepositoryImpl): MainRepository1

//    @Binds
//    fun provideAdRepository(adRepositoryImpl: AdRepositoryImpl): AdRepository


}
