package com.example.leanbackpocmvvm.vastdata.player

import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.vastdata.bridges.VastComponent
import android.content.Context
import androidx.media3.ui.PlayerView
import com.example.leanbackpocmvvm.vastdata.parser.VastParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber

@UnstableApi
class ExoPlayerManagerWrapper private constructor(context: Context) {

    interface ProgressCallback {
        fun onProgressUpdate(currentPosition: Long, duration: Long, adNumber: Int, totalAds: Int)
    }

    interface PlayerCallback {
        fun onReady(isReady: Boolean)
        fun onEnded()
        fun onError(message: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val exoPlayerManager: ExoPlayerManager
    private var progressCallback: ProgressCallback? = null
    private var playerCallback: PlayerCallback? = null

    init {
        // Initialize VastComponent first
        VastComponent.initialize(context.applicationContext)

        exoPlayerManager = ExoPlayerManager(
            context.applicationContext,
            VastComponent.getVastTrackingManager()
        )

        setupProgressCallback()
    }

    private fun setupProgressCallback() {
        exoPlayerManager.setProgressCallback(object : ExoPlayerManager.ProgressCallback {
            override fun onProgressUpdate(
                currentPosition: Long,
                duration: Long,
                adNumber: Int,
                totalAds: Int
            ) {
                progressCallback?.onProgressUpdate(currentPosition, duration, adNumber, totalAds)
            }
        })
    }

    fun prepareVastAd(
        vastUrl: String,
        tileId: String,
        playerView: PlayerView,
        callback: PlayerCallback,
        progress: ProgressCallback,
        vastAd: VastParser.VastAd,
        adNumber: Int,
        totalAds: Int
    ) {
        try {
            Timber.d(TAG, "Preparing VAST ad playback: $vastUrl")
            playerCallback = callback
            progressCallback = progress

            exoPlayerManager.prepareVideo(
                videoUrl = vastUrl,
                playerView = playerView,
                onReady = { isReady ->
                    callback.onReady(isReady)
                },
                onEnded = {
                    callback.onEnded()
                },
                isPartOfSequence = true,
                vastAd = vastAd,
                adNumber = adNumber,
                totalAds = totalAds
            )
        } catch (e: Exception) {
            Timber.e(TAG, "Error preparing VAST ad: ${e.message}", e)
            callback.onError(e.message ?: "Unknown error preparing VAST ad")
            VastComponent.getVastErrorHandler().logError(e, tileId, "prepareVastAd")
        }
    }

    fun onLifecycleDestroy() {
        Timber.d(TAG, "Lifecycle destroy")
        exoPlayerManager.onLifecycleDestroy()
        scope.cancel()
        clearCallbacks()
        instance = null
    }

    private fun clearCallbacks() {
        progressCallback = null
        playerCallback = null
    }

    fun isPlaying(): Boolean = exoPlayerManager.isPlayingVideo.get()

    companion object {
        private const val TAG = "ExoPlayerManagerWrapper"

        @Volatile
        private var instance: ExoPlayerManagerWrapper? = null

        @JvmStatic
        fun getInstance(context: Context): ExoPlayerManagerWrapper {
            return instance ?: synchronized(this) {
                instance ?: ExoPlayerManagerWrapper(context).also { instance = it }
            }
        }
    }
}