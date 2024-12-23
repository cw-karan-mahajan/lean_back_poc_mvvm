package com.example.leanbackpocmvvm.repository

import tv.cloudwalker.adtech.vastdata.network.Resource
import com.example.leanbackpocmvvm.models.AdResponse

interface AdRepository {
    suspend fun fetchAds(adUrls: List<String>): List<Pair<String, Resource<AdResponse>>>
    suspend fun trackImpressions(impressions: List<Pair<String, String>>): List<Pair<String, Resource<Unit>>>
    fun getImpressionTrackerUrls(adsServerUrl: String): List<String>

    fun processVideoUrl(tileId: String, adsVideoUrl: String): String
    fun storeAdId(tileId: String, adid: String)
}