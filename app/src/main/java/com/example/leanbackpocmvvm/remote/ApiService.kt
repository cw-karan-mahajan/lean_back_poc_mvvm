package com.example.leanbackpocmvvm.remote

import com.example.leanbackpocmvvm.models.AdResponse
import com.example.leanbackpocmvvm.models.MyData2
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface ApiService {

    @GET("cats/")
    suspend fun fetchList() : Response<MyData2?>
}

interface AdApiService {
    @GET
    suspend fun getAd(@Url url: String): Response<AdResponse?>

}