package com.example.leanbackpocmvvm.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.example.leanbackpocmvvm.views.customview.NewVideoCardView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
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
    private val _isPlayingVideo = MutableLiveData<Boolean>()
    val isPlayingVideo: LiveData<Boolean> = _isPlayingVideo
    private val _hasVideoEnded = MutableLiveData<Boolean>()
    val hasVideoEnded: LiveData<Boolean> = _hasVideoEnded
    private var lastVideoStartTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private var pendingRestartRunnable: Runnable? = null
    private val MAX_RETRY_COUNT = 3
    private var retryCount = 0
    private val SURFACE_PREPARATION_TIMEOUT = 2000L // 2 seconds
    private var surfacePrepared = false
    private var useFallbackPlayer = false
    private var lastPlayedUrl: String = ""

    private val simpleCache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024) // 100 MB cache
        )
    }

    fun playVideo(videoUrl: String, cardView: NewVideoCardView, onVideoEnded: () -> Unit) {
        coroutineScope.launch(Dispatchers.Main) {
            Log.d(TAG, "Starting playback of $videoUrl")
            releasePlayer()

            lastPlayedUrl = videoUrl
            if (exoPlayer == null)
                exoPlayer = createExoPlayer(onVideoEnded)

            val upstreamFactory = DefaultDataSource.Factory(context)
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(simpleCache)
                .setUpstreamDataSourceFactory(upstreamFactory)
            val source = ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(videoUrl))

            exoPlayer?.let { player ->
                player.setMediaSource(source)
                player.prepare()

                cardView.startVideoPlayback(player)
                currentPlayingView = cardView

                player.playWhenReady = true
                _isPlayingVideo.value = true
                _hasVideoEnded.value = false
                lastVideoStartTime = System.currentTimeMillis()
            }
        }
    }

    private fun createExoPlayer(onVideoEnded: () -> Unit): ExoPlayer {
        Log.d(TAG, "ExoPlayerManager: Creating new ExoPlayer instance")
        if (useFallbackPlayer) {
            return createFallbackExoPlayer(onVideoEnded)
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS / 10,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / 10
            )
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context))
            .setRenderersFactory(
                DefaultRenderersFactory(context)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                    .setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                        try {
                            MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                        } catch (e: MediaCodecUtil.DecoderQueryException) {
                            Log.w(TAG, "Failed to query decoder info, falling back to default", e)
                            emptyList()
                        }
                    }
            )
            .setLoadControl(loadControl)
            .build()
            .apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                repeatMode = Player.REPEAT_MODE_OFF

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        Log.d(TAG, "ExoPlayer state changed to: $state")
                        when (state) {
                            Player.STATE_ENDED -> {
                                _hasVideoEnded.postValue(true)
                                _isPlayingVideo.postValue(false)
                                currentPlayingView?.showThumbnail()
                                currentPlayingView?.shrinkCard()
                                onVideoEnded()
                                Log.d(TAG, "ExoPlayer playback ended")
                            }

                            Player.STATE_READY -> {
                                _isPlayingVideo.postValue(playWhenReady)
                                currentPlayingView?.ensureVideoVisible()
                                Log.d(TAG, "ExoPlayer ready, playWhenReady: $playWhenReady")
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "ExoPlayer error: ${error.message}")
                        if (error.cause is IllegalStateException && error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) {
                            Log.w(TAG, "Caught MediaCodec error, attempting to recover")
                            handler.postDelayed({ restartVideo() }, 100)
                        } else {
                            handleRestartFailure()
                        }
                    }
                })
            }
    }

    private fun createFallbackExoPlayer(onVideoEnded: () -> Unit): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF))
            .build()
            .apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                repeatMode = Player.REPEAT_MODE_OFF

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        Log.d(TAG, "Fallback ExoPlayer state changed to: $state")
                        when (state) {
                            Player.STATE_ENDED -> {
                                _hasVideoEnded.postValue(true)
                                _isPlayingVideo.postValue(false)
                                currentPlayingView?.showThumbnail()
                                currentPlayingView?.shrinkCard()
                                onVideoEnded()
                                Log.d(TAG, "Fallback ExoPlayer playback ended")
                            }

                            Player.STATE_READY -> {
                                _isPlayingVideo.postValue(playWhenReady)
                                currentPlayingView?.ensureVideoVisible()
                                Log.d(TAG, "Fallback ExoPlayer ready, playWhenReady: $playWhenReady")
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Fallback ExoPlayer error: ${error.message}")
                        handleRestartFailure()
                    }
                })
            }
    }

    fun setVideoSurface(surface: Surface) {
        exoPlayer?.setVideoSurface(surface)
    }

    fun releasePlayer() {
        pendingRestartRunnable?.let { handler.removeCallbacks(it) }
        currentPlayingView?.showThumbnail()
        currentPlayingView?.shrinkCard()
        currentPlayingView = null
        exoPlayer?.run {
            stop()
            clearVideoSurface()
            setVideoSurface(null)
            clearMediaItems()
            release()
        }
        exoPlayer = null
        _isPlayingVideo.postValue(false)
        _hasVideoEnded.postValue(false)
        useFallbackPlayer = false
        retryCount = 0
    }

    private fun restartVideo() {
        Log.d(TAG, "restartVideo: Attempting to restart video")
        exoPlayer?.let { player ->
            player.pause()
            player.seekTo(0)

            surfacePrepared = false
            prepareSurface()

            handler.postDelayed({
                if (surfacePrepared) {
                    player.play()
                    _isPlayingVideo.postValue(true)
                    _hasVideoEnded.postValue(false)
                    currentPlayingView?.ensureVideoVisible()
                    lastVideoStartTime = System.currentTimeMillis()
                    retryCount = 0
                    Log.d(TAG, "restartVideo: Video restarted")
                } else {
                    Log.e(TAG, "restartVideo: Surface not prepared in time")
                    handleRestartFailure()
                }
            }, SURFACE_PREPARATION_TIMEOUT)
        }
    }

    private fun handleRestartFailure() {
        if (retryCount < MAX_RETRY_COUNT) {
            retryCount++
            Log.d(TAG, "Retrying video restart, attempt $retryCount")
            handler.postDelayed({ restartVideo() }, 1000)
        } else if (!useFallbackPlayer) {
            Log.w(TAG, "Switching to fallback player")
            useFallbackPlayer = true
            releasePlayer()
            currentPlayingView?.let { view ->
                playVideo(lastPlayedUrl, view) { /* onVideoEnded callback */ }
            }
        } else {
            Log.e(TAG, "Failed to restart video after $MAX_RETRY_COUNT attempts, even with fallback player")
            releasePlayer()
            currentPlayingView?.showThumbnail()
        }
    }

    private fun prepareSurface() {
        currentPlayingView?.let { cardView ->
            val surface = cardView.getVideoSurface()
            if (surface != null) {
                exoPlayer?.setVideoSurface(surface)
                surfacePrepared = true
                Log.d(TAG, "Surface prepared successfully")
            } else {
                Log.e(TAG, "Failed to get valid surface")
                handleRestartFailure()
            }
        }
    }

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
    }

    companion object {
        const val TAG = "ExoPlayerManager"
    }
}