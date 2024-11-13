package com.example.leanbackpocmvvm.repository.impl

import android.content.Context
import android.util.Log
import com.example.leanbackpocmvvm.core.Resource
import com.example.leanbackpocmvvm.remote.DynamicApiServiceFactory
import com.example.leanbackpocmvvm.remote.VastApiService
import com.example.leanbackpocmvvm.vastdata.parser.VastParser
import com.example.leanbackpocmvvm.repository.VastRepository
import com.example.leanbackpocmvvm.vastdata.tracking.AdEventTracker
import com.example.leanbackpocmvvm.utils.NetworkConnectivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VastRepositoryImpl @Inject constructor(
    private val vastParser: VastParser,
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivity,
    private val httpClient: OkHttpClient,
    private val adEventTracker: AdEventTracker,
    private val dynamicApiServiceFactory: DynamicApiServiceFactory
) : VastRepository {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ongoingOperations = ConcurrentHashMap<String, Job>()

    override suspend fun parseVastAd(vastUrl: String, tileId: String): Flow<Resource<List<VastParser.VastAd>>> = channelFlow {
        if (!networkConnectivity.isConnected()) {
            send(Resource.error("No internet connection"))
            return@channelFlow
        }

        try {
            send(Resource.loading())

            // Make API call to get VAST XML
            Log.d(TAG, "Fetching VAST XML from URL: $vastUrl")
            val vastApiService = dynamicApiServiceFactory.createService(VastApiService::class.java, vastUrl)
            val path = dynamicApiServiceFactory.extractPath(vastUrl)
            val queryParams = dynamicApiServiceFactory.extractQueryParams(vastUrl)

            val response = vastApiService.getVastXml(path, queryParams)
            if (!response.isSuccessful) {
                send(Resource.error("Failed to fetch VAST XML: ${response.code()}"))
                return@channelFlow
            }

            val xmlString = response.body()
            if (xmlString == null) {
                send(Resource.error("Empty VAST XML response"))
                return@channelFlow
            }

            Log.d(TAG, "Successfully received VAST XML, now parsing...")

            // Parse XML using existing vastParser
            val vastAds = withTimeout(TIMEOUT_MS) {
                vastParser.parseVastUrl(vastUrl, tileId)
            }

            if (!vastAds.isNullOrEmpty()) {
                Log.d(TAG, "Successfully parsed ${vastAds.size} VAST ads")
                vastAds.forEachIndexed { index, vastAd ->
                    Log.d(TAG, "Processing Ad ${index + 1} of ${vastAds.size}")
                    logVastAdDetails(vastAd)
                }
                send(Resource.success(vastAds))
            } else {
                send(Resource.error("Failed to parse VAST XML"))
            }

        } catch (e: TimeoutCancellationException) {
            send(Resource.error("VAST parsing timeout"))
        } catch (e: Exception) {
            send(Resource.error("Error parsing VAST: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
        .retryWhen { cause, attempt ->
            if (attempt < MAX_RETRY_ATTEMPTS && shouldRetry(cause)) {
                delay(RETRY_DELAY_MS * (attempt + 1))
                true
            } else false
        }

    override suspend fun trackAdEvent(url: String): Flow<Resource<Boolean>> = channelFlow {
        if (!networkConnectivity.isConnected()) {
            send(Resource.error<Boolean>("No internet connection"))
            return@channelFlow
        }

        try {
            send(Resource.loading<Boolean>())
            val request = Request.Builder()
                .url(url)
                .build()

            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        send(Resource.success(true))
                    } else {
                        send(Resource.error<Boolean>("Failed to track event: ${response.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            send(Resource.error<Boolean>("Error tracking event: ${e.message}"))
            Log.e(TAG, "Error tracking event", e)
        }
    }.flowOn(Dispatchers.IO)
        .retryWhen { cause, attempt ->
            if (attempt < MAX_RETRY_ATTEMPTS && shouldRetry(cause)) {
                delay(RETRY_DELAY_MS * (attempt + 1))
                true
            } else false
        }

    override suspend fun preloadVastAd(vastUrl: String, tileId: String): Flow<Resource<List<VastParser.VastAd>>> = channelFlow {
        if (!networkConnectivity.isConnected()) {
            send(Resource.error("No internet connection"))
            return@channelFlow
        }

        try {
            send(Resource.loading())

            // First fetch VAST XML
            val vastApiService = dynamicApiServiceFactory.createService(VastApiService::class.java, vastUrl)
            val path = dynamicApiServiceFactory.extractPath(vastUrl)
            val queryParams = dynamicApiServiceFactory.extractQueryParams(vastUrl)

            val response = vastApiService.getVastXml(path, queryParams)
            if (!response.isSuccessful) {
                send(Resource.error("Failed to fetch VAST XML for preload"))
                return@channelFlow
            }

            // Then parse and preload
            val vastAds = withTimeout(TIMEOUT_MS) {
                vastParser.parseVastUrl(vastUrl, tileId)
            }

            if (!vastAds.isNullOrEmpty()) {
                // Preload the first media file of each ad
                vastAds.forEach { vastAd ->
                    vastAd.mediaFiles.firstOrNull()?.let { mediaFile ->
                        preloadMedia(mediaFile.url)
                    }
                }
                send(Resource.success(vastAds))
            } else {
                send(Resource.error("Failed to preload VAST XML"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error preloading VAST for tileId: $tileId", e)
            send(Resource.error("Error preloading VAST: ${e.message}"))
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
                    } else {
                        Log.d(TAG, "Successfully preloaded media: $url")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preloading media", e)
        }
    }

    private fun logVastAdDetails(vastAd: VastParser.VastAd) {
        Log.d(TAG, "========== VAST Ad Details ==========")
        Log.d(TAG, "Ad ID: ${vastAd.id}")
        Log.d(TAG, "Sequence: ${vastAd.sequence}")
        Log.d(TAG, "Ad System: ${vastAd.adSystem}")
        Log.d(TAG, "Ad Title: ${vastAd.adTitle}")
        Log.d(TAG, "Duration: ${vastAd.duration}")

        // Log MediaFiles
        Log.d(TAG, "------- MediaFiles (${vastAd.mediaFiles.size}) -------")
        vastAd.mediaFiles.forEach { mediaFile ->
            Log.d(TAG, """
                MediaFile:
                - URL: ${mediaFile.url}
                - Bitrate: ${mediaFile.bitrate}
                - Resolution: ${mediaFile.width}x${mediaFile.height}
                - Type: ${mediaFile.type}
                - Delivery: ${mediaFile.delivery}
            """.trimIndent())
        }

        // Log Tracking Events
        Log.d(TAG, "------- Tracking Events -------")
        vastAd.trackingEvents.forEach { (event, url) ->
            Log.d(TAG, "Event: $event -> URL: $url")
        }

        // Log Click Information
        Log.d(TAG, "------- Click Information -------")
        Log.d(TAG, "ClickThrough: ${vastAd.clickThrough}")
        Log.d(TAG, "ClickTracking: ${vastAd.clickTracking}")

        // Log Extensions if any
        if (vastAd.extensions.isNotEmpty()) {
            Log.d(TAG, "------- Extensions -------")
            vastAd.extensions.forEach { (key, value) ->
                Log.d(TAG, "$key: $value")
            }
        }

        Log.d(TAG, "===================================")
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
            is SocketTimeoutException,
            is IOException,
            is ConnectException -> true
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

