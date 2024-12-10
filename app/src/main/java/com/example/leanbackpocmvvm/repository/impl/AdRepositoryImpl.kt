package com.example.leanbackpocmvvm.repository.impl

import android.util.Log
import com.example.leanbackpocmvvm.core.Resource
import com.example.leanbackpocmvvm.models.*
import com.example.leanbackpocmvvm.remote.AdApiService
import com.example.leanbackpocmvvm.remote.DynamicApiServiceFactory
import com.example.leanbackpocmvvm.repository.AdRepository
import com.example.leanbackpocmvvm.utils.NetworkConnectivity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@ExperimentalCoroutinesApi
@Singleton
class AdRepositoryImpl @Inject constructor(
    private val dynamicApiServiceFactory: DynamicApiServiceFactory,
    private val networkConnectivity: NetworkConnectivity,
    private val gson: Gson
) : AdRepository {

    private val maxConcurrentCalls = 20
    private val adDispatcher = Dispatchers.IO.limitedParallelism(maxConcurrentCalls)
    private val impressionUrls = Collections.synchronizedMap(mutableMapOf<String, List<String>>())
    private val adIdMap = ConcurrentHashMap<String, String>()

    companion object {
        private const val TAG = "AdRepositoryImpl"
    }

    override suspend fun fetchAds(adUrls: List<String>): List<Pair<String, Resource<AdResponse>>> = withContext(Dispatchers.Default) {
        if (!networkConnectivity.isConnected()) {
            return@withContext adUrls.map { it to Resource.error("No internet connection") }
        }

        adUrls.map { url ->
            async(adDispatcher) {
                fetchSingleAd(url)
            }
        }.awaitAll()
    }

    private suspend fun fetchSingleAd(url: String): Pair<String, Resource<AdResponse>> {
        try {
            val adApiService = dynamicApiServiceFactory.createService(AdApiService::class.java, url)
            val path = dynamicApiServiceFactory.extractPath(url)
            val queryParams = dynamicApiServiceFactory.extractQueryParams(url)

            val response = adApiService.getAd(path, queryParams)
            if (response.isSuccessful) {
                try {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        val rawString = responseBody.string()
                        val adResponse = gson.fromJson(rawString, AdResponse::class.java)

                        // Store adid from response
                        adResponse.seatbid?.firstOrNull()?.bid?.firstOrNull()?.adid?.let { adid ->
                            Log.d(TAG, "Extracted adid: $adid")
                        }

                        val parsedResponse = parseAdResponse(adResponse, url)
                        return url to Resource.success(parsedResponse)
                    }
                    return url to Resource.error("Empty response body")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing response: ${e.message}")
                    return url to Resource.error("Error parsing response: ${e.message}")
                }
            }
            return url to Resource.error("Error fetching ad: ${response.code()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching ad: ${e.message}", e)
            return url to Resource.error("Something went wrong: ${e.message}")
        }
    }

    private fun parseAdResponse(adResponse: AdResponse, url: String): AdResponse {
        val updatedSeatbid = adResponse.seatbid?.map { seatbid ->
            val updatedBids = seatbid.bid?.map { bid ->
                try {
                    Log.d(TAG, "Original adm content: ${bid.adm}")

                    // Remove any surrounding quotes and unescape the JSON string
                    val cleanAdm = bid.adm.trim().let { adm ->
                        if (adm.startsWith("\"") && adm.endsWith("\"")) {
                            adm.substring(1, adm.length - 1)
                        } else {
                            adm
                        }}.replace("\\\"", "\"")

                    val nativeAdWrapper = gson.fromJson(cleanAdm, NativeAdWrapper::class.java)
                    val imageUrl = nativeAdWrapper.native?.assets?.firstOrNull()?.img?.url
                    val impTrackerUrls = nativeAdWrapper.native?.imptrackers ?: emptyList()

                    Log.d(TAG, "Parsed Image URL: $imageUrl")

                    if (impTrackerUrls.isNotEmpty()) {
                        impressionUrls[url] = impTrackerUrls
                    }
                    bid.copy(parsedImageUrl = imageUrl)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing bid JSON: ${e.message}", e)
                    Log.e(TAG, "Failed adm content: ${bid.adm}")
                    e.printStackTrace()
                    bid
                }
            }
            seatbid.copy(bid = updatedBids)
        }
        return adResponse.copy(seatbid = updatedSeatbid)
    }

    override suspend fun trackImpressions(impressions: List<Pair<String, String>>): List<Pair<String, Resource<Unit>>> = withContext(Dispatchers.Default) {
        if (!networkConnectivity.isConnected()) {
            return@withContext impressions.map { it.first to Resource.error("No internet connection") }
        }

        impressions.map { (tileId, impUrl) ->
            async(adDispatcher) {
                tileId to trackSingleImpression(impUrl)
            }
        }.awaitAll()
    }

    private suspend fun trackSingleImpression(impUrl: String): Resource<Unit> {
        return try {
            val adApiService = dynamicApiServiceFactory.createService(AdApiService::class.java, impUrl)
            val path = dynamicApiServiceFactory.extractPath(impUrl)
            val queryParams = dynamicApiServiceFactory.extractQueryParams(impUrl)

            val response = adApiService.trackImpression(path, queryParams)
            if (response.isSuccessful) {
                Resource.success(Unit)
            } else {
                Resource.error("Error tracking impression: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking impression: ${e.message}", e)
            Resource.error("Something went wrong: ${e.message}")
        }
    }

    override fun getImpressionTrackerUrls(adsServerUrl: String): List<String> {
        return impressionUrls[adsServerUrl] ?: emptyList()
    }

   override fun processVideoUrl(tileId: String, adsVideoUrl: String): String {
        return adIdMap[tileId]?.let { adid ->
            Log.d(TAG, "Processing URL for tileId: $tileId with adid: $adid")
            adsVideoUrl.replace("[ADID]", adid)
        } ?: adsVideoUrl
    }

    override fun storeAdId(tileId: String, adid: String) {
        adIdMap[tileId] = adid
        Log.d(TAG, "Stored adid: $adid for tileId: $tileId")
    }
}