package com.example.leanbackpocmvvm.utils

import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
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
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.leanbackpocmvvm.views.customview.NewVideoCardView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var isPlayingVideo = false
    private var hasVideoEnded = false
    private val simpleCache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024) // 100 MB cache
        )
    }

    fun playVideo(videoUrl: String, cardView: NewVideoCardView, onVideoEnded: () -> Unit) {
        //Log.d(TAG, "Attempting to play video: $videoUrl")
        coroutineScope.launch(Dispatchers.Main) {
            releasePlayer()

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
                isPlayingVideo = true
                hasVideoEnded = false
            }
        }
    }

    private fun createExoPlayer(onVideoEnded: () -> Unit): ExoPlayer {
        Log.d(TAG, "ExoPlayerManager: Creating new ExoPlayer instance")
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS / 10,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / 10
            )
            .build()

        return ExoPlayer.Builder(context)
            .setRenderersFactory(
                DefaultRenderersFactory(context).setExtensionRendererMode(
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                )
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
                                hasVideoEnded = true
                                isPlayingVideo = false
                                currentPlayingView?.showThumbnail()
                                currentPlayingView?.shrinkCard()
                                onVideoEnded()
                                Log.d(TAG, "ExoPlayer playback ended")
                            }

                            Player.STATE_READY -> {
                                isPlayingVideo = playWhenReady
                                currentPlayingView?.ensureVideoVisible()
                                Log.d(TAG, "ExoPlayer ready, playWhenReady: $playWhenReady")
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "ExoPlayer error: ${error.message}")
                        currentPlayingView?.showThumbnail()
                        currentPlayingView?.shrinkCard()
                        onVideoEnded()
                    }
                })
            }
    }

    fun setVideoSurface(surface: Surface) {
        exoPlayer?.setVideoSurface(surface)
    }

    fun releasePlayer() {
        //coroutineScope.launch(Dispatchers.Main) {
        currentPlayingView?.showThumbnail()
        currentPlayingView = null
        exoPlayer?.release()
        exoPlayer = null
        isPlayingVideo = false
        hasVideoEnded = false
        // }
    }

    fun isVideoPlaying(): Boolean = isPlayingVideo

    companion object {
        const val TAG = "ExoPlayerManager"
    }
}