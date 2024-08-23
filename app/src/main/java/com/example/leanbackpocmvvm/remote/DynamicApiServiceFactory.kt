package com.example.leanbackpocmvvm.remote

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicApiServiceFactory @Inject constructor(
    private val retrofitBuilder: Retrofit.Builder,
    private val okHttpClientBuilder: OkHttpClient.Builder
) {
    private val cachedRetrofits = mutableMapOf<String, Retrofit>()

    fun <T> createService(serviceClass: Class<T>, baseUrl: String, headers: Map<String, String> = emptyMap()): T {
        val cacheKey = "$baseUrl:${headers.hashCode()}"

        val retrofit = cachedRetrofits.getOrPut(cacheKey) {
            Log.d("DynamicApiServiceFactory", "Creating new Retrofit instance for base URL: $baseUrl")
            val clientBuilder = okHttpClientBuilder
            if (headers.isNotEmpty()) {
                clientBuilder.addInterceptor { chain ->
                    val original = chain.request()
                    val requestBuilder = original.newBuilder()
                    headers.forEach { (key, value) ->
                        requestBuilder.header(key, value)
                    }
                    chain.proceed(requestBuilder.build())
                }
            }
            val client = clientBuilder.build()
            try {
                retrofitBuilder
                    .baseUrl(baseUrl)
                    .client(client)
                    .build()
            } catch (e: IllegalArgumentException) {
                Log.e("DynamicApiServiceFactory", "Error creating Retrofit instance: ${e.message}", e)
                throw e
            }
        }

        return retrofit.create(serviceClass)
    }
}