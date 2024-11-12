package com.example.leanbackpocmvvm.remote

import com.example.leanbackpocmvvm.models.AdResponse
import com.example.leanbackpocmvvm.models.MyData2
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.QueryMap
import retrofit2.http.Url

interface ApiService {

    @GET("cats/")
    suspend fun fetchList(): Response<MyData2?>
}

interface AdApiService {
    @GET
    suspend fun getAd(
        @Url path: String,
        @QueryMap queryParams: Map<String, String>
    ): Response<AdResponse?>

    @GET
    suspend fun trackImpression(
        @Url path: String,
        @QueryMap queryParams: Map<String, String>
    ): Response<Unit>
}

interface VastApiService {
    @GET
    suspend fun getVastXml(
        @Url path: String,
        @QueryMap queryParams: Map<String, String> = emptyMap()
    ): Response<String>
}