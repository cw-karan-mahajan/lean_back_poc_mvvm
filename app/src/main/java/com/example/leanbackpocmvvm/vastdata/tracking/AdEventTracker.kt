package com.example.leanbackpocmvvm.vastdata.tracking

import android.util.Log
import com.example.leanbackpocmvvm.vastdata.parser.VastParser.VastAd
import com.example.leanbackpocmvvm.utils.NetworkConnectivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdEventTracker @Inject constructor(
    private val httpClient: OkHttpClient,
    private val networkConnectivity: NetworkConnectivity,
    private val retryAttempts: Int = 3,
    private val retryDelayMs: Long = 1000L
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val trackingJobs = ConcurrentHashMap<String, Job>()

    fun trackImpression(vastAd: VastAd) {
        fireTrackingPixel(vastAd.impression, "impression_${vastAd.id}")
    }

    fun trackEvent(url: String, eventType: String, vastAdId: String) {
        fireTrackingPixel(url, "${eventType}_${vastAdId}")
    }

    fun trackQuartile(vastAd: VastAd, quartileEvent: String) {
        vastAd.trackingEvents[quartileEvent]?.let { url ->
            fireTrackingPixel(url, "${quartileEvent}_${vastAd.id}")
        }
    }

    fun trackClick(vastAd: VastAd) {
        vastAd.clickTracking?.let { url ->
            fireTrackingPixel(url, "click_${vastAd.id}")
        }
    }

    private fun fireTrackingPixel(url: String, jobKey: String) {
        // Cancel any existing tracking job for this event
        trackingJobs[jobKey]?.cancel()

        trackingJobs[jobKey] = scope.launch {
            var currentAttempt = 0
            var lastError: Exception? = null

            while (currentAttempt < retryAttempts) {
                try {
                    if (!networkConnectivity.isConnected()) {
                        Log.w(TAG, "No network connection, delaying tracking attempt")
                        delay(retryDelayMs)
                        currentAttempt++
                        continue
                    }

                    val request = Request.Builder()
                        .url(url)
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.d(TAG, "Successfully tracked event: $jobKey")
                            return@launch
                        } else {
                            throw Exception("Failed to track event: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    lastError = e
                    Log.e(TAG, "Error tracking event ($currentAttempt/$retryAttempts): ${e.message}")
                    if (currentAttempt < retryAttempts - 1) {
                        delay(retryDelayMs * (currentAttempt + 1)) // Exponential backoff
                    }
                    currentAttempt++
                }
            }

            lastError?.let {
                Log.e(TAG, "Failed to track event after $retryAttempts attempts: ${it.message}")
            }
        }.also { job ->
            job.invokeOnCompletion { throwable ->
                trackingJobs.remove(jobKey)
                throwable?.let {
                    Log.e(TAG, "Tracking job completed with error: ${it.message}")
                }
            }
        }
    }

    fun trackAllQuartiles(vastAd: VastAd, position: Long, duration: Long) {
        val percentage = if (duration > 0) (position * 100) / duration else 0

        when {
            percentage >= 25 && percentage < 50 ->
                trackQuartile(vastAd, VastAd.EVENT_FIRST_QUARTILE)
            percentage >= 50 && percentage < 75 ->
                trackQuartile(vastAd, VastAd.EVENT_MIDPOINT)
            percentage >= 75 ->
                trackQuartile(vastAd, VastAd.EVENT_THIRD_QUARTILE)
        }
    }

    fun startTracking(vastAd: VastAd) {
        trackImpression(vastAd)
        trackEvent(
            vastAd.trackingEvents[VastAd.EVENT_START] ?: return,
            VastAd.EVENT_START,
            vastAd.id
        )
    }

    fun completeTracking(vastAd: VastAd) {
        vastAd.trackingEvents[VastAd.EVENT_COMPLETE]?.let { url ->
            trackEvent(url, VastAd.EVENT_COMPLETE, vastAd.id)
        }
    }

    fun cancelAllTracking() {
        trackingJobs.forEach { (_, job) -> job.cancel() }
        trackingJobs.clear()
    }

    fun cancelTracking(vastAdId: String) {
        trackingJobs.entries.removeIf { (key, job) ->
            if (key.endsWith(vastAdId)) {
                job.cancel()
                true
            } else false
        }
    }

    private suspend fun retryWithBackoff(
        times: Int = retryAttempts,
        initialDelayMs: Long = retryDelayMs,
        maxDelayMs: Long = 5000L,
        block: suspend () -> Unit
    ) {
        var currentDelay = initialDelayMs
        repeat(times) { attempt ->
            try {
                block()
                return
            } catch (e: Exception) {
                Log.e(TAG, "Attempt ${attempt + 1}/$times failed: ${e.message}")
            }
            delay(currentDelay)
            currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
        }
    }

    companion object {
        private const val TAG = "AdEventTracker"
    }
}