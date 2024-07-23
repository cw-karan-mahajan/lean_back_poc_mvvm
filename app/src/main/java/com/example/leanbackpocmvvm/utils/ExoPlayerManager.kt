package com.example.leanbackpocmvvm.utils

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
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
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.example.leanbackpocmvvm.views.customview.NewVideoCardView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class ExoPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private var currentPlayingView: WeakReference<NewVideoCardView>? = null
    private var isPlayingVideo = AtomicBoolean(false)
    private var hasVideoEnded = AtomicBoolean(false)
    private lateinit var tileId: String
    private var currentVideoUrl = ""
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

    private val playerPool = ObjectPool(
        factory = { createExoPlayer() },
        reset = { it.stop() },
        validate = { it.playbackState != Player.STATE_IDLE }
    )

    private fun getOrCreatePlayer(): ExoPlayer {
        return playerPool.acquire()
    }

    suspend fun playVideo(videoUrl: String, cardView: NewVideoCardView, tileId: String) {
        this.tileId = tileId
        if (videoUrl.isEmpty()) return
        currentVideoUrl = videoUrl
        withContext(Dispatchers.Main) {
            try {
                while (isReleasing.get()) {
                    delay(100)
                }

                if (isPlayingVideo.get()) {
                    releasePlayer()
                }

                cardView.prepareForVideoPlayback()
                delay(500)

                val player = getOrCreatePlayer()
                val upstreamFactory = DefaultDataSource.Factory(context)
                val cacheDataSourceFactory = CacheDataSource.Factory()
                    .setCache(simpleCache)
                    .setUpstreamDataSourceFactory(upstreamFactory)
                val source = ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(videoUrl))

                player.setMediaSource(source)
                player.prepare()

                val surface = cardView.getVideoSurface()
                if (surface != null && surface.isValid) {
                    player.setVideoSurface(surface)
                    cardView.startVideoPlayback(player)
                    currentPlayingView = WeakReference(cardView)
                    player.playWhenReady = true
                    isPlayingVideo.set(true)
                    hasVideoEnded.set(false)
                } else {
                    releasePlayer()
                    cardView.resetPlayerView()
                    throw IllegalStateException("Invalid or null surface")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing video: ${e.message}")
                releasePlayer()
                cardView.resetPlayerView()
                throw e
            }
        }
    }

    private fun createExoPlayer(): ExoPlayer {
        val player = if (isAndroidVersion9Supported()) {
            ExoPlayer.Builder(context)
                .setRenderersFactory(
                    DefaultRenderersFactory(context).setExtensionRendererMode(
                        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    ).setMediaCodecSelector(createMediaCodecSelector())
                )
                .setLoadControl(createLoadControl())
                .build().apply {
                    addListener(playerListener)
                }

        } else {
            val renderersFactory = DefaultRenderersFactory(context).apply {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                setMediaCodecSelector(createMediaCodecSelector())
            }

            ExoPlayer.Builder(context, renderersFactory)
                .setLoadControl(createLoadControl())
                .build()
                .apply {
                    videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    repeatMode = Player.REPEAT_MODE_OFF

                    addListener(playerListener)
                }
        }

        return player
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

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            playerScope.launch {
                Log.e(TAG, "Player error: ${error.message}", error)
                currentPlayingView?.get()?.showThumbnail()
                // Attempt to recover from the error
                getOrCreatePlayer().let { player ->
                    when (error.errorCode) {
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                            // Retry network errors
                            player.prepare()
                        }

                        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                            // Retry playback for live streams
                            player.seekToDefaultPosition()
                            player.prepare()
                        }

                        else -> {
                            // For other errors, reset the player
                            player.stop()
                            player.clearMediaItems()
                            player.setMediaItem(MediaItem.fromUri(currentVideoUrl ?: ""))
                            player.prepare()
                        }
                    }
                }
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            playerScope.launch {
                when (state) {
                    Player.STATE_IDLE -> Log.d(TAG, "Player is idle")
                    Player.STATE_BUFFERING -> Log.d(TAG, "Player is buffering")
                    Player.STATE_READY -> {
                        Log.d(TAG, "Player is ready")
                        currentPlayingView?.get()?.ensureVideoVisible()
                    }

                    Player.STATE_ENDED -> {
                        Log.d(TAG, "Player has ended")
                        handleVideoEnded()
                    }
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

    private fun handleVideoEnded() {
        hasVideoEnded.set(true)
        isPlayingVideo.set(false)
        currentPlayingView?.get()?.let { view ->
            view.resetPlayerView()
            view.shrinkCard()
            if (!isAndroidVersion9Supported()) {
                view.onVideoEnded(tileId)
            }
        }
    }

    fun setVideoSurface(surface: Surface) {
        playerScope.launch {
            getOrCreatePlayer().setVideoSurface(surface)
        }
    }

    fun releasePlayer() {
        if (isReleasing.getAndSet(true)) {
            Log.d(TAG, "ExoPlayer is already being released")
            return
        }

        playerScope.launch {
            try {
                currentPlayingView?.get()?.showThumbnail()
                currentPlayingView?.get()?.shrinkCard()
                currentPlayingView?.clear()
                getOrCreatePlayer().let { player ->
                    player.stop()
                    playerPool.release(player)
                }
                isPlayingVideo.set(false)
                hasVideoEnded.set(false)
            } finally {
                isReleasing.set(false)
            }
        }

        if (isAndroidVersion9Supported())
            System.gc()
    }

    fun onLifecycleDestroy() {
        releasePlayer()
        playerScope.cancel()
    }

    companion object {
        const val TAG = "ExoPlayerManager"
    }
}

class ObjectPool<T>(
    private val factory: () -> T,
    private val reset: (T) -> Unit,
    private val validate: (T) -> Boolean
) {
    private val pool = ConcurrentLinkedQueue<T>()

    fun acquire(): T {
        var obj = pool.poll()
        if (obj == null || !validate(obj)) {
            obj = factory()
        } else {
            reset(obj)
        }
        return obj
    }

    fun release(obj: T) {
        reset(obj)
        pool.offer(obj)
    }
}