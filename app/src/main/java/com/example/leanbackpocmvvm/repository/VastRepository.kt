package com.example.leanbackpocmvvm.repository

import com.example.leanbackpocmvvm.core.Resource
import com.example.leanbackpocmvvm.vastdata.parser.VastParser
import kotlinx.coroutines.flow.Flow

interface VastRepository {
    suspend fun parseVastAd(vastUrl: String, tileId: String): Flow<Resource<List<VastParser.VastAd>>>
    suspend fun trackAdEvent(url: String): Flow<Resource<Boolean>>
    suspend fun preloadVastAd(vastUrl: String, tileId: String): Flow<Resource<List<VastParser.VastAd>>>
    fun clearVastCache()
    fun cancelOngoingOperations()
}