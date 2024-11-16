package com.example.leanbackpocmvvm.views.customview

import android.content.Context
import android.util.Log
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
import com.example.leanbackpocmvvm.views.viewmodel.CustomRowItemX
import kotlinx.coroutines.launch

@UnstableApi
class NewVideoCardView(context: Context) : FrameLayout(context) {
    private val thumbnailImageView: ImageView
    private val posterImageView: ImageView
    val videoPlaceholder: FrameLayout
    private val innerLayout: FrameLayout
    var isVideoPlaying = false
    private var thumbnailOverlay: FrameLayout

    private var originalWidth: Int = 0
    private var originalHeight: Int = 0
    private var stretchedWidth: Int = 0
    private var stretchedHeight: Int = 0
    private lateinit var lifecycleOwner: LifecycleOwner
    private var isViewAttached = false
    var customItem: CustomRowItemX? = null
    private var isInAdSequence = false

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        isFocusable = true
        isFocusableInTouchMode = true
        background = ContextCompat.getDrawable(context, R.drawable.focus_onselect_bg)

        innerLayout = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, 0)
            clipChildren = false
        }

        thumbnailImageView = ImageView(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(6, 6, 6, 6)
            }
        }
        innerLayout.addView(thumbnailImageView)

        posterImageView = ImageView(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(6, 6, 6, 6)
            }
            visibility = View.GONE
        }
        innerLayout.addView(posterImageView)

        videoPlaceholder = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(6, 6, 6, 6)
            }
            visibility = View.GONE
        }
        innerLayout.addView(videoPlaceholder)

        addView(innerLayout)
        clipChildren = false

        thumbnailOverlay = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }
        addView(thumbnailOverlay)

        setOnFocusChangeListener { _, hasFocus ->
            Log.d(
                TAG,
                "setOnFocusChangeListener hasFocus $hasFocus  isVideoPlaying $isVideoPlaying"
            )
            updateFocusOverlayVisibility(hasFocus)
            if (hasFocus) {
                if (isVideoPlaying) {
                    stretchCard()
                } else {
                    shrinkCard()
                }
            } else {
                if (!isVideoPlaying)
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

    fun setLifecycleOwner(owner: LifecycleOwner) {
        this.lifecycleOwner = owner
    }

    fun setImage(imageUrl: String?, width: Int, height: Int) {
        GlideApp.with(context).clear(thumbnailImageView)
        GlideApp.with(context).clear(posterImageView)
        thumbnailImageView.setImageDrawable(null)
        posterImageView.setImageDrawable(null)
        val mImageUrl = imageUrl?.replace("http://", "https://") ?: ""
        if (imageUrl != null) {
            loadRegularImage(mImageUrl, width, height)
        }

    }

    private fun loadCurrentImage() {
        customItem?.let { item ->
            val isAdImage = item.rowItemX.adsServer != null
            val imageUrl = if (isAdImage) item.rowItemX.adImageUrl else item.contentData.imageUrl
            setImage(
                imageUrl,
                item.contentData.width,
                item.contentData.height
            )
        }
    }

    private fun loadRegularImage(imageUrl: String, width: Int, height: Int) {
        if (isViewAttached) {
            (context as? MainActivity)?.safelyUseGlide {
                GlideApp.with(context)
                    .load(imageUrl)
                    .centerCrop()
                    .override(width, height)
                    .into(thumbnailImageView)
            }
        }
    }

    fun setMainImageDimensions(isReqStretched: Boolean, isPortrait: Boolean, w: Int, h: Int) {
        val width = dpToPx(context, w)
        val height = dpToPx(context, h)
        val adHeight = dpToPx(context, 640)
        originalWidth = width
        originalHeight = height

        stretchedWidth = if (isReqStretched) width else (width * 2.5).toInt()
        stretchedHeight = if (isReqStretched) adHeight else (width * 1.5).toInt()

        resizeCard(false) // Initially set to non-stretched size
    }

    fun prepareForVideoPlayback(isPartOfSequence: Boolean = false) {
        Log.d(TAG, "Preparing for video playback, isPartOfSequence: $isPartOfSequence")
        isInAdSequence = isPartOfSequence
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
        Log.d(TAG, "Starting video playback")
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

    fun endVideoPlayback(shouldResetState: Boolean) {
        Log.d(TAG, "Ending video playback, shouldResetState: $shouldResetState")
        if (shouldResetState) {
            resetCardState()
        } else if (isInAdSequence) {
            // Keep card stretched for next ad
            videoPlaceholder.visibility = View.VISIBLE
        }
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
        thumbnailOverlay.layoutParams = LayoutParams(targetWidth, targetHeight)
        updateFocusOverlayVisibility(isFocused)
    }

    private fun stretchCard() {
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

    private fun updateFocusOverlayVisibility(hasFocus: Boolean) {
        Log.d(TAG, "updateFocusOverlay hasFocus $hasFocus  isVideoPlaying $isVideoPlaying")
        val shouldBeVisible = hasFocus || isVideoPlaying
        lifecycleOwner.lifecycleScope.launch {
            background = if (shouldBeVisible) ContextCompat.getDrawable(
                context, R.drawable.itemview_background_focused
            ) else ContextCompat.getDrawable(context, R.drawable.focus_onselect_bg)
            Log.d(TAG, "updateFocusOverlay " + if (shouldBeVisible) View.VISIBLE else View.INVISIBLE)
        }
    }

    fun resetCardState() {
        Log.d(TAG, "Resetting card state")
        isVideoPlaying = false
        isInAdSequence = false
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