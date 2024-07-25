package com.example.leanbackpocmvvm.remote

import com.example.leanbackpocmvvm.models.MyData2
import retrofit2.Response
import retrofit2.http.GET

interface ApiService {

    @GET("cats/")
    suspend fun fetchList() : Response<MyData2?>
}