package tv.cloudwalker.adtech.player

import android.content.Context
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.PlayerView
import tv.cloudwalker.adtech.vastdata.parser.VastParser
import tv.cloudwalker.adtech.vastdata.tracking.VastTrackingManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import timber.log.Timber
import tv.cloudwalker.adtech.utils.isAndroidVersion9Supported
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class ExoPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vastTrackingManager: VastTrackingManager
) {
    // Progress tracking callback interface
    interface ProgressCallback {
        fun onProgressUpdate(currentPosition: Long, duration: Long, adNumber: Int, totalAds: Int)
    }

    private var exoPlayer: ExoPlayer? = null
    var isPlayingVideo = AtomicBoolean(false)
    private var hasVideoEnded = AtomicBoolean(false)
    private var currentVideoUrl = ""
    private val trackedEvents = ConcurrentHashMap<String, MutableSet<String>>()
    private var currentVastAd: VastParser.VastAd? = null
    private var progressCallback: ProgressCallback? = null
    private var progressTrackingJob: Job? = null
    private var currentAdNumber = 0
    private var totalAdsCount = 0

    private val simpleCache: SimpleCache by lazy {
        val cacheSize = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            50 * 1024 * 1024 // 50 MB for Android 9
        } else {
            100 * 1024 * 1024 // 100 MB for other versions
        }
        SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(cacheSize.toLong())
        )
    }

    private val isReleasing = AtomicBoolean(false)
    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun setProgressCallback(callback: ProgressCallback) {
        progressCallback = callback
    }

    fun getOrCreatePlayer(): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = createExoPlayer()
        }
        return exoPlayer!!
    }

    fun prepareVideo(
        videoUrl: String,
        playerView: PlayerView,
        onReady: (Boolean) -> Unit,
        onEnded: () -> Unit,
        isPartOfSequence: Boolean = false,
        vastAd: VastParser.VastAd? = null,
        adNumber: Int = 1,
        totalAds: Int = 1
    ) {
        playerScope.launch {
            try {
                while (isReleasing.get()) {
                    delay(100)
                }

                currentAdNumber = adNumber
                totalAdsCount = totalAds

                Timber.d(
                    TAG,
                    "Starting video preparation - isPartOfSequence: $isPartOfSequence, Ad $adNumber/$totalAds"
                )

                when {
                    isPlayingVideo.get() -> {
                        if (!isPartOfSequence) {
                            releasePlayer()
                        }
                    }
                }

                val player = getOrCreatePlayer()
                playerView.player = player

                val upstreamFactory = DefaultDataSource.Factory(context)
                val cacheDataSourceFactory = CacheDataSource.Factory()
                    .setCache(simpleCache)
                    .setUpstreamDataSourceFactory(upstreamFactory)
                val source = ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(videoUrl))

                player.stop()
                player.clearMediaItems()
                player.setMediaSource(source)
                player.prepare()

                vastAd?.let { setupTrackingListener(it) }

                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> {
                                Timber.d(TAG, "Video ready to play")
                                startProgressTracking()
                                onReady(true)
                                isPlayingVideo.set(true)
                                hasVideoEnded.set(false)
                            }

                            Player.STATE_ENDED -> {
                                Timber.d(TAG, "Video playback ended")
                                stopProgressTracking()
                                if (!isPartOfSequence) {
                                    releasePlayer()
                                }
                                onEnded()
                                hasVideoEnded.set(true)
                                isPlayingVideo.set(false)
                            }

                            Player.STATE_BUFFERING -> {
                                Timber.d(TAG, "Video buffering")
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Timber.e(TAG, "Player error: ${error.message}", error)
                        stopProgressTracking()
                        onReady(false)
                        when (error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                                player.prepare()
                            }

                            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                                player.seekToDefaultPosition()
                                player.prepare()
                            }

                            else -> {
                                player.stop()
                                player.clearMediaItems()
                                player.setMediaItem(MediaItem.fromUri(videoUrl))
                                player.prepare()
                            }
                        }
                    }
                })

                currentVideoUrl = videoUrl
                player.playWhenReady = true

            } catch (e: Exception) {
                Timber.e(TAG, "Error preparing video: ${e.message}")
                stopProgressTracking()
                onReady(false)
            }
        }
    }

    private fun startProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = playerScope.launch {
            try {
                while (isActive) {
                    exoPlayer?.let { player ->
                        if (player.playbackState == Player.STATE_READY ||
                            player.playbackState == Player.STATE_BUFFERING) {

                            val duration = when (val rawDuration = player.duration) {
                                C.TIME_UNSET -> 0L
                                else -> rawDuration
                            }

                            val position = when (val rawPosition = player.currentPosition) {
                                C.TIME_UNSET -> 0L
                                else -> rawPosition.coerceAtMost(duration)
                            }

                            progressCallback?.onProgressUpdate(
                                currentPosition = position,
                                duration = duration,
                                adNumber = currentAdNumber,
                                totalAds = totalAdsCount
                            )
                        }
                        delay(200) // More frequent updates for smoother progress
                    }
                }
            } catch (e: Exception) {
                Timber.e(TAG, "Error tracking progress: ${e.message}")
            }
        }
    }

    private fun stopProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }

    private fun setupTrackingListener(vastAd: VastParser.VastAd) {
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        if (!hasTrackedEvent(vastAd.id, VastParser.VastAd.EVENT_START)) {
                            vastTrackingManager.trackEvent(vastAd, VastParser.VastAd.EVENT_START)
                            markEventTracked(vastAd.id, VastParser.VastAd.EVENT_START)
                            startProgressMonitoring(vastAd)
                        }
                    }

                    Player.STATE_ENDED -> {
                        if (!hasTrackedEvent(vastAd.id, VastParser.VastAd.EVENT_COMPLETE)) {
                            vastTrackingManager.trackEvent(vastAd, VastParser.VastAd.EVENT_COMPLETE)
                            markEventTracked(vastAd.id, VastParser.VastAd.EVENT_COMPLETE)
                            clearTrackedEvents(vastAd.id)
                        }
                    }
                }
            }
        })
    }

    private fun startProgressMonitoring(vastAd: VastParser.VastAd) {
        var trackedFirstQuartile = false
        var trackedMidpoint = false
        var trackedThirdQuartile = false

        playerScope.launch {
            try {
                while (isActive && exoPlayer != null) {
                    val duration = exoPlayer?.duration ?: 0
                    val position = exoPlayer?.currentPosition ?: 0

                    if (duration > 0) {
                        val percentage = (position * 100.0) / duration

                        if (!trackedFirstQuartile && percentage >= 25) {
                            vastTrackingManager.trackQuartile(
                                vastAd,
                                VastParser.VastAd.EVENT_FIRST_QUARTILE
                            )
                            trackedFirstQuartile = true
                        }
                        if (!trackedMidpoint && percentage >= 50) {
                            vastTrackingManager.trackQuartile(
                                vastAd,
                                VastParser.VastAd.EVENT_MIDPOINT
                            )
                            trackedMidpoint = true
                        }
                        if (!trackedThirdQuartile && percentage >= 75) {
                            vastTrackingManager.trackQuartile(
                                vastAd,
                                VastParser.VastAd.EVENT_THIRD_QUARTILE
                            )
                            trackedThirdQuartile = true
                        }
                    }
                    delay(200)
                }
            } catch (e: Exception) {
                Timber.e(TAG, "Error monitoring progress: ${e.message}")
            }
        }
    }

    private fun createExoPlayer(): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setMediaCodecSelector(createMediaCodecSelector())
        }
        return ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(createLoadControl())
            .build()
            .apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    private fun createMediaCodecSelector(): MediaCodecSelector {
        return MediaCodecSelector.DEFAULT.let { defaultSelector ->
            MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                val decoders = defaultSelector.getDecoderInfos(
                    mimeType,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder
                )
                if (isAndroidVersion9Supported()) {
                    decoders.sortedWith(compareByDescending {
                        it.name.lowercase().contains("qcom") ||
                                it.name.lowercase().contains("mali") ||
                                it.name.lowercase().contains("arm")
                    })
                } else {
                    decoders.filterNot { it.name == "OMX.MS.AVC.Decoder" }
                }
            }
        }
    }

    private fun createLoadControl(): LoadControl {
        val builder = DefaultLoadControl.Builder()
        if (isAndroidVersion9Supported()) {
            builder.setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS / 2,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS / 2,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS / 2,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / 2
            )
        } else {
            builder.setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS / 10,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / 10
            )
        }
        return builder.build()
    }

    private fun hasTrackedEvent(vastAdId: String, event: String): Boolean {
        return trackedEvents.getOrPut(vastAdId) {
            Collections.newSetFromMap(ConcurrentHashMap())
        }.contains(event)
    }

    private fun markEventTracked(vastAdId: String, event: String) {
        trackedEvents.getOrPut(vastAdId) {
            Collections.newSetFromMap(ConcurrentHashMap())
        }.add(event)
    }

    private fun setCurrentVastAd(vastAd: VastParser.VastAd?) {
        currentVastAd = vastAd
        if (vastAd == null) {
            clearAllTracking()
        }
    }

    fun clearTrackedEvents(vastAdId: String) {
        trackedEvents.remove(vastAdId)
    }

    private fun clearAllTracking() {
        trackedEvents.clear()
        currentVastAd = null
    }

    fun releasePlayer() {
        if (isReleasing.getAndSet(true)) {
            return
        }

        playerScope.launch {
            try {
                stopProgressTracking()
                clearAllTracking()
                currentVastAd?.let { vastAd ->
                    vastTrackingManager.cancelTracking(vastAd.id)
                }
                exoPlayer?.run {
                    stop()
                    clearMediaItems()
                    release()
                }
            } finally {
                exoPlayer = null
                isPlayingVideo.set(false)
                hasVideoEnded.set(false)
                isReleasing.set(false)
                currentVideoUrl = ""
                progressCallback = null
                currentAdNumber = 0
                totalAdsCount = 0
            }
        }
    }

    fun reinitializePlayer() {
        releasePlayer()
        exoPlayer = createExoPlayer()
    }

    fun onLifecycleDestroy() {
        clearAllTracking()
        vastTrackingManager.cancelAllTracking()
        stopProgressTracking()
        releasePlayer()
        playerScope.cancel()
    }

    companion object {
        const val TAG = "ExoPlayerManager"
    }
}