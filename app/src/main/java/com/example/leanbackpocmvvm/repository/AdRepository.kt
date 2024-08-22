package com.example.leanbackpocmvvm.repository

import com.example.leanbackpocmvvm.core.Resource
import com.example.leanbackpocmvvm.models.AdResponse

interface AdRepository {
    suspend fun fetchAds(adUrls: List<String>): List<Pair<String, Resource<AdResponse>>>
}