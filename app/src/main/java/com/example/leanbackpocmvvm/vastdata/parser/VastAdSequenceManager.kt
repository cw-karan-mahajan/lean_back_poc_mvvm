package com.example.leanbackpocmvvm.vastdata.parser

import com.example.leanbackpocmvvm.vastdata.tracking.VastTrackingManager
import com.example.leanbackpocmvvm.vastdata.validator.VastMediaSelector
import timber.log.Timber
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
    private var totalAds = 0

    suspend fun prepareAdSequence(vastUrl: String, tileId: String): Boolean {
        currentSequence.clear()
        currentIndex = 0
        totalAds = 0
        Timber.d(TAG, "tileId $tileId vastUrl $vastUrl")
        try {
            vastParser.parseVastUrl(vastUrl, tileId)
                .fold(
                    onSuccess = { vastAds ->
                        currentSequence.addAll(vastAds
                            .sortedBy { it.sequence }
                            .filter { vastMediaSelector.selectBestMediaFile(it) != null }
                        )
                        totalAds = currentSequence.size
                    },
                    onFailure = { error ->
                        Timber.e(TAG, "Error preparing ad sequence: ${error.message}")
                        return false
                    }
                )

            return currentSequence.isNotEmpty()
        } catch (e: Exception) {
            Timber.e(TAG, "Error preparing ad sequence: ${e.message}")
            return false
        }
    }

    fun getCurrentAd(): VastParser.VastAd? {
        return if (currentIndex < currentSequence.size) currentSequence[currentIndex] else null
    }

    fun getCurrentVideoUrl(): String? {
        return getCurrentAd()?.let { ad ->
            vastMediaSelector.selectBestMediaFile(ad)?.url
        }
    }

    fun hasNextAd(): Boolean = currentIndex < totalAds - 1

    fun isLastAd(): Boolean = currentIndex == totalAds - 1

    fun moveToNextAd(): Boolean {
        if (hasNextAd()) {
            currentIndex++
            return true
        }
        return false
    }

    fun completeCurrentAd() {
        getCurrentAd()?.let { vastTrackingManager.completeTracking(it) }
    }

    fun reset() {
        currentSequence.clear()
        currentIndex = 0
        totalAds = 0
    }

    fun getCurrentAdIndex(): Int = currentIndex

    fun getTotalAds(): Int = totalAds

    companion object {
        private const val TAG = "VastAdSequenceManager"
    }
}