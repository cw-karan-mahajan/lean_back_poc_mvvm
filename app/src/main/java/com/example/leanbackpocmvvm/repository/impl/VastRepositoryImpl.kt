package com.example.leanbackpocmvvm.repository.impl

import android.content.Context
import android.util.Log
import com.example.leanbackpocmvvm.core.Resource
import com.example.leanbackpocmvvm.vastdata.parser.VastParser
import com.example.leanbackpocmvvm.vastdata.parser.VastParser.VastAd
import com.example.leanbackpocmvvm.repository.VastRepository
import com.example.leanbackpocmvvm.vastdata.tracking.AdEventTracker
import com.example.leanbackpocmvvm.utils.NetworkConnectivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VastRepositoryImpl @Inject constructor(
    private val vastParser: VastParser,
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivity,
    private val httpClient: OkHttpClient,
    private val adEventTracker: AdEventTracker
) : VastRepository {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ongoingOperations = ConcurrentHashMap<String, Job>()

    override suspend fun parseVastAd(vastUrl: String, tileId: String): Flow<Resource<VastAd>> = flow {
        if (!networkConnectivity.isConnected()) {
            emit(Resource.error<VastAd>("No internet connection"))
            return@flow
        }
        try {
            emit(Resource.loading<VastAd>())
            val operationKey = "parse_$tileId"
            val job = coroutineScope.launch {
                val vastAd = withTimeout(TIMEOUT_MS) {
                    vastParser.parseVastUrl(vastUrl, tileId)
                }
                if (vastAd != null) {
                    emit(Resource.success<VastAd>(vastAd))
                } else {
                    emit(Resource.error<VastAd>("Failed to parse VAST XML"))
                }
            }

            ongoingOperations[operationKey] = job
            job.join()
            ongoingOperations.remove(operationKey)

        } catch (e: TimeoutCancellationException) {
            emit(Resource.error<VastAd>("VAST parsing timeout"))
            Log.e(TAG, "VAST parsing timeout for tileId: $tileId", e)
        } catch (e: Exception) {
            emit(Resource.error<VastAd>("Error parsing VAST: ${e.message}"))
            Log.e(TAG, "Error parsing VAST for tileId: $tileId", e)
        }
    }.flowOn(Dispatchers.IO)
        .retryWhen { cause, attempt ->
            if (attempt < MAX_RETRY_ATTEMPTS && shouldRetry(cause)) {
                delay(RETRY_DELAY_MS * (attempt + 1))
                true
            } else false
        }

    override suspend fun trackAdEvent(url: String): Flow<Resource<Boolean>> = flow {
        if (!networkConnectivity.isConnected()) {
            emit(Resource.error<Boolean>("No internet connection"))
            return@flow
        }
        try {
            emit(Resource.loading<Boolean>())
            val request = Request.Builder()
                .url(url)
                .build()

            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        emit(Resource.success(true))
                    } else {
                        emit(Resource.error<Boolean>("Failed to track event: ${response.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            emit(Resource.error<Boolean>("Error tracking event: ${e.message}"))
            Log.e(TAG, "Error tracking event", e)
        }
    }.flowOn(Dispatchers.IO)
        .retryWhen { cause, attempt ->
            if (attempt < MAX_RETRY_ATTEMPTS && shouldRetry(cause)) {
                delay(RETRY_DELAY_MS * (attempt + 1))
                true
            } else false
        }

    override suspend fun preloadVastAd(vastUrl: String, tileId: String): Flow<Resource<VastAd>> = flow {
        if (!networkConnectivity.isConnected()) {
            emit(Resource.error<VastAd>("No internet connection"))
            return@flow
        }

        try {
            emit(Resource.loading<VastAd>())
            val operationKey = "preload_$tileId"
            val job = coroutineScope.launch {
                val vastAd = withTimeout(TIMEOUT_MS) {
                    vastParser.parseVastUrl(vastUrl, tileId)
                }

                if (vastAd != null) {
                    // Preload the first media file
                    vastAd.mediaFiles.firstOrNull()?.let { mediaFile ->
                        preloadMedia(mediaFile.url)
                    }
                    emit(Resource.success(vastAd))
                } else {
                    emit(Resource.error<VastAd>("Failed to preload VAST XML"))
                }
            }
            ongoingOperations[operationKey] = job
            job.join()
            ongoingOperations.remove(operationKey)

        } catch (e: Exception) {
            emit(Resource.error<VastAd>("Error preloading VAST: ${e.message}"))
            Log.e(TAG, "Error preloading VAST for tileId: $tileId", e)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun preloadMedia(url: String) {
        try {
            val request = Request.Builder()
                .url(url)
                .head()  // Only get headers to check availability
                .build()

            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Failed to preload media: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preloading media", e)
        }
    }

    override fun clearVastCache() {
        vastParser.clearCache()
    }

    override fun cancelOngoingOperations() {
        ongoingOperations.forEach { (_, job) -> job.cancel() }
        ongoingOperations.clear()
        coroutineScope.coroutineContext.cancelChildren()
    }

    private fun shouldRetry(cause: Throwable): Boolean {
        return when (cause) {
            is java.net.SocketTimeoutException,
            is java.io.IOException,
            is java.net.ConnectException -> true
            else -> false
        }
    }

    companion object {
        private const val TAG = "VastRepositoryImpl"
        private const val TIMEOUT_MS = 10000L // 10 seconds
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L // 1 second
    }
}