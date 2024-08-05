package com.example.leanbackpocmvvm.views.customview

import android.content.Context
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.R
import com.example.leanbackpocmvvm.application.GlideApp
import com.example.leanbackpocmvvm.utils.dpToPx
import com.example.leanbackpocmvvm.views.activity.MainActivity
import com.example.leanbackpocmvvm.views.exoplayer.ExoPlayerManager
import com.example.leanbackpocmvvm.views.viewmodel.CustomRowItemX
import com.example.leanbackpocmvvm.views.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@UnstableApi
class NewVideoCardView(context: Context) : FrameLayout(context) {
    private val thumbnailImageView: ImageView
    private val posterImageView: ImageView
    val videoPlaceholder: FrameLayout
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
    var mTileId: String? = null
    private var isViewAttached = false
    private var currentImageUrl: String? = null
    var customItem: CustomRowItemX? = null

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

        videoPlaceholder = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(3, 6, 3, 6)
            }
            visibility = View.GONE
        }
        innerLayout.addView(videoPlaceholder)

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
            updateFocusOverlayVisibility(hasFocus)
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
        this.mTileId = tileId
    }

    fun setLifecycleOwner(owner: LifecycleOwner) {
        this.lifecycleOwner = owner
    }

    fun setMainViewModel(viewModel: MainViewModel) {
        this.mainViewModel = viewModel
    }

    fun setImage(imageUrl: String, width: Int, height: Int) {
        GlideApp.with(context).clear(thumbnailImageView)
        GlideApp.with(context).clear(posterImageView)
        thumbnailImageView.setImageDrawable(null)
        posterImageView.setImageDrawable(null)

        currentImageUrl = imageUrl.replace("http://", "https://")
        loadCurrentImage(width, height)
    }

    private fun loadCurrentImage(width: Int = -1, height: Int = -1) {
        if (isViewAttached && currentImageUrl != null) {
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
        videoPlaceholder.visibility = View.VISIBLE
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

    fun startVideoPlayback() {
        Log.d(TAG, "startVideoPlayback called")
        isVideoPlaying = true
        stretchCard()
        updateFocusOverlayVisibility(true)

        thumbnailOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                thumbnailOverlay.visibility = View.GONE
                thumbnailOverlay.alpha = 1f
            }
            .start()
    }

    fun endVideoPlayback() {
        showThumbnail()
        shrinkCard()
        isVideoPlaying = false
        updateFocusOverlayVisibility(isFocused)
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
        videoPlaceholder.layoutParams = LayoutParams(targetWidth, targetHeight)
        focusOverlay.layoutParams = LayoutParams(targetWidth, targetHeight)
        thumbnailOverlay.layoutParams = LayoutParams(targetWidth, targetHeight)

        updateFocusOverlayVisibility(isFocused)
    }

    fun stretchCard() {
        lifecycleOwner.lifecycleScope.launch {
            resizeCard(true)
        }
    }

    fun shrinkCard() {
        lifecycleOwner.lifecycleScope.launch {
            Log.d(TAG, "Shrinking card")
            resizeCard(false)
        }
    }

    fun showThumbnail() {
        videoPlaceholder.visibility = View.GONE
        posterImageView.visibility = View.GONE
        thumbnailImageView.visibility = View.VISIBLE
        thumbnailOverlay.visibility = View.GONE
    }

    fun updateFocusOverlayVisibility(hasFocus: Boolean) {
        val shouldBeVisible = hasFocus || isVideoPlaying
        focusOverlay.visibility = if (shouldBeVisible) View.VISIBLE else View.INVISIBLE
    }

    fun resetCardState() {
        isVideoPlaying = false
        videoPlaceholder.visibility = View.GONE
        posterImageView.visibility = View.GONE
        thumbnailImageView.visibility = View.VISIBLE
        thumbnailOverlay.visibility = View.GONE
        shrinkCard()
        updateFocusOverlayVisibility(isFocused)
    }

    companion object {
        private const val TAG = "NewVideoCardView"
    }
}