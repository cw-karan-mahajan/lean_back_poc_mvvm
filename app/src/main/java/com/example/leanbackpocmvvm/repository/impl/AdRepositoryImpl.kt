package com.example.leanbackpocmvvm.repository.impl

import com.example.leanbackpocmvvm.core.Resource
import com.example.leanbackpocmvvm.models.*
import com.example.leanbackpocmvvm.remote.AdApiService
import com.example.leanbackpocmvvm.repository.AdRepository
import com.example.leanbackpocmvvm.utils.NetworkConnectivity
import com.example.leanbackpocmvvm.utils.getResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AdRepositoryImpl @Inject constructor(
    private val adApiService: AdApiService,
    private val networkConnectivity: NetworkConnectivity,
    private val gson: Gson
) : AdRepository {

    private val maxConcurrentCalls = 20
    private val adDispatcher = Dispatchers.IO.limitedParallelism(maxConcurrentCalls)

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
            val response = adApiService.getAd(url)
            val adResponse = response.getResponse()

            if (adResponse != null && response.isSuccessful) {
                val parsedResponse = parseAdResponse(adResponse)
                url to Resource.success(parsedResponse)
            } else {
                url to Resource.error("Error fetching ad: ${response.code()}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            url to Resource.error("Something went wrong: ${e.message}")
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
                    println("Error parsing ad JSON: ${e.message}")
                    bid
                }
            }
            seatbid.copy(bid = updatedBids)
        }
        return adResponse.copy(seatbid = updatedSeatbid)
    }
}