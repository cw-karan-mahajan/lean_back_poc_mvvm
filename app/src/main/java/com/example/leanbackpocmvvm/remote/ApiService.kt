package com.example.leanbackpocmvvm.remote

import com.example.leanbackpocmvvm.models.MyData2
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.QueryMap
import retrofit2.http.Url

interface ApiService {

    @GET("cats")
    suspend fun fetchList(): Response<MyData2?>
}

interface AdApiService {
    @Headers("Accept-Encoding: identity", "Accept: application/json")
    @GET
    suspend fun getAd(
        @Url path: String,
        @QueryMap queryParams: Map<String, String>
    ): Response<ResponseBody?>

    @GET
    suspend fun trackImpression(
        @Url path: String,
        @QueryMap queryParams: Map<String, String>
    ): Response<Unit>
}

