package com.example.leanbackpocmvvm.repository.impl

import com.example.leanbackpocmvvm.core.Resource
import com.example.leanbackpocmvvm.models.MyData2
import com.example.leanbackpocmvvm.remote.ApiService
import com.example.leanbackpocmvvm.remote.DynamicApiServiceFactory
import com.example.leanbackpocmvvm.repository.MainRepository1
import com.example.leanbackpocmvvm.utils.NetworkConnectivity
import com.example.leanbackpocmvvm.utils.getResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton


@ExperimentalCoroutinesApi
@Singleton
class MainRepositoryImpl @Inject constructor(
    private val dynamicApiServiceFactory: DynamicApiServiceFactory,
    private val networkConnectivity: NetworkConnectivity
) : MainRepository1 {

    override fun fetchList(baseUrl: String, headers: Map<String, String>): Flow<Resource<MyData2>> = flow {
        if (!networkConnectivity.isConnected()) {
            emit(Resource.error("You are offline. Connect to the Internet to access the app"))
            return@flow
        } else {
            val apiService = dynamicApiServiceFactory.createService(ApiService::class.java, baseUrl, headers)
            val response = apiService.fetchList()
            val code = response.code()
            val authServiceResponse = response.getResponse()
            val state =
                if (authServiceResponse != null && code == 200) Resource.success(authServiceResponse) else Resource.error(
                    "Something went wrong"
                )
            emit(state)
        }
    }.catch { e -> emit(Resource.error("Something went wrong $e")) }
}