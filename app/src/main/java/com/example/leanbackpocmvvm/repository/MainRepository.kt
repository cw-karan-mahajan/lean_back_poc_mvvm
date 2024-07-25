package com.example.leanbackpocmvvm.repository

import com.example.leanbackpocmvvm.core.Resource
import com.example.leanbackpocmvvm.models.MyData2
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

@Singleton
interface MainRepository {
    fun fetchList(): Flow<Resource<MyData2>>
}