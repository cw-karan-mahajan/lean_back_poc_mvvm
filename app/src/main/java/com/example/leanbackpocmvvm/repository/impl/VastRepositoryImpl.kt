package com.example.leanbackpocmvvm.repository.impl

import android.content.Context
import com.example.leanbackpocmvvm.core.Resource
import com.example.leanbackpocmvvm.vastdata.network.DynamicApiServiceFactory
import com.example.leanbackpocmvvm.remote.VastApiService
import com.example.leanbackpocmvvm.vastdata.parser.VastParser
import com.example.leanbackpocmvvm.repository.VastRepository
import com.example.leanbackpocmvvm.vastdata.tracking.AdEventTracker
import com.example.leanbackpocmvvm.vastdata.network.NetworkConnectivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import timber.log.Timber
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

            Timber.d(TAG, "Fetching VAST XML from URL: $vastUrl")
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

            Timber.d(TAG, "Successfully received VAST XML, now parsing...")

            vastParser.parseVastUrl(vastUrl, tileId)
                .fold(
                    onSuccess = { vastAds ->
                        if (vastAds.isNotEmpty()) {
                            Timber.d(TAG, "Successfully parsed ${vastAds.size} VAST ads")
                            vastAds.forEachIndexed { index, vastAd ->
                                Timber.d(TAG, "Processing Ad ${index + 1} of ${vastAds.size}")
                                logVastAdDetails(vastAd)
                            }
                            send(Resource.success(vastAds))
                        } else {
                            send(Resource.error("No valid VAST ads found"))
                        }
                    },
                    onFailure = { error ->
                        send(Resource.error("Error parsing VAST: ${error.message}"))
                    }
                )

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
                        send(Resource.success<Boolean>(true))
                    } else {
                        send(Resource.error<Boolean>("Failed to track event: ${response.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            send(Resource.error<Boolean>("Error tracking event: ${e.message}"))
            Timber.e(TAG, "Error tracking event", e)
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

            val vastApiService = dynamicApiServiceFactory.createService(VastApiService::class.java, vastUrl)
            val path = dynamicApiServiceFactory.extractPath(vastUrl)
            val queryParams = dynamicApiServiceFactory.extractQueryParams(vastUrl)

            val response = vastApiService.getVastXml(path, queryParams)
            if (!response.isSuccessful) {
                send(Resource.error("Failed to fetch VAST XML for preload"))
                return@channelFlow
            }

            vastParser.parseVastUrl(vastUrl, tileId)
                .fold(
                    onSuccess = { vastAds ->
                        if (vastAds.isNotEmpty()) {
                            vastAds.forEach { vastAd ->
                                vastAd.mediaFiles.firstOrNull()?.let { mediaFile ->
                                    preloadMedia(mediaFile.url)
                                }
                            }
                            send(Resource.success(vastAds))
                        } else {
                            send(Resource.error("No valid VAST ads found for preload"))
                        }
                    },
                    onFailure = { error ->
                        send(Resource.error("Error parsing VAST for preload: ${error.message}"))
                    }
                )

        } catch (e: Exception) {
            Timber.e(TAG, "Error preloading VAST for tileId: $tileId", e)
            send(Resource.error("Error preloading VAST: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun preloadMedia(url: String) {
        try {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()

            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w(TAG, "Failed to preload media: ${response.code}")
                    } else {
                        Timber.d(TAG, "Successfully preloaded media: $url")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(TAG, "Error preloading media", e)
        }
    }

    private fun logVastAdDetails(vastAd: VastParser.VastAd) {
        Timber.d(TAG, "========== VAST Ad Details ==========")
        Timber.d(TAG, "Ad ID: ${vastAd.id}")
        Timber.d(TAG, "Sequence: ${vastAd.sequence}")
        Timber.d(TAG, "Ad System: ${vastAd.adSystem}")
        Timber.d(TAG, "Ad Title: ${vastAd.adTitle}")
        Timber.d(TAG, "Duration: ${vastAd.duration}")

        Timber.d(TAG, "------- MediaFiles (${vastAd.mediaFiles.size}) -------")
        vastAd.mediaFiles.forEach { mediaFile ->
            Timber.d(TAG, """
                MediaFile:
                - URL: ${mediaFile.url}
                - Bitrate: ${mediaFile.bitrate}
                - Resolution: ${mediaFile.width}x${mediaFile.height}
                - Type: ${mediaFile.type}
                - Delivery: ${mediaFile.delivery}
            """.trimIndent())
        }

        Timber.d(TAG, "------- Tracking Events -------")
        vastAd.trackingEvents.forEach { (event, url) ->
            Timber.d(TAG, "Event: $event -> URL: $url")
        }

        Timber.d(TAG, "------- Click Information -------")
        Timber.d(TAG, "ClickThrough: ${vastAd.clickThrough}")
        Timber.d(TAG, "ClickTracking: ${vastAd.clickTracking}")

        if (vastAd.extensions.isNotEmpty()) {
            Timber.d(TAG, "------- Extensions -------")
            vastAd.extensions.forEach { (key, value) ->
                Timber.d(TAG, "$key: $value")
            }
        }
        Timber.d(TAG, "===================================")
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
        private const val TIMEOUT_MS = 10000L
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
    }
}