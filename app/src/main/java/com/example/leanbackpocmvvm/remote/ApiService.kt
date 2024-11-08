package com.example.leanbackpocmvvm.remote

import retrofit2.Response
import retrofit2.http.GET

interface ApiService {

    @GET("cats/")
    suspend fun fetchList() : Response<MyData2?>
}