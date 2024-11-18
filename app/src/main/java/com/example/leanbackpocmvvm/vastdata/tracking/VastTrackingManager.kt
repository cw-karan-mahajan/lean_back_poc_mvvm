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
    private val completedEvents = ConcurrentHashMap<String, Boolean>()

    fun trackEvent(vastAd: VastParser.VastAd, eventType: String) {
        val trackingKey = "${eventType}_${vastAd.id}"

        /*
        if (eventType == VastParser.VastAd.EVENT_COMPLETE &&
            completedEvents.putIfAbsent(vastAd.id, true) == true) {
            Log.d(TAG, "Complete event already tracked for ad ${vastAd.id}")
            return
        }*/

        activeTracking[trackingKey]?.let { existingJob ->
            existingJob.cancel(CancellationException("New tracking started"))
        }

        activeTracking[trackingKey] = scope.launch {
            try {
                trackingSemaphore.acquire()
                vastAd.trackingEvents[eventType]?.let { url ->
                    try {
                        retryWithBackoff {
                            adEventTracker.trackEvent(url, eventType, vastAd.id)
                        }
                        Log.d(TAG, "Successfully tracked event: ${eventType}_${vastAd.id}")
                    } catch (e: CancellationException) {
                        Log.d(TAG, "Tracking cancelled for event: ${eventType}_${vastAd.id}")
                        if (eventType == VastParser.VastAd.EVENT_COMPLETE) {
                            completedEvents.remove(vastAd.id)
                        }
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to track event: ${eventType}_${vastAd.id}", e)
                        if (eventType == VastParser.VastAd.EVENT_COMPLETE) {
                            completedEvents.remove(vastAd.id)
                        }
                        throw e
                    }
                }
            } catch (e: CancellationException) {
                // Ignore cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Error tracking event $eventType for ad ${vastAd.id}: ${e.message}")
            } finally {
                trackingSemaphore.release()
                activeTracking.remove(trackingKey)
            }
        }
    }

    fun trackQuartile(vastAd: VastParser.VastAd, quartileEvent: String) {
        trackEvent(vastAd, quartileEvent)
    }

    fun completeTracking(vastAd: VastParser.VastAd) {
        scope.launch {
            try {
                // Track complete event
                trackEvent(vastAd, VastParser.VastAd.EVENT_COMPLETE)

                // Small delay to ensure tracking is sent before clearing cache
                delay(100)
                vastCache.clear()
            } catch (e: Exception) {
                Log.e(TAG, "Error completing tracking for ad ${vastAd.id}: ${e.message}")
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
            } catch (e: CancellationException) {
                throw e  // Don't retry on cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Attempt ${attempt + 1}/$maxRetryAttempts failed: ${e.message}")
                if (attempt < maxRetryAttempts - 1) {
                    delay(currentDelay)
                    currentDelay *= 2 // Exponential backoff
                } else {
                    throw e  // Rethrow last error
                }
            }
        }
    }

    fun cancelTracking(vastAdId: String) {
        completedEvents.remove(vastAdId)
        activeTracking.entries.removeIf { (key, job) ->
            if (key.contains(vastAdId)) {
                job.cancel(CancellationException("Tracking cancelled for ad: $vastAdId"))
                Log.d(TAG, "Cancelled tracking for ad: $vastAdId")
                true
            } else false
        }
    }

    fun cancelAllTracking() {
        scope.launch {
            try {
                completedEvents.clear()
                activeTracking.forEach { (key, job) ->
                    job.cancel(CancellationException("All tracking cancelled"))
                    Log.d(TAG, "Cancelled tracking for: $key")
                }
                activeTracking.clear()
                // Clear cache when cancelling all tracking
                vastCache.clear()
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling all tracking: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "VastTrackingManager"
    }
}