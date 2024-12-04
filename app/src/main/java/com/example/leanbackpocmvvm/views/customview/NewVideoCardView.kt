package com.example.leanbackpocmvvm.views.customview

import android.animation.ValueAnimator
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.R
import com.example.leanbackpocmvvm.application.GlideApp
import com.example.leanbackpocmvvm.utils.dpToPx
import com.example.leanbackpocmvvm.views.activity.MainActivity
import com.example.leanbackpocmvvm.views.viewmodel.CustomRowItemX
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
class NewVideoCardView(context: Context) : FrameLayout(context) {
    private val thumbnailImageView: ImageView
    private val posterImageView: ImageView
    val videoPlaceholder: FrameLayout
    private val innerLayout: FrameLayout
    private var isVideoPlaying = false
    private var thumbnailOverlay: FrameLayout
    private var hasVideoStarted = false

    private var originalWidth: Int = 0
    private var originalHeight: Int = 0
    private var stretchedWidth: Int = 0
    private var stretchedHeight: Int = 0
    private lateinit var lifecycleOwner: LifecycleOwner
    private var isViewAttached = false
    var customItem: CustomRowItemX? = null
    private var isInAdSequence = false
    private var currentAnimator: ValueAnimator? = null
    private var isPreparingNextInSequence = false

    // Progress tracking components
    private var progressOverlay: View
    private var progressBar: ProgressBar
    private var adCounterText: TextView
    private var durationText: TextView
    private var progressUpdateJob: Job? = null
    private var currentAdDuration: Long = 0
    private var currentAdNumber: Int = 0
    private var totalAds: Int = 0

    private val ANIMATION_DURATION = 300L

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

        // Initialize progress overlay
        progressOverlay = LayoutInflater.from(context).inflate(R.layout.progress_overlay, this, false)
        progressBar = progressOverlay.findViewById(R.id.adProgress)
        adCounterText = progressOverlay.findViewById(R.id.adCounterText)
        durationText = progressOverlay.findViewById(R.id.durationText)
        progressOverlay.visibility = View.GONE
        addView(progressOverlay)

        setOnFocusChangeListener { _, hasFocus ->
            Log.d(
                TAG,
                "Focus changed: $hasFocus, isVideoPlaying: $isVideoPlaying, hasVideoStarted: $hasVideoStarted"
            )
            updateFocusOverlayVisibility(hasFocus)

            if (!hasVideoStarted) {
                resizeCard(false)
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
        currentAnimator?.cancel()
        currentAnimator = null
        progressUpdateJob?.cancel()
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

    private fun animateCardSize(stretch: Boolean, onComplete: (() -> Unit)? = null) {
        val startWidth = width
        val startHeight = height
        val targetWidth = if (stretch) stretchedWidth else originalWidth
        val targetHeight = if (stretch) stretchedHeight else originalHeight

        currentAnimator?.cancel()

        currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                val currentWidth = startWidth + ((targetWidth - startWidth) * progress).toInt()
                val currentHeight = startHeight + ((targetHeight - startHeight) * progress).toInt()

                updateCardSize(currentWidth, currentHeight)
            }

            doOnEnd {
                onComplete?.invoke()
                currentAnimator = null
            }

            start()
        }
    }

    private fun updateCardSize(width: Int, height: Int) {
        val params = layoutParams
        params.width = width
        params.height = height
        layoutParams = params

        innerLayout.layoutParams = LayoutParams(width, height)
        thumbnailImageView.layoutParams = LayoutParams(width, height)
        posterImageView.layoutParams = LayoutParams(width, height)
        videoPlaceholder.layoutParams = LayoutParams(width, height)
        thumbnailOverlay.layoutParams = LayoutParams(width, height)
        progressOverlay.layoutParams = LayoutParams(width, LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.BOTTOM
        }
    }

    fun updateAdProgress(currentPosition: Long, duration: Long, adNumber: Int, totalAdCount: Int) {
        currentAdDuration = duration
        currentAdNumber = adNumber
        totalAds = totalAdCount

        progressOverlay.visibility = View.VISIBLE

        // Convert to seconds and ensure logical progress
        val durationSeconds = (duration / 1000).toInt()
        val currentSeconds = (currentPosition / 1000).toInt()
        progressBar.max = durationSeconds
        progressBar.progress = currentSeconds

        val remainingSeconds = (durationSeconds - currentSeconds).coerceAtLeast(0)
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60

        adCounterText.text = "Ad $adNumber of $totalAdCount"
        durationText.text = " (${minutes}:${String.format("%02d", seconds)})"
    }

    private fun startProgressTracking(duration: Long) {
        progressUpdateJob?.cancel()
        progressUpdateJob = lifecycleOwner.lifecycleScope.launch {
            try {
                val durationSeconds = (duration / 1000).toInt()
                progressBar.max = durationSeconds
                var elapsedSeconds = 0

                while (isActive && elapsedSeconds <= durationSeconds) {
                    val remainingSeconds = (durationSeconds - elapsedSeconds).coerceAtLeast(0)
                    val minutes = remainingSeconds / 60
                    val seconds = remainingSeconds % 60
                    progressBar.progress = elapsedSeconds
                    durationText.text = " (${minutes}:${String.format("%02d", seconds)})"

                    delay(1000)
                    elapsedSeconds++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in progress tracking: ${e.message}")
            }
        }
    }

    fun prepareForVideoPlayback(isPartOfSequence: Boolean = false) {
        Log.d(TAG, "Preparing for video playback, isPartOfSequence: $isPartOfSequence")
        isInAdSequence = isPartOfSequence
        isPreparingNextInSequence = isPartOfSequence && hasVideoStarted

        videoPlaceholder.visibility = View.VISIBLE
        thumbnailImageView.visibility = View.GONE
        posterImageView.visibility = View.GONE

        if (!isPreparingNextInSequence) {
            setupThumbnailOverlay()
        }
    }

    private fun setupThumbnailOverlay() {
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

    fun startVideoPlayback(adNumber: Int = 1, totalAdCount: Int = 1, duration: Long = 0) {
        Log.d(TAG, "Starting video playback")
        isVideoPlaying = true
        hasVideoStarted = true
        updateFocusOverlayVisibility(true)

        if (duration > 0) {
            progressOverlay.visibility = View.VISIBLE
            adCounterText.text = "Ad $adNumber of $totalAdCount"
            progressBar.max = duration.toInt()
            progressBar.progress = 0
            startProgressTracking(duration)
        }

        if (!isPreparingNextInSequence) {
            animateCardSize(true) {
                thumbnailOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        thumbnailOverlay.visibility = View.GONE
                        thumbnailOverlay.alpha = 1f
                    }
                    .start()
            }
        } else {
            updateCardSize(stretchedWidth, stretchedHeight)
            thumbnailOverlay.visibility = View.GONE
        }
    }

    fun endVideoPlayback(shouldResetState: Boolean) {
        if (isInAdSequence) {
            isPreparingNextInSequence = true
            videoPlaceholder.visibility = View.VISIBLE
        } else if (shouldResetState) {
            progressUpdateJob?.cancel()
            progressOverlay.visibility = View.GONE
            isVideoPlaying = false
            hasVideoStarted = false
            isPreparingNextInSequence = false
            animateCardSize(false) {
                resetCardState(skipAnimation = true)
            }
        }
    }

    private fun resizeCard(stretch: Boolean) {
        val targetWidth = if (stretch) stretchedWidth else originalWidth
        val targetHeight = if (stretch) stretchedHeight else originalHeight
        updateCardSize(targetWidth, targetHeight)
        updateFocusOverlayVisibility(isFocused)
    }

    fun showThumbnail() {
        videoPlaceholder.visibility = View.GONE
        posterImageView.visibility = View.GONE
        thumbnailImageView.visibility = View.VISIBLE
        thumbnailOverlay.visibility = View.GONE
        progressOverlay.visibility = View.GONE
    }

    private fun updateFocusOverlayVisibility(hasFocus: Boolean) {
        Log.d(TAG, "updateFocusOverlay hasFocus $hasFocus isVideoPlaying $isVideoPlaying")
        val shouldBeVisible = hasFocus || isVideoPlaying
        lifecycleOwner.lifecycleScope.launch {
            background = if (shouldBeVisible)
                ContextCompat.getDrawable(context, R.drawable.itemview_background_focused)
            else
                ContextCompat.getDrawable(context, R.drawable.focus_onselect_bg)
        }
    }

    fun shrinkCard() {
        lifecycleOwner.lifecycleScope.launch {
            resizeCard(false)
        }
    }

    fun resetCardState(skipAnimation: Boolean = false) {
        Log.d(TAG, "Resetting card state")
        isVideoPlaying = false
        isInAdSequence = false
        hasVideoStarted = false
        videoPlaceholder.visibility = View.GONE
        posterImageView.visibility = View.GONE
        thumbnailImageView.visibility = View.VISIBLE
        thumbnailOverlay.visibility = View.GONE
        progressOverlay.visibility = View.GONE
        progressUpdateJob?.cancel()

        if (skipAnimation) {
            resizeCard(false)
        } else {
            animateCardSize(false)
        }

        updateFocusOverlayVisibility(isFocused)
    }

    fun updateProgressVisibility(visible: Boolean) {
        progressOverlay.visibility = if (visible) View.VISIBLE else View.GONE
    }

    companion object {
        private const val TAG = "NewVideoCardView"
    }
}