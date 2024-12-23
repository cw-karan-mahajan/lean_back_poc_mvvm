package tv.cloudwalker.adtech.vastdata.network

import android.net.Uri
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicApiServiceFactory @Inject constructor(
    private val retrofitBuilder: Retrofit.Builder,
    private val okHttpClientBuilder: OkHttpClient.Builder
) {
    private val cachedRetrofits = mutableMapOf<String, Retrofit>()

    fun <T> createService(serviceClass: Class<T>, url: String, headers: Map<String, String> = emptyMap()): T {
        val baseUrl = extractBaseUrl(url)
        val cacheKey = "$baseUrl:${headers.hashCode()}"

        val retrofit = cachedRetrofits.getOrPut(cacheKey) {
            Timber.d("DynamicApiService", "Creating new Retrofit instance for base URL: $baseUrl")
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
                Timber.e("DynamicApiService", "Error creating Retrofit instance: ${e.message}", e)
                throw e
            }
        }

        return retrofit.create(serviceClass)
    }

    private fun extractBaseUrl(url: String): String {
        val uri = Uri.parse(url)
        return "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}/"
    }

    fun extractPath(url: String): String {
        val uri = Uri.parse(url)
        return uri.path ?: ""
    }

    fun extractQueryParams(url: String): Map<String, String> {
        val uri = Uri.parse(url)
        return uri.queryParameterNames.associateWith { uri.getQueryParameter(it) ?: "" }
    }
}