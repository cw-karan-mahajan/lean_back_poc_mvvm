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
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
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
import com.example.leanbackpocmvvm.utils.dpToPx
import com.example.leanbackpocmvvm.views.viewmodel.MainViewModel
import com.example.leanbackpocmvvm.views.viewmodel.VideoPlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi
class NewVideoCardView(context: Context) : FrameLayout(context) {
    private val imageView: ImageView
    private var playerView: PlayerView
    private val innerLayout: FrameLayout
    private var videoSurface: Surface? = null
    var isVideoPlaying = false
    private val focusOverlay: View

    private var originalWidth: Int = 0
    private var originalHeight: Int = 0
    private var stretchedWidth: Int = 0
    private var stretchedHeight: Int = 0
    private var exoPlayerManager: ExoPlayerManager? = null
    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var mainViewModel: MainViewModel
    private lateinit var tileId:String

    init {
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

        playerView = createPlayerView()
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

        addPlayerViewAttachStateListener()
    }

    private fun createPlayerView(): PlayerView {
        return PlayerView(context).apply {
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
    }

    private fun addPlayerViewAttachStateListener() {
        playerView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                updateVideoSurface()
            }

            override fun onViewDetachedFromWindow(v: View) {
                videoSurface = null
            }
        })
    }

    fun setTileId(tileId:String) {
        this.tileId = tileId
    }

    fun setLifecycleOwner(owner: LifecycleOwner) {
        this.lifecycleOwner = owner
    }

    fun setMainViewModel(viewModel: MainViewModel) {
        this.mainViewModel = viewModel
    }

    fun setImage(imageUrl: String, width: Int, height: Int) {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            Glide.with(context)
                .load(imageUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .override(width, height)
                .into(imageView)
        }
    }

    fun setExoPlayerManager(manager: ExoPlayerManager) {
        this.exoPlayerManager = manager
    }

    fun updateVideoPlaybackState(state: VideoPlaybackState) {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            when (state) {
                is VideoPlaybackState.Playing -> {
                    if (state.itemId == tag) {
                        exoPlayerManager?.playVideo(state.videoUrl, this@NewVideoCardView, tileId)
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
    }

    fun setMainImageDimensions(isReqStretched: Boolean, isPortrait: Boolean, w: Int, h: Int) {
        val width = dpToPx(context, w)
        val height = dpToPx(context, h)
        originalWidth = width
        originalHeight = height

        stretchedWidth = if (isReqStretched) width else (width * 2.5).toInt()
        stretchedHeight = if (isReqStretched) 600 else (width * 1.5).toInt()

        resizeCard(false) // Initially set to non-stretched size
    }

    fun prepareForVideoPlayback() {
        Log.d(TAG, "Preparing for video playback")
        playerView.visibility = View.VISIBLE
        playerView.alpha = 1f // Make it fully visible
        requestLayout()
        invalidate()
    }

    fun startVideoPlayback(player: ExoPlayer) {
        Log.d(TAG, "startVideoPlayback called")
        playerView.player = player
        playerView.useController = false
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        playerView.visibility = View.VISIBLE
        imageView.visibility = View.GONE

        Log.d(TAG, "PlayerView dimensions: ${playerView.width}x${playerView.height}")
        Log.d(TAG, "PlayerView visibility: ${playerView.visibility == View.VISIBLE}")

        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                Log.d(TAG, "First frame rendered")
                player.removeListener(this)
                val animation = ObjectAnimator.ofFloat(playerView, "alpha", 0f, 1f).apply {
                    duration = 500
                }
                animation.start()
                animation.doOnEnd {
                    Log.d(TAG, "Fade-in animation completed")
                    ensureVideoVisible()
                    stretchCard()
                }
            }
        })

        isVideoPlaying = true
        updateFocusOverlayVisibility()
    }

    fun stopVideoPlayback() {
        playerView.player = null
        playerView.visibility = View.GONE
        showThumbnail()
        isVideoPlaying = false
        shrinkCard()
        updateFocusOverlayVisibility()
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
    }

    fun stretchCard() {
        if (isCardFocused()) {
            resizeCard(true)
        }
    }

    fun shrinkCard() {
        Log.d(TAG, "Shrinking card")
        resizeCard(false)
        requestLayout()
        invalidate()
    }

    private fun updateVideoSurface() {
        val surfaceView = playerView.videoSurfaceView as? SurfaceView
        surfaceView?.holder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.d(TAG, "Surface created")
                videoSurface = holder.surface
                exoPlayerManager?.setVideoSurface(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                Log.d(TAG, "Surface changed: format=$format, width=$width, height=$height")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.d(TAG, "Surface destroyed")
                videoSurface = null
            }
        })
    }

    fun getVideoSurface(): Surface? {
        return videoSurface
    }

    fun showThumbnail() {
        playerView.visibility = View.GONE
        imageView.visibility = View.VISIBLE
    }

    fun ensureVideoVisible() {
        playerView.visibility = View.VISIBLE
        imageView.visibility = View.GONE
    }

    private fun isCardFocused(): Boolean {
        return isFocused
    }

    fun updateFocusOverlayVisibility() {
        val shouldBeVisible = isCardFocused() || isVideoPlaying
        focusOverlay.visibility = if (shouldBeVisible) View.VISIBLE else View.INVISIBLE
    }

    fun onVideoEnded(tileId: String) {
        mainViewModel.onVideoEnded(tileId)
    }

    fun resetPlayerView() {
        playerView.player = null
        playerView.visibility = View.GONE
        showThumbnail()
        isVideoPlaying = false
    }

    fun getPlayerViewState(): String {
        return "Width: ${playerView.width}, Height: ${playerView.height}, " +
                "Visibility: ${playerView.visibility == View.VISIBLE}, " +
                "Is laid out: ${playerView.isLaidOut}, " +
                "Has surface: ${playerView.videoSurfaceView != null}"
    }

    companion object {
        private const val TAG = "NewVideoCardView"
    }
}