package tv.cloudwalker.adtech.vastdata.repository

import tv.cloudwalker.adtech.vastdata.parser.VastParser
import kotlinx.coroutines.flow.Flow
import tv.cloudwalker.adtech.vastdata.network.Resource

interface VastRepository {
    suspend fun parseVastAd(vastUrl: String, tileId: String): Flow<Resource<List<VastParser.VastAd>>>
    suspend fun trackAdEvent(url: String): Flow<Resource<Boolean>>
    suspend fun preloadVastAd(vastUrl: String, tileId: String): Flow<Resource<List<VastParser.VastAd>>>
    fun clearVastCache()
    fun cancelOngoingOperations()
}