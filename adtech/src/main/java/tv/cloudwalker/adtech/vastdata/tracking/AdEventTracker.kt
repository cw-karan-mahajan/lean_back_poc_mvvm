package tv.cloudwalker.adtech.vastdata.tracking

import android.util.Log
import tv.cloudwalker.adtech.vastdata.parser.VastParser.VastAd
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

    fun trackEvent(url: String, eventType: String, vastAdId: String) {
        fireTrackingPixel(url, "${eventType}_${vastAdId}")
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

    companion object {
        private const val TAG = "AdEventTracker"
    }
}