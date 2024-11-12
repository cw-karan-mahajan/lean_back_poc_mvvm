/*
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

    //private val focusOverlay: View
    private var thumbnailOverlay: FrameLayout
//    private val loaderView: ProgressBar

    private var originalWidth: Int = 0
    private var originalHeight: Int = 0
    private var stretchedWidth: Int = 0
    private var stretchedHeight: Int = 0
    private var exoPlayerManager: ExoPlayerManager? = null
    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var mainViewModel: MainViewModel
    var mTileId: String? = null
    private var isViewAttached = false
    var customItem: CustomRowItemX? = null

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

//        focusOverlay = View(context).apply {
//            layoutParams = LayoutParams(
//                ViewGroup.LayoutParams.MATCH_PARENT,
//                ViewGroup.LayoutParams.MATCH_PARENT
//            )
//            background = ContextCompat.getDrawable(context, R.drawable.focus_onselect_bg)
//            visibility = GONE
//        }
//        innerLayout.addView(focusOverlay)

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

    fun setTileId(tileId: String) {
        this.mTileId = tileId
    }

    fun setLifecycleOwner(owner: LifecycleOwner) {
        this.lifecycleOwner = owner
    }

    fun setMainViewModel(viewModel: MainViewModel) {
        this.mainViewModel = viewModel
    }

    fun setImage(imageUrl: String?, width: Int, height: Int, isAdImage: Boolean = false) {
        GlideApp.with(context).clear(thumbnailImageView)
        GlideApp.with(context).clear(posterImageView)
        thumbnailImageView.setImageDrawable(null)
        posterImageView.setImageDrawable(null)
        val mImageUrl = imageUrl?.replace("http://", "https://") ?: ""

        if (imageUrl != null) {
            loadRegularImage(mImageUrl, width, height)
        }
        */
/*if (isAdImage) {
            if (imageUrl != null) {
                loadAdImage(mImageUrl, width, height)
            } else {
                // Set a placeholder image here if needed
            }
        } else {
            if (imageUrl != null) {
                loadRegularImage(mImageUrl, width, height)
            } else {
                // Set a placeholder image here if needed
            }
        }*//*

    }

    private fun loadCurrentImage() {
        customItem?.let { item ->
            val isAdImage = item.rowItemX.adsServer != null
            val imageUrl = if (isAdImage) item.rowItemX.adImageUrl else item.contentData.imageUrl
            setImage(
                imageUrl,
                item.contentData.width,
                item.contentData.height,
                isAdImage
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

    fun setExoPlayerManager(manager: ExoPlayerManager) {
        this.exoPlayerManager = manager
    }

    fun setMainImageDimensions(isReqStretched: Boolean, isPortrait: Boolean, w: Int, h: Int) {
        val width = dpToPx(context, w)
        val height = dpToPx(context, h)

        originalWidth = width
        originalHeight = height
        Log.d(TAG, "OriginalHeight $originalHeight  originalWidth $originalWidth")
        stretchedWidth = if (isReqStretched) width else (width * 2.5).toInt()
        stretchedHeight = if (isReqStretched) 600 else (width * 1.5).toInt()

        Log.d(TAG, "Height $stretchedHeight  width $stretchedWidth")

        resizeCard(false) // Initially set to non-stretched size
    }

    fun prepareForVideoPlayback() {
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
        Log.d(TAG, "startVideoPlayback")
        isVideoPlaying = true

        stretchCard()
        Log.d(TAG, "startVideoPlayback updateFocusOverlayVisibility true hard coded")
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
        Log.d(TAG, "endVideoPlayback")
        showThumbnail()
        shrinkCard()
        isVideoPlaying = false
        Log.d(TAG, "endVideoPlayback updateFocusOverlayVisibility From isFocused  $isFocused")
        updateFocusOverlayVisibility(isFocused)

        videoPlaceholder.removeAllViews()
        videoPlaceholder.visibility = View.GONE
    }

    private fun resizeCard(stretch: Boolean) {
        Log.d(TAG, "---resizeCard $stretch")
        val targetWidth = if (stretch) stretchedWidth else originalWidth
        val targetHeight = if (stretch) stretchedHeight else originalHeight

        val params = layoutParams
        params.width = targetWidth
        params.height = targetHeight
        layoutParams = params

        innerLayout.layoutParams = LayoutParams(targetWidth, targetHeight)
        thumbnailImageView.layoutParams = LayoutParams(targetWidth, targetHeight)
        posterImageView.layoutParams = LayoutParams(targetWidth, targetHeight)
        videoPlaceholder.layoutParams = LayoutParams(targetWidth - 5 , targetHeight -5 )
//        focusOverlay.layoutParams = LayoutParams(targetWidth, targetHeight)
        thumbnailOverlay.layoutParams = LayoutParams(targetWidth, targetHeight)
        Log.d(TAG, "resizeCard ---- updateFocusOverlayVisibility isFocused $isFocused  ")
        updateFocusOverlayVisibility(isFocused)
    }

    private fun stretchCard() {
        lifecycleOwner.lifecycleScope.launch {
            Log.d(TAG, "stretchCard true")
            resizeCard(true)
        }
    }

    fun shrinkCard() {
        lifecycleOwner.lifecycleScope.launch {
            isVideoPlaying = false
            Log.d(TAG, "Shrinking card false")
            resizeCard(false)
        }
    }

    fun showThumbnail() {
        videoPlaceholder.visibility = View.GONE
        posterImageView.visibility = View.GONE
        thumbnailImageView.visibility = View.VISIBLE
        thumbnailOverlay.visibility = View.GONE
        //loaderView.visibility = View.GONE
    }

    private fun updateFocusOverlayVisibility(hasFocus: Boolean) {
        Log.d(TAG, "updateFocusOverlay hasFocus $hasFocus  isVideoPlaying $isVideoPlaying")
        val shouldBeVisible = hasFocus || isVideoPlaying
        lifecycleOwner.lifecycleScope.launch {
//      focusOverlay.visibility = if (shouldBeVisible) View.VISIBLE else View.INVISIBLE
            background = if (shouldBeVisible) ContextCompat.getDrawable(
                context, R.drawable.itemview_background_focused
            ) else ContextCompat.getDrawable(context, R.drawable.focus_onselect_bg)
            Log.d(TAG, "updateFocusOverlay " + if (shouldBeVisible) View.VISIBLE else View.INVISIBLE)
        }
    }

    fun resetCardState() {
        isVideoPlaying = false
        videoPlaceholder.visibility = View.GONE
        posterImageView.visibility = View.GONE
        thumbnailImageView.visibility = View.VISIBLE
        thumbnailOverlay.visibility = View.GONE
        //loaderView.visibility = View.GONE
        shrinkCard()
        updateFocusOverlayVisibility(isFocused)
    }

    companion object {
        const val TAG = "NewVideoCardView"
    }
}*/
