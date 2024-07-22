package com.example.leanbackpocmvvm.utils

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.example.leanbackpocmvvm.views.customview.VideoCardView
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoExoPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    private var currentPlayingView: VideoCardView? = null
    private var currentTileId: String? = null

    private fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context)
                .setRenderersFactory(DefaultRenderersFactory(context).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER))
                .build()
            exoPlayer?.addListener(playerListener)
        }
        return exoPlayer!!
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player error: ${error.message}", error)
            currentPlayingView?.showThumbnail()
            // Attempt to recover from the error
            exoPlayer?.let { player ->
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
                        player.setMediaItem(MediaItem.fromUri(currentPlayingView?.videoTileItem?.videoUrl ?: ""))
                        player.prepare()
                    }
                }
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_IDLE -> Log.d(TAG, "Player is idle")
                Player.STATE_BUFFERING -> Log.d(TAG, "Player is buffering")
                Player.STATE_READY -> {
                    Log.d(TAG, "Player is ready")
                    currentPlayingView?.showVideo()
                }
                Player.STATE_ENDED -> {
                    Log.d(TAG, "Player has ended")
                    currentPlayingView?.showThumbnail()
                }
            }
        }
    }

    fun playVideo(videoUrl: String, cardView: VideoCardView, tileId: String) {
        this.currentTileId = tileId
        if (videoUrl.isEmpty()) return
        Log.d(TAG, "Playing video: $videoUrl")

        if (currentPlayingView != cardView) {
            stopPlayback()
        }

        val player = getPlayer()
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(videoUrl))

        // Detach the player from the previous view
        currentPlayingView?.playerView?.player = null

        // Attach the player to the new view
        cardView.playerView.player = player

        player.prepare()
        player.playWhenReady = true

        currentPlayingView = cardView
        cardView.showVideo() // Show video immediately after setting up the player
    }

    fun stopPlayback() {
        exoPlayer?.release()
        exoPlayer = null
        //exoPlayer?.stop()
        currentPlayingView?.showThumbnail()
        currentPlayingView?.playerView?.player = null
        currentPlayingView = null
        currentTileId = null
    }

    fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
        currentPlayingView = null
        currentTileId = null
    }

    fun preloadVideo(videoUrl: String) {
        if (videoUrl.isEmpty()) return
        val player = getPlayer()
        player.addMediaItem(MediaItem.fromUri(videoUrl))
        player.prepare()
    }

    companion object {
        private const val TAG = "VideoExoPlayerManager"
    }
}