package com.example.leanbackpocmvvm.utils

import android.content.Context
import android.util.Log
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import com.example.leanbackpocmvvm.views.customview.NewVideoCardView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    fun playVideo(videoUrl: String, cardView: NewVideoCardView, onVideoEnded: () -> Unit) {
        coroutineScope.launch(Dispatchers.Main) {
            releasePlayer()

            exoPlayer = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                prepare()
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            coroutineScope.launch(Dispatchers.Main) {
                                onVideoEnded()
                                releasePlayer()
                            }
                        }
                    }
                })
            }

            cardView.getPlayerView().player = exoPlayer
            currentPlayingView = cardView
        }
    }

    fun releasePlayer() {
        coroutineScope.launch(Dispatchers.Main) {
            currentPlayingView?.showThumbnail()
            currentPlayingView = null
            exoPlayer?.release()
            exoPlayer = null
        }
    }
}