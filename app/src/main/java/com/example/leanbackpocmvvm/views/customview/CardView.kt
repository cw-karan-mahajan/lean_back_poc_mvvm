package com.example.leanbackpocmvvm.views.customview

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
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.example.leanbackpocmvvm.R
import com.example.leanbackpocmvvm.utils.dpToPx
import com.example.leanbackpocmvvm.utils.isAndroidVersion9Supported
import com.example.leanbackpocmvvm.views.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi
class CardView(context: Context) : FrameLayout(context) {
    private val thumbnailImageView: ImageView
    private val posterImageView: ImageView

    //private var playerView: PlayerView
    private val innerLayout: FrameLayout
    private var videoSurface: Surface? = null
    var isVideoPlaying = false
    private val focusOverlay: View
    private var thumbnailOverlay: FrameLayout

    private var originalWidth: Int = 0
    private var originalHeight: Int = 0
    private var stretchedWidth: Int = 0
    private var stretchedHeight: Int = 0

    private lateinit var tileId: String
    private lateinit var lifecycleOwner: LifecycleOwner

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

        //playerView = createPlayerView()
        //innerLayout.addView(playerView)

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

//        setOnFocusChangeListener { _, hasFocus ->
//            Log.d(TAG, "Focus changed: $hasFocus")
//            updateFocusOverlayVisibility()
//            if (hasFocus) {
//                if (isVideoPlaying) {
//                    stretchCard()
//                } else {
//                    shrinkCard()
//                }
//            } else {
//                shrinkCard()
//            }
//        }

    }

    fun setLifecycleOwner(owner: LifecycleOwner) {
        this.lifecycleOwner = owner
    }

    fun setImage(imageUrl: String, width: Int, height: Int) {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            loadImage(imageUrl, width, height, thumbnailImageView)
            loadImage(imageUrl, width, height, posterImageView)
        }
    }

    private fun loadImage(imageUrl: String, width: Int, height: Int, imageView: ImageView) {
        val requestOptions = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .centerCrop()
            .override(width, height)
            .downsample(DownsampleStrategy.CENTER_INSIDE)
        if (isAndroidVersion9Supported()) {
            requestOptions.format(DecodeFormat.PREFER_RGB_565) // Uses less memory
        }

        //GlideApp.with(context).load(imageUrl).override(width, height).into(imageView)
        Glide.with(context)
            .load(imageUrl)
            .apply(requestOptions)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(imageView)
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

    private fun resizeCard(stretch: Boolean) {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            val targetWidth = if (stretch) stretchedWidth else originalWidth
            val targetHeight = if (stretch) stretchedHeight else originalHeight

            val params = layoutParams
            params.width = targetWidth
            params.height = targetHeight
            layoutParams = params

            innerLayout.layoutParams = LayoutParams(targetWidth, targetHeight)
            thumbnailImageView.layoutParams = LayoutParams(targetWidth, targetHeight)
            posterImageView.layoutParams = LayoutParams(targetWidth, targetHeight)
            //playerView.layoutParams = LayoutParams(targetWidth, targetHeight)
            focusOverlay.layoutParams = LayoutParams(targetWidth, targetHeight)
            thumbnailOverlay.layoutParams = LayoutParams(targetWidth, targetHeight)
            updateFocusOverlayVisibility()
        }
    }

    fun stretchCard() {
        if (isCardFocused()) {
            resizeCard(true)
        }
    }

    fun shrinkCard() {
        Log.d(TAG, "Shrinking card")
        resizeCard(false)
    }

    fun showThumbnail() {
        //playerView.visibility = View.GONE
        posterImageView.visibility = View.GONE
        thumbnailImageView.visibility = View.VISIBLE
        thumbnailOverlay.visibility = View.GONE
    }

    private fun isCardFocused(): Boolean {
        return isFocused
    }

   private fun updateFocusOverlayVisibility() {
        val shouldBeVisible = isCardFocused() || isVideoPlaying
        focusOverlay.visibility = if (shouldBeVisible) View.VISIBLE else View.INVISIBLE
    }

    companion object {
        private const val TAG = "NewVideoCardView"
    }
}