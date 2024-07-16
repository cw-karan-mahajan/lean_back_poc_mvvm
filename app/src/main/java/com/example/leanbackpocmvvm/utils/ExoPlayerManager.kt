package com.example.leanbackpocmvvm.utils

import android.content.Context
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
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.example.leanbackpocmvvm.views.customview.NewVideoCardView
import com.example.leanbackpocmvvm.views.viewmodel.MainViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
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
    private var currentPlayingView: NewVideoCardView? = null
    private var isPlayingVideo = AtomicBoolean(false)
    private var hasVideoEnded = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var tileId:String
    private val simpleCache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024) // 100 MB cache
        )
    }

    private val isReleasing = AtomicBoolean(false)

    suspend fun playVideo(videoUrl: String, cardView: NewVideoCardView, tileId: String) {
        this.tileId = tileId
        if (videoUrl.isNullOrEmpty()) return
        var retryCount = 0
        val maxRetries = 1
        withContext(Dispatchers.Main) {
            Log.d(TAG, "Starting playVideo for URL: $videoUrl")

            try {
                while (isReleasing.get()) {
                    Log.d(TAG, "Waiting for player release to complete")
                    delay(100)
                }

                if (isPlayingVideo.get()) {
                    Log.d(TAG, "Video is already playing, releasing player")
                    releasePlayer()
                }

                cardView.prepareForVideoPlayback()
                delay(500) // Give some time for the layout to update

                while (retryCount < maxRetries) {
                    try {
                        Log.d(TAG, "Attempt ${retryCount + 1} to play video")
                        if (exoPlayer == null) {
                            Log.d(TAG, "Creating new ExoPlayer instance")
                            exoPlayer = createExoPlayer()
                        }

                        val upstreamFactory = DefaultDataSource.Factory(context)
                        val cacheDataSourceFactory = CacheDataSource.Factory()
                            .setCache(simpleCache)
                            .setUpstreamDataSourceFactory(upstreamFactory)
                        val source = ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(videoUrl))

                        exoPlayer?.let { player ->
                            Log.d(TAG, "Setting media source and preparing player")
                            player.setMediaSource(source)
                            player.prepare()

                            Log.d(TAG, "About to call getVideoSurface")
                            val surface = cardView.getVideoSurface()
                            Log.d(TAG, "After getVideoSurface call, surface: ${surface != null}")

                            if (surface != null && surface.isValid) {
                                Log.d(TAG, "Setting video surface and starting playback")
                                player.setVideoSurface(surface)
                                cardView.startVideoPlayback(player)
                                currentPlayingView = cardView
                                player.playWhenReady = true
                                isPlayingVideo.set(true)
                                hasVideoEnded.set(false)

                                Log.d(TAG, "Video playback started successfully")
                                return@withContext
                            } else {
                                Log.e(
                                    TAG,
                                    "Invalid or null surface received. PlayerView state: ${cardView.getPlayerViewState()}"
                                )
                                cardView.resetPlayerView()
                                delay(1000) // Wait a bit before retrying
                                retryCount++
                            }
                        } ?: run {
                            Log.e(TAG, "ExoPlayer is null")
                            throw IllegalStateException("ExoPlayer is null")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error playing video (attempt ${retryCount + 1}): ${e.message}")
                        e.printStackTrace()
                        releasePlayer()
                        cardView.resetPlayerView()
                        if (retryCount < maxRetries - 1) {
                            delay(1000)
                            retryCount++
                        } else {
                            throw e
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play video after $maxRetries attempts", e)
                cardView.showThumbnail()
                cardView.shrinkCard()
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
            .setLooper(mainHandler.looper)
            .build()
            .apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                repeatMode = Player.REPEAT_MODE_OFF

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        mainHandler.post {
                            when (state) {
                                Player.STATE_ENDED -> {
                                    Log.d(TAG, "Playback ended")
                                    hasVideoEnded.set(true)
                                    isPlayingVideo.set(false)
                                    currentPlayingView?.resetPlayerView()
                                    currentPlayingView?.shrinkCard()
                                    currentPlayingView?.onVideoEnded(tileId)  // Notify video ended
                                }

                                Player.STATE_READY -> {
                                    Log.d(TAG, "Player is ready")
                                    isPlayingVideo.set(playWhenReady)
                                    currentPlayingView?.ensureVideoVisible()
                                }

                                Player.STATE_BUFFERING -> Log.d(TAG, "Player is buffering")
                                Player.STATE_IDLE -> Log.d(TAG, "Player is idle")
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        mainHandler.post {
                            Log.e(TAG, "ExoPlayer error: ${error.message}")
                            error.printStackTrace()
                            currentPlayingView?.showThumbnail()
                            currentPlayingView?.shrinkCard()
                            releasePlayer()
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d(TAG, "Is playing changed: $isPlaying")
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

    fun setVideoSurface(surface: Surface) {
        mainHandler.post {
            exoPlayer?.setVideoSurface(surface)
        }
    }

    fun releasePlayer() {
        if (isReleasing.getAndSet(true)) {
            Log.d(TAG, "ExoPlayer is already being released")
            return
        }

        mainHandler.post {
            try {
                currentPlayingView?.showThumbnail()
                currentPlayingView?.shrinkCard()
                currentPlayingView = null
                exoPlayer?.release()
                exoPlayer = null
                isPlayingVideo.set(false)
                hasVideoEnded.set(false)
            } finally {
                isReleasing.set(false)
            }
        }
    }

    fun getPlayer(): ExoPlayer {
        return exoPlayer!!
    }

    fun isVideoPlaying(): Boolean = isPlayingVideo.get()

    companion object {
        const val TAG = "ExoPlayerManager"
    }
}