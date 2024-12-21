package com.example.leanbackpocmvvm.repository.impl

import com.example.leanbackpocmvvm.core.Resource
import com.example.leanbackpocmvvm.models.MyData2
import com.example.leanbackpocmvvm.remote.ApiService
import com.example.leanbackpocmvvm.repository.MainRepository1
import com.example.leanbackpocmvvm.utils.getResponse
import com.example.leanbackpocmvvm.vastdata.network.NetworkConnectivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton


@ExperimentalCoroutinesApi
@Singleton
class MainRepositoryImpl @Inject internal constructor(
    private val authService: ApiService,
    private val networkConnectivity: NetworkConnectivity
) : MainRepository1 {

    override fun fetchList(): Flow<Resource<MyData2>> = flow {
        if (!networkConnectivity.isConnected()) {
            emit(Resource.error("You are offline. Connect to the Internet to access the app"))
            return@flow
        } else {
            val code = authService.fetchList().code()
            val authServiceResponse = authService.fetchList().getResponse()
            val state =
                if (authServiceResponse != null && code == 200) Resource.success(authServiceResponse) else Resource.error(
                    "Something went wrong"
                )
            emit(state)
        }
    }.catch { e -> emit(Resource.error("Something went wrong $e")) }


    //.flowOn(Dispatchers.IO)

    //.catch { e -> emit(Resource.error("Something went wrong $e")) }
}