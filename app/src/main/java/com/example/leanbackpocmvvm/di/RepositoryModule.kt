package com.example.leanbackpocmvvm.di

import com.example.leanbackpocmvvm.repository.MainRepository1
import com.example.leanbackpocmvvm.repository.impl.MainRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {

    @Binds
    fun providesAuthRepository(authRepositoryImpl: MainRepositoryImpl): MainRepository1

    //@Binds
    //fun provideAdRepository(adRepositoryImpl: AdRepositoryImpl): AdRepository
}