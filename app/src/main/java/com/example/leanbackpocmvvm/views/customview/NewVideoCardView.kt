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
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.example.leanbackpocmvvm.R
import com.example.leanbackpocmvvm.application.GlideApp
import com.example.leanbackpocmvvm.utils.dpToPx
import com.example.leanbackpocmvvm.views.activity.MainActivity
import com.example.leanbackpocmvvm.views.exoplayer.ExoPlayerManager
import com.example.leanbackpocmvvm.views.viewmodel.CustomRowItemX
import com.example.leanbackpocmvvm.views.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class NewVideoCardView(context: Context) : FrameLayout(context) {
    private val thumbnailImageView: ImageView
    private val posterImageView: ImageView
    private var playerView: PlayerView
    private val innerLayout: FrameLayout
    private var videoSurface: Surface? = null
    var isVideoPlaying = false
    private val focusOverlay: View
    private var thumbnailOverlay: FrameLayout

    private var originalWidth: Int = 0
    private var originalHeight: Int = 0
    private var stretchedWidth: Int = 0
    private var stretchedHeight: Int = 0
    private var exoPlayerManager: ExoPlayerManager? = null
    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var mainViewModel: MainViewModel
    private lateinit var tileId: String
    private var isViewAttached = false
    private var currentImageUrl: String? = null

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

        thumbnailImageView = ImageView(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(3, 6, 3, 6)
            }
        }
        innerLayout.addView(thumbnailImageView)

        posterImageView = ImageView(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(3, 6, 3, 6)
            }
            visibility = View.GONE
        }
        innerLayout.addView(posterImageView)

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

        thumbnailOverlay = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }
        addView(thumbnailOverlay)

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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isViewAttached = true
        loadCurrentImage()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isViewAttached = false
        (context as? MainActivity)?.safelyUseGlide {
            GlideApp.with(context).clear(thumbnailImageView)
            GlideApp.with(context).clear(posterImageView)
        }
    }

    fun setTileId(tileId: String) {
        this.tileId = tileId
    }

    fun setLifecycleOwner(owner: LifecycleOwner) {
        this.lifecycleOwner = owner
    }

    fun setMainViewModel(viewModel: MainViewModel) {
        this.mainViewModel = viewModel
    }

    fun setImage(imageUrl: String, width: Int, height: Int) {
        //if (!isCardDestroying) {
            currentImageUrl = imageUrl.replace("http://", "https://")
            loadCurrentImage(width, height)
       // }
    }

//    fun setImage(imageUrl: String, width: Int, height: Int) {
//        // Cancel any ongoing image load for this ImageView
//        GlideApp.with(context).clear(thumbnailImageView)
//        GlideApp.with(context).clear(posterImageView)
//
//        // Set a placeholder or reset the ImageView
//        thumbnailImageView.setImageDrawable(null)
//        posterImageView.setImageDrawable(null)
//
//        // Load the new image
//        val imgUrl = imageUrl.replace("http://", "https://")
//        currentImageUrl = imgUrl
//
//        //loadImage(width, height, thumbnailImageView)
//        //loadImage(width, height, posterImageView)
//        loadCurrentImage(width, height)
//    }

    private fun loadCurrentImage(width: Int = -1, height: Int = -1) {
        if (isViewAttached &&  currentImageUrl != null) {
            (context as? MainActivity)?.safelyUseGlide {
                val requestBuilder = GlideApp.with(context)
                    .load(currentImageUrl)
                    .centerCrop()

                if (width > 0 && height > 0) {
                    requestBuilder.override(width, height)
                }

                requestBuilder.into(thumbnailImageView)
                requestBuilder.into(posterImageView)
            }
        }
    }

    fun stopVideoPlayback() {
        playerView.player = null
        playerView.visibility = View.GONE
        showThumbnail()
        isVideoPlaying = false
        shrinkCard()
        updateFocusOverlayVisibility()
        GlideApp.with(context).clear(thumbnailImageView)
        GlideApp.with(context).clear(posterImageView)
    }

    fun setExoPlayerManager(manager: ExoPlayerManager) {
        this.exoPlayerManager = manager
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
        playerView.alpha = 1f
        thumbnailImageView.visibility = View.GONE
        posterImageView.visibility = View.GONE

        // Create a copy of the thumbnail image
        val thumbnailCopy = ImageView(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageDrawable(thumbnailImageView.drawable)
        }

        thumbnailOverlay.removeAllViews()
        thumbnailOverlay.addView(thumbnailCopy)
        thumbnailOverlay.visibility = View.VISIBLE
    }

    fun startVideoPlayback(player: ExoPlayer) {
        Log.d(TAG, "startVideoPlayback called")
        playerView.player = player
        playerView.useController = false
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        playerView.visibility = View.VISIBLE

        Log.d(TAG, "PlayerView dimensions: ${playerView.width}x${playerView.height}")
        Log.d(TAG, "PlayerView visibility: ${playerView.visibility == View.VISIBLE}")

        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                Log.d(TAG, "First frame rendered")
                player.removeListener(this)
                thumbnailOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        thumbnailOverlay.visibility = View.GONE
                        thumbnailOverlay.alpha = 1f
                        ensureVideoVisible()
                        stretchCard()
                    }
                    .start()
            }
        })

        isVideoPlaying = true
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
        thumbnailImageView.layoutParams = LayoutParams(targetWidth, targetHeight)
        posterImageView.layoutParams = LayoutParams(targetWidth, targetHeight)
        playerView.layoutParams = LayoutParams(targetWidth, targetHeight)
        focusOverlay.layoutParams = LayoutParams(targetWidth, targetHeight)
        thumbnailOverlay.layoutParams = LayoutParams(targetWidth, targetHeight)

        updateFocusOverlayVisibility()
    }

    fun stretchCard() {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            if (isCardFocused()) {
                resizeCard(true)
            }
        }
    }

    fun shrinkCard() {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            Log.d(TAG, "Shrinking card")
            resizeCard(false)
        }
    }

    private fun updateVideoSurface() {
        val surfaceView = playerView.videoSurfaceView as? SurfaceView
        surfaceView?.holder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.d(TAG, "Surface created")
                videoSurface = holder.surface
                exoPlayerManager?.setVideoSurface(holder.surface)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
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
        posterImageView.visibility = View.GONE
        thumbnailImageView.visibility = View.VISIBLE
        thumbnailOverlay.visibility = View.GONE
    }

    fun ensureVideoVisible() {
        playerView.visibility = View.VISIBLE
        thumbnailImageView.visibility = View.GONE
        posterImageView.visibility = View.GONE
        thumbnailOverlay.visibility = View.GONE
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

    fun resetCardState() {
        isVideoPlaying = false
        playerView.visibility = View.GONE
        posterImageView.visibility = View.GONE
        thumbnailImageView.visibility = View.VISIBLE
        thumbnailOverlay.visibility = View.GONE
        shrinkCard()
        updateFocusOverlayVisibility()
    }

    fun resetPlayerView() {
        playerView.player = null
        playerView.visibility = View.GONE
        showThumbnail()
        isVideoPlaying = false
    }

    companion object {
        private const val TAG = "NewVideoCardView"
    }
}