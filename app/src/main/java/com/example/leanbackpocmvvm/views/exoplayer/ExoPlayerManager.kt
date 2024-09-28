package com.example.leanbackpocmvvm.views.exoplayer

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.leanbackpocmvvm.views.customview.NewVideoCardView
import com.google.ads.interactivemedia.v3.api.AdEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class ExoPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    private var adsLoader: ImaAdsLoader? = null
    private val isPlayingAd = AtomicBoolean(false)
    private val isReleasing = AtomicBoolean(false)
    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val STATIC_ASSET_FILE = "point_one_sec.mp4"

    private fun getOrCreateAdsLoader(onEnded: () -> Unit): ImaAdsLoader {
        return adsLoader ?: ImaAdsLoader.Builder(context)
            .setDebugModeEnabled(true)  // Set to false for production
            .setAdEventListener(buildAdEventListener(onEnded))
            .build().also { adsLoader = it }
    }

    private fun buildAdEventListener(onEnded: () -> Unit): AdEvent.AdEventListener = AdEvent.AdEventListener { event ->
        when (event.type) {
            //AdEvent.AdEventType.LOADED -> isPlayingAd.set(true)
            AdEvent.AdEventType.ALL_ADS_COMPLETED -> {
                isPlayingAd.set(false)
                onEnded()
            }
            else -> { /* Handle other ad events if necessary */ }
        }
    }

    fun prepareAd(
        adsVideoUrl: String,
        playerView: PlayerView,
        onReady: (Boolean) -> Unit,
        onEnded: () -> Unit
    ) {
        playerScope.launch {
            try {
                Log.d(NewVideoCardView.TAG, "---prepareAd" )
                val player = getOrCreatePlayer(playerView, onEnded)
                playerView.player = player
                playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)

                val contentUri = Uri.parse("asset:///$STATIC_ASSET_FILE")
                val adTagUri = Uri.parse(adsVideoUrl)
                val mediaItem = MediaItem.Builder()
                    .setUri(contentUri)
                    .setAdsConfiguration(MediaItem.AdsConfiguration.Builder(adTagUri).build())
                    .build()

                player.setMediaItem(mediaItem)
                player.prepare()
                //player.playWhenReady = true

                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> onReady(true)
                            //Player.STATE_ENDED -> onEnded()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Player error: ${error.message}")
                        onReady(false)
                        handlePlayerError(error, player, adsVideoUrl)
                    }
                })

                player.playWhenReady = true
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing ad: ${e.message}")
                onReady(false)
            }
        }
    }

    private fun getOrCreatePlayer(playerView: PlayerView, onEnded: () -> Unit): ExoPlayer {
        if (exoPlayer == null) {
            Log.d(NewVideoCardView.TAG, "---getOrCreatePlayer" )
            val adsLoader = getOrCreateAdsLoader(onEnded)
            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setLocalAdInsertionComponents({ adsLoader }, playerView)

            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH

            exoPlayer = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    videoScalingMode = C.VIDEO_SCALING_MODE_DEFAULT
                    repeatMode = Player.REPEAT_MODE_OFF
                }
            adsLoader.setPlayer(exoPlayer)
        }
        return exoPlayer!!
    }

    private fun handlePlayerError(error: PlaybackException, player: ExoPlayer, adsVideoUrl: String) {
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
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse("asset:///$STATIC_ASSET_FILE"))
                    .setAdsConfiguration(MediaItem.AdsConfiguration.Builder(Uri.parse(adsVideoUrl)).build())
                    .build()
                player.setMediaItem(mediaItem)
                player.prepare()
            }
        }
    }

    fun releasePlayer() {
        if (isReleasing.getAndSet(true)) return
        playerScope.launch {
            try {
                adsLoader?.setPlayer(null)
                adsLoader?.release()
                exoPlayer?.release()
            } finally {
                adsLoader = null
                exoPlayer = null
                isPlayingAd.set(false)
                isReleasing.set(false)
            }
        }
    }

    fun onLifecycleDestroy() {
        releasePlayer()
        adsLoader?.release()
        adsLoader = null
        playerScope.cancel()
    }

    companion object {
        const val TAG = "ExoPlayerManager"
    }
}