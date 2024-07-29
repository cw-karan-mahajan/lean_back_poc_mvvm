package com.example.leanbackpocmvvm.views.exoplayer

import android.content.Context
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
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.example.leanbackpocmvvm.views.customview.NewVideoCardView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class ExoPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private var exoPlayer: ExoPlayer? = null
    private var currentPlayingView: WeakReference<NewVideoCardView>? = null
    private var isPlayingVideo = AtomicBoolean(false)
    private var hasVideoEnded = AtomicBoolean(false)
    private lateinit var tileId: String
    private val simpleCache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024) // 100 MB cache
        )
    }

    private val isReleasing = AtomicBoolean(false)
    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun getOrCreatePlayer(): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = createExoPlayer()
        }
        return exoPlayer!!
    }

    suspend fun playVideo(videoUrl: String, cardView: NewVideoCardView, tileId: String) {
        this.tileId = tileId
        if (videoUrl.isEmpty()) return

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
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS / 10,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / 10
            )
            .build()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setMediaCodecSelector(createMediaCodecSelector())
        }

        return ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                repeatMode = Player.REPEAT_MODE_OFF

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        playerScope.launch {
                            when (state) {
                                Player.STATE_ENDED -> handleVideoEnded()
                                Player.STATE_READY -> handlePlayerReady()
                                Player.STATE_BUFFERING -> Log.d(TAG, "Player is buffering")
                                Player.STATE_IDLE -> Log.d(TAG, "Player is idle")
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        playerScope.launch {
                            handlePlayerError(error)
                        }
                    }
                })
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
                decoders.filterNot { it.name == "OMX.MS.AVC.Decoder" }
            }
        }
    }

    private fun handleVideoEnded() {
        hasVideoEnded.set(true)
        isPlayingVideo.set(false)
        currentPlayingView?.get()?.let { view ->
            view.resetPlayerView()
            view.shrinkCard()
            view.onVideoEnded(tileId)
        }
    }

    private fun handlePlayerReady() {
        isPlayingVideo.set(exoPlayer?.playWhenReady == true)
        currentPlayingView?.get()?.ensureVideoVisible()
    }

    private fun handlePlayerError(error: PlaybackException) {
        Log.e(TAG, "ExoPlayer error: ${error.message}")
        currentPlayingView?.get()?.let { view ->
            view.showThumbnail()
            view.shrinkCard()
        }
        releasePlayer()
    }

    fun setVideoSurface(surface: Surface) {
        playerScope.launch {
            exoPlayer?.setVideoSurface(surface)
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
                exoPlayer?.release()
                exoPlayer = null
                isPlayingVideo.set(false)
                hasVideoEnded.set(false)
            } finally {
                isReleasing.set(false)
            }
        }
    }

    fun onLifecycleDestroy() {
        releasePlayer()
        playerScope.cancel()
    }

    companion object {
        const val TAG = "ExoPlayerManager"
    }
}