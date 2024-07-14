package com.example.leanbackpocmvvm.views.customview

import android.animation.ObjectAnimator
import android.content.Context
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.leanbackpocmvvm.R
import com.example.leanbackpocmvvm.utils.ExoPlayerManager
import com.example.leanbackpocmvvm.utils.ExoPlayerManager.Companion.TAG
import com.example.leanbackpocmvvm.utils.dpToPx
import com.example.leanbackpocmvvm.views.viewmodel.VideoPlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class NewVideoCardView(context: Context) : FrameLayout(context) {
    private val imageView: ImageView
    private val playerView: PlayerView
    private val innerLayout: FrameLayout
    private var videoSurface: Surface? = null
    var isVideoPlaying = false
    private val focusOverlay: View

    private var originalWidth: Int = 0
    private var originalHeight: Int = 0
    private var stretchedWidth: Int = 0
    private var stretchedHeight: Int = 0
    private var exoPlayerManager: ExoPlayerManager? = null

    init {
        //Log.d(TAG, "Initializing NewVideoCardView")
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        innerLayout = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(0, 0, 0, 0)
            clipChildren = false
        }

        imageView = ImageView(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(3, 6, 3, 6)
            }
        }
        innerLayout.addView(imageView)

        playerView = PlayerView(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(3, 6, 3, 6)
            }
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            visibility = GONE
        }
        innerLayout.addView(playerView)

        focusOverlay = View(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = ContextCompat.getDrawable(context, R.drawable.focus_onselect_bg)
            visibility = INVISIBLE
        }
        innerLayout.addView(focusOverlay)

        addView(innerLayout)
        clipChildren = false

        isFocusable = true
        isFocusableInTouchMode = true

        setOnFocusChangeListener { _, hasFocus ->
            Log.d(TAG, "Focus changed: $hasFocus")
            updateFocusOverlayVisibility()
            if (hasFocus) {
                if (isVideoPlaying) {
                    stretchCard()
                } else {
                    shrinkCard()
                }
            } else {
                shrinkCard()
            }
        }

        playerView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                //Log.d(TAG, "PlayerView attached to window")
                updateVideoSurface()
            }

            override fun onViewDetachedFromWindow(v: View) {
                //Log.d(TAG, "PlayerView detached from window")
                videoSurface = null
            }
        })
        //Log.d(TAG, "NewVideoCardView initialization complete")
    }

    fun setImage(imageUrl: String, width: Int, height: Int) {
        //Log.d(TAG, "Setting image: $imageUrl, width: $width, height: $height")

        Glide.with(context)
            .load(imageUrl)
            .transition(DrawableTransitionOptions.withCrossFade())
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .centerCrop()
            .override(width, height)
            .into(imageView)
    }

    fun setupForVideoPlayback(
        exoPlayerManager: ExoPlayerManager,
        videoUrl: String,
        onVideoEnded: () -> Unit
    ) {
        // This method will be called when we want to prepare the view for video playback
        //exoPlayerManager.playVideo(videoUrl, this, onVideoEnded)
    }

    fun setExoPlayerManager(manager: ExoPlayerManager) {
        this.exoPlayerManager = manager
    }

    fun updateVideoPlaybackState(state: VideoPlaybackState) {
        when (state) {
            is VideoPlaybackState.Playing -> {
                if (state.itemId == tag) {
                    exoPlayerManager?.playVideo(state.videoUrl, this) {
                        showThumbnail()
                    }
                } else {
                    stopVideoPlayback()
                    showThumbnail()
                }
            }

            is VideoPlaybackState.Stopped -> {
                stopVideoPlayback()
                showThumbnail()
            }
        }
    }

    fun setMainImageDimensions(isReqStretched: Boolean, isPortrait: Boolean, w: Int, h: Int) {
        val width = dpToPx(context, w)
        val height = dpToPx(context, h)
        originalWidth = width
        originalHeight = height

        //Log.d(TAG, "Calculate_Width : $width height $height")
        stretchedWidth = if (isReqStretched) width else (width * 2.5).toInt()
        stretchedHeight = if (isReqStretched) 600 else (width * 1.5).toInt()

        resizeCard(false) // Initially set to non-stretched size
    }

    fun startVideoPlayback(player: ExoPlayer) {
        //Log.d(TAG, "Starting video playback")
        playerView.player = player
        playerView.useController = false // Ensure controller is hidden
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM // or RESIZE_MODE_FIT
        playerView.visibility = View.VISIBLE
        imageView.visibility = View.GONE
        updateVideoSurface()
        playerView.alpha = 0f
        player.playWhenReady = true

        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                player.removeListener(this)
                val animation = ObjectAnimator.ofFloat(playerView, "alpha", 0f, 1f).apply {
                    duration = 500
                }
                animation.start()
                animation.doOnEnd {
                    ensureVideoVisible()
                    stretchCard()
                    imageView.visibility = View.GONE
                }
            }
        })

        isVideoPlaying = true
        updateFocusOverlayVisibility()
    }

    fun stopVideoPlayback() {
        //Log.d(TAG, "Stopping video playback")
        playerView.player = null
        playerView.visibility = View.GONE
        showThumbnail()
        isVideoPlaying = false
        shrinkCard()
        updateFocusOverlayVisibility()
        //exoPlayerManager?.releasePlayer()
    }

    private fun resizeCard(stretch: Boolean) {
        val targetWidth = if (stretch) stretchedWidth else originalWidth
        val targetHeight = if (stretch) stretchedHeight else originalHeight

        val params = layoutParams
        params.width = targetWidth
        params.height = targetHeight
        layoutParams = params

        innerLayout.layoutParams = LayoutParams(targetWidth, targetHeight)
        imageView.layoutParams = LayoutParams(targetWidth, targetHeight)
        playerView.layoutParams = LayoutParams(targetWidth, targetHeight)
        focusOverlay.layoutParams = LayoutParams(targetWidth, targetHeight)

        imageView.visibility = View.VISIBLE
        playerView.visibility = if (isVideoPlaying) View.VISIBLE else View.GONE
        updateFocusOverlayVisibility()
        //Log.d(TAG, "Card resized to: $targetWidth x $targetHeight")
    }

    fun stretchCard() {
        if (isCardFocused()) {
            //Log.d(TAG, "Stretching card")
            resizeCard(true)
        }
    }

    fun shrinkCard() {
        //Log.d(TAG, "Shrinking card")
        resizeCard(false)
    }

    private fun updateVideoSurface() {
        //Log.d(TAG, "Updating video surface")
        val surfaceView = playerView.videoSurfaceView as? SurfaceView
        surfaceView?.holder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                videoSurface = holder.surface
                exoPlayerManager?.setVideoSurface(holder.surface)
                //Log.d(TAG, "Surface created")
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                //Log.d(TAG, "Surface changed: $width x $height")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                videoSurface = null
                // Log.d(TAG, "Surface destroyed")
            }
        })
    }

    fun getVideoSurface(): Surface? {
        if (videoSurface == null || !videoSurface!!.isValid) {
            updateVideoSurface()
        }
        return videoSurface
    }

    fun showThumbnail() {
        //Log.d(TAG, "Showing thumbnail")
        playerView.visibility = View.GONE
        imageView.visibility = View.VISIBLE
    }

    fun ensureVideoVisible() {
        //Log.d(TAG, "Ensuring video is visible")
        playerView.visibility = View.VISIBLE
        imageView.visibility = View.GONE
    }

    private fun isCardFocused(): Boolean {
        return isFocused
    }

    fun updateFocusOverlayVisibility() {
        val shouldBeVisible = isCardFocused() || isVideoPlaying
        //Log.d(TAG, "Updating focus overlay visibility: $shouldBeVisible")
        focusOverlay.visibility = if (shouldBeVisible) View.VISIBLE else View.INVISIBLE
    }
}