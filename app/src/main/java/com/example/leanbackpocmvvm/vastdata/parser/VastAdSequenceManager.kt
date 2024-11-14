package com.example.leanbackpocmvvm.vastdata.parser

import android.util.Log
import com.example.leanbackpocmvvm.vastdata.tracking.VastTrackingManager
import com.example.leanbackpocmvvm.vastdata.validator.VastMediaSelector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VastAdSequenceManager @Inject constructor(
    private val vastParser: VastParser,
    private val vastMediaSelector: VastMediaSelector,
    private val vastTrackingManager: VastTrackingManager
) {
    private var currentSequence = mutableListOf<VastParser.VastAd>()
    private var currentIndex = 0
    private var isPlaying = false

    suspend fun prepareAdSequence(vastUrl: String, tileId: String): Boolean {
        currentSequence.clear()
        currentIndex = 0

        try {
            val vastAds = vastParser.parseVastUrl(vastUrl, tileId)
            if (vastAds.isNullOrEmpty()) return false

            // Sort by sequence number and filter valid ads
            currentSequence.addAll(vastAds.sortedBy { it.sequence }
                .filter { vastMediaSelector.selectLowestBitrateMediaFile(it) != null })

            return currentSequence.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing ad sequence: ${e.message}")
            return false
        }
    }

    fun getCurrentAd(): VastParser.VastAd? {
        return if (currentIndex < currentSequence.size) currentSequence[currentIndex] else null
    }

    fun getCurrentVideoUrl(): String? {
        return getCurrentAd()?.let { ad ->
            vastMediaSelector.selectLowestBitrateMediaFile(ad)?.url
        }
    }

    fun hasNextAd(): Boolean = currentIndex < currentSequence.size - 1

    fun moveToNextAd(): Boolean {
        if (hasNextAd()) {
            currentIndex++
            return true
        }
        return false
    }

    fun startTracking() {
        getCurrentAd()?.let { vastTrackingManager.startTracking(it) }
    }

    fun completeCurrentAd() {
        getCurrentAd()?.let { vastTrackingManager.completeTracking(it) }
    }

    fun reset() {
        currentSequence.clear()
        currentIndex = 0
        isPlaying = false
    }

    companion object {
        private const val TAG = "VastAdSequenceManager"
    }
}