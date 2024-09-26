package com.example.leanbackpocmvvm.views.exoplayer

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.AdsConfiguration
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.leanbackpocmvvm.utils.isAndroidVersion9Supported
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
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
    private var isPlayingVideo = AtomicBoolean(false)
    private var hasVideoEnded = AtomicBoolean(false)
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

    private val STATIC_ASSET_FILE = "one_sec_video.mp4"
    private val isReleasing = AtomicBoolean(false)
    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun getOrCreatePlayer(): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = createExoPlayer()
        }
        return exoPlayer!!
    }

    fun prepareVideo(
        videoUrl: String, adsVideoUrl: String?, tileType: String, playerView: PlayerView,
        onReady: (Boolean) -> Unit, onEnded: () -> Unit
    ) {
        playerScope.launch {
            if (exoPlayer == null || exoPlayer?.playbackState == Player.STATE_IDLE) {
                reinitializePlayer()
            }

            try {
                while (isReleasing.get()) {
                    delay(100)
                }

                if (isPlayingVideo.get()) {
                    releasePlayer()
                }

                val player = getOrCreatePlayer()
                playerView.player = player

                when {
                    tileType == "typeAdsVideo" && !adsVideoUrl.isNullOrEmpty() -> {
                        setupImaAdsLoader(onEnded)
                        prepareImaAdsVideo(adsVideoUrl, playerView, videoUrl, onReady, onEnded)
                    }

                    else -> {
                        prepareRegularVideo(videoUrl)
                    }
                }

                playerListener(player, onReady, onEnded, videoUrl)

                currentVideoUrl = videoUrl
                player.playWhenReady = true
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing video: ${e.message}")
                onReady(false)
            }
        }
    }

    private fun playerListener(
        player: ExoPlayer, onReady: (Boolean) -> Unit, onEnded: () -> Unit,
        videoUrl: String
    ) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        onReady(true)
                        isPlayingVideo.set(true)
                        hasVideoEnded.set(false)
                    }

                    Player.STATE_ENDED -> {
                        onEnded()
                        hasVideoEnded.set(true)
                        isPlayingVideo.set(false)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error: ${error.message}", error)
                onReady(false)
                handlePlayerError(error, player, videoUrl)
            }
        })
    }

    private fun setupImaAdsLoader(onEnded: () -> Unit) {
        adsLoader = ImaAdsLoader.Builder(context)
            .setDebugModeEnabled(true)
            .setAdEventListener(buildAdEventListener(onEnded))
            .build()
    }

    private fun buildAdEventListener(onEnded: () -> Unit): AdEventListener {
        return AdEventListener { event: AdEvent ->
            val eventType = event.type
            /* if (eventType != AdEventType.AD_PROGRESS) {
                 Log.i(TAG, "IMA event: $eventType")
             } else */
            if (eventType == AdEventType.ALL_ADS_COMPLETED) {
                //adsLoader?.release()
                //adsLoader?.setPlayer(null)
                //onEnded()
                //hasVideoEnded.set(true)
                //isPlayingVideo.set(false)
                //adsLoader?.release()
                //exoPlayer?.pause()

            }
        }
    }

    private fun prepareImaAdsVideo(
        adsVideoUrl: String, playerView: PlayerView, videoUrl: String, onReady: (Boolean) -> Unit,
        onEnded: () -> Unit
    ) {
        val mediaSourceFactory: MediaSource.Factory =
            DefaultMediaSourceFactory(context).setLocalAdInsertionComponents( { unusedAdTagUri: AdsConfiguration? -> adsLoader }, playerView)

        // Create an ExoPlayer and set it as the player for content and ads.
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        exoPlayer = player
        playerView.player = player
        adsLoader?.setPlayer(player)

        // Create the MediaItem to play, specifying the content URI and ad tag URI.
        //"https://storage.googleapis.com/gvabox/media/samples/stock.mp4"
        val contentUri = Uri.parse("asset:///$STATIC_ASSET_FILE")
        val adTagUri = Uri.parse(adsVideoUrl)
        val mediaItem = MediaItem.Builder()
            .setUri(contentUri)
            .setAdsConfiguration(AdsConfiguration.Builder(adTagUri).build())
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()

        // Add listener to the newly created player instance
        playerListener(player, onReady, onEnded, videoUrl)

        player.playWhenReady = true
    }

    private fun prepareRegularVideo(videoUrl: String) {
        val upstreamFactory = DefaultDataSource.Factory(context)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
        val source = ProgressiveMediaSource.Factory(cacheDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoUrl))

        exoPlayer?.setMediaSource(source)
        exoPlayer?.prepare()
    }

    private fun handlePlayerError(error: PlaybackException, player: ExoPlayer, videoUrl: String) {
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

    fun preloadVideo(videoUrl: String) {
        playerScope.launch(Dispatchers.IO) {
            try {
                val dataSourceFactory = DefaultDataSource.Factory(context)
                val cacheDataSourceFactory = CacheDataSource.Factory()
                    .setCache(simpleCache)
                    .setUpstreamDataSourceFactory(dataSourceFactory)
                val mediaSource = ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(videoUrl))

                withContext(Dispatchers.Main) {
                    val player = getOrCreatePlayer()
                    player.setMediaSource(mediaSource)
                    player.prepare()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading video: ${e.message}")
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
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
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

    fun setVideoSurface(surface: Surface) {
        playerScope.launch {
            exoPlayer?.setVideoSurface(surface)
        }
    }

    fun releasePlayer() {
        if (isReleasing.getAndSet(true)) {
            return
        }
        playerScope.launch {
            try {
                adsLoader?.setPlayer(null)
                exoPlayer?.run {
                    stop()
                    clearMediaItems()
                    release()
                }
                adsLoader?.release()
            } finally {
                exoPlayer = null
                adsLoader = null
                isPlayingVideo.set(false)
                hasVideoEnded.set(false)
                isReleasing.set(false)
                currentVideoUrl = ""
            }
        }
    }

    fun reinitializePlayer() {
        releasePlayer()
        exoPlayer = createExoPlayer()
    }

    fun isPlayerReleased(): Boolean = exoPlayer == null

    fun onLifecycleDestroy() {
        releasePlayer()
        playerScope.cancel()
    }

    companion object {
        const val TAG = "ExoPlayerManager"
    }
}