package com.example.leanbackpocmvvm.vastdata.tracking

import android.util.Log
import com.example.leanbackpocmvvm.vastdata.cache.SimpleVastCache
import com.example.leanbackpocmvvm.vastdata.parser.VastParser
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VastTrackingManager @Inject constructor(
    private val adEventTracker: AdEventTracker,
    private val vastCache: SimpleVastCache,
    private val maxRetryAttempts: Int = 3,
    private val retryDelayMs: Long = 1000L,
    private val maxConcurrentTracking: Int = 5
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeTracking = ConcurrentHashMap<String, Job>()
    private val trackingSemaphore = Semaphore(maxConcurrentTracking)

    fun startTracking(vastAd: VastParser.VastAd) {
        val trackingKey = "track_${vastAd.id}"

        activeTracking[trackingKey]?.cancel()
        activeTracking[trackingKey] = scope.launch {
            try {
                trackingSemaphore.acquire()

                // Track impression
                retryWithBackoff {
                    adEventTracker.trackImpression(vastAd)
                }

                // Track start event
                vastAd.trackingEvents[VastParser.VastAd.EVENT_START]?.let { url ->
                    retryWithBackoff {
                        adEventTracker.trackEvent(url, VastParser.VastAd.EVENT_START, vastAd.id)
                    }
                }

                // Cache the VastAd
                vastCache.put(vastAd.id, vastAd)

            } catch (e: Exception) {
                Log.e(TAG, "Error starting tracking for ad ${vastAd.id}: ${e.message}")
            } finally {
                trackingSemaphore.release()
            }
        }
    }

    fun trackQuartile(vastAd: VastParser.VastAd, quartileEvent: String) {
        val trackingKey = "quartile_${vastAd.id}_$quartileEvent"

        activeTracking[trackingKey]?.cancel()
        activeTracking[trackingKey] = scope.launch {
            try {
                trackingSemaphore.acquire()

                vastAd.trackingEvents[quartileEvent]?.let { url ->
                    retryWithBackoff {
                        adEventTracker.trackEvent(url, quartileEvent, vastAd.id)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error tracking quartile for ad ${vastAd.id}: ${e.message}")
            } finally {
                trackingSemaphore.release()
            }
        }
    }

    fun completeTracking(vastAd: VastParser.VastAd) {
        val trackingKey = "complete_${vastAd.id}"

        activeTracking[trackingKey]?.cancel()
        activeTracking[trackingKey] = scope.launch {
            try {
                trackingSemaphore.acquire()

                vastAd.trackingEvents[VastParser.VastAd.EVENT_COMPLETE]?.let { url ->
                    retryWithBackoff {
                        adEventTracker.trackEvent(url, VastParser.VastAd.EVENT_COMPLETE, vastAd.id)
                    }
                }

                // Remove from cache after completion
                vastCache.clear()

            } catch (e: Exception) {
                Log.e(TAG, "Error completing tracking for ad ${vastAd.id}: ${e.message}")
            } finally {
                trackingSemaphore.release()
            }
        }
    }

    private suspend fun retryWithBackoff(
        block: suspend () -> Unit
    ) {
        var currentDelay = retryDelayMs
        repeat(maxRetryAttempts) { attempt ->
            try {
                block()
                return
            } catch (e: Exception) {
                Log.e(TAG, "Attempt ${attempt + 1}/$maxRetryAttempts failed: ${e.message}")
                if (attempt < maxRetryAttempts - 1) {
                    delay(currentDelay)
                    currentDelay *= 2 // Exponential backoff
                }
            }
        }
    }

    fun cancelTracking(vastAdId: String) {
        activeTracking.entries.removeIf { (key, job) ->
            if (key.contains(vastAdId)) {
                job.cancel()
                true
            } else false
        }
    }

    fun cancelAllTracking() {
        activeTracking.forEach { (_, job) -> job.cancel() }
        activeTracking.clear()
        scope.coroutineContext.cancelChildren()
    }

    companion object {
        private const val TAG = "VastTrackingManager"
    }
}