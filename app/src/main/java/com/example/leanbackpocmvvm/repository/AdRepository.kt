package com.example.leanbackpocmvvm.repository

import com.example.leanbackpocmvvm.core.Resource
import com.example.leanbackpocmvvm.models.AdResponse
import retrofit2.Response

interface AdRepository {
    suspend fun fetchAds(adUrls: List<String>): List<Pair<String, Resource<AdResponse>>>
    suspend fun trackImpressions(impressions: List<Pair<String, String>>): List<Pair<String, Resource<Unit>>>
    fun getImpressionTrackerUrls(adsServerUrl: String): List<String>
}