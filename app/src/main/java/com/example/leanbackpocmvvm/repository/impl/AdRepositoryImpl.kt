package com.example.leanbackpocmvvm.repository.impl

import android.util.Log
import com.example.leanbackpocmvvm.core.Resource
import com.example.leanbackpocmvvm.models.*
import com.example.leanbackpocmvvm.remote.AdApiService
import com.example.leanbackpocmvvm.remote.DynamicApiServiceFactory
import com.example.leanbackpocmvvm.repository.AdRepository
import com.example.leanbackpocmvvm.utils.NetworkConnectivity
import com.example.leanbackpocmvvm.utils.getResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdRepositoryImpl @Inject constructor(
    private val dynamicApiServiceFactory: DynamicApiServiceFactory,
    private val networkConnectivity: NetworkConnectivity,
    private val gson: Gson
) : AdRepository {

    private val maxConcurrentCalls = 20
    private val adDispatcher = Dispatchers.IO.limitedParallelism(maxConcurrentCalls)
    companion object {
        private const val DEFAULT_AD_SERVER_URL = "https://tiles.springserve.com/nv"
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
        return try {
            val baseUrl = extractBaseUrl(url)
            Log.d("AdRepositoryImpl", "Extracted base URL: $baseUrl for URL: $url")

            val adApiService = dynamicApiServiceFactory.createService(AdApiService::class.java, baseUrl)
            val response = adApiService.getAd(url)
            val adResponse = response.getResponse()

            if (adResponse != null && response.isSuccessful) {
                val parsedResponse = parseAdResponse(adResponse)
                url to Resource.success(parsedResponse)
            } else {
                url to Resource.error("Error fetching ad: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("AdRepositoryImpl", "Error fetching ad: ${e.message}", e)
            url to Resource.error("Something went wrong: ${e.message}")
        }
    }

    private fun extractBaseUrl(url: String): String {
        val urlPattern = "^(https?://[^/]+).*$".toRegex()
        val matchResult = urlPattern.find(url)
        return matchResult?.groupValues?.get(1) ?: run {
            Log.w("AdRepositoryImpl", "Failed to extract base URL from $url. Using default.")
            DEFAULT_AD_SERVER_URL
        }
    }

    private fun parseAdResponse(adResponse: AdResponse): AdResponse {
        val updatedSeatbid = adResponse.seatbid?.map { seatbid ->
            val updatedBids = seatbid.bid?.map { bid ->
                try {
                    val nativeAdWrapper = gson.fromJson(bid.adm, NativeAdWrapper::class.java)
                    val imageUrl = nativeAdWrapper.native?.assets?.firstOrNull()?.img?.url
                    bid.copy(parsedImageUrl = imageUrl)
                } catch (e: Exception) {
                    Log.e("AdRepositoryImpl", "Error parsing ad JSON: ${e.message}", e)
                    bid
                }
            }
            seatbid.copy(bid = updatedBids)
        }
        return adResponse.copy(seatbid = updatedSeatbid)
    }
}