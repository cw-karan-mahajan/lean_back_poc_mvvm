package com.example.leanbackpocmvvm.views.customview

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.bumptech.glide.Glide
import com.example.leanbackpocmvvm.R
import com.example.leanbackpocmvvm.databinding.ItemNewCardViewBinding
import com.example.leanbackpocmvvm.utils.ExoPlayerManager
import com.example.leanbackpocmvvm.views.viewmodel.ContentData
import com.example.leanbackpocmvvm.views.viewmodel.MainViewModel
import com.example.leanbackpocmvvm.views.viewmodel.VideoPlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@UnstableApi
class NewVideoCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ItemNewCardViewBinding
    private lateinit var exoPlayerManager: ExoPlayerManager
    private lateinit var coroutineScope: CoroutineScope

    init {
        binding = ItemNewCardViewBinding.inflate(LayoutInflater.from(context), this, true)
        setupView()
    }

    @OptIn(UnstableApi::class)
    private fun setupView() {
        isFocusable = true
        isFocusableInTouchMode = true
        clipChildren = false

        binding.focusOverlay.background = ContextCompat.getDrawable(context, R.drawable.focus_onselect_bg)
        binding.focusOverlay.visibility = View.INVISIBLE

        binding.playerView.useController = false
        binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        binding.playerView.visibility = View.GONE

        setOnFocusChangeListener { _, hasFocus ->
            updateFocusOverlayVisibility(hasFocus)
        }
    }

    fun initialize(exoPlayerManager: ExoPlayerManager, coroutineScope: CoroutineScope) {
        this.exoPlayerManager = exoPlayerManager
        this.coroutineScope = coroutineScope
    }

    fun bind(item: MainViewModel.CustomRowItemX, videoPlaybackState: VideoPlaybackState) {
        setContent(item.contentData)
        updateVideoPlaybackState(videoPlaybackState)
    }

    private fun setContent(contentData: ContentData) {
        setImage(contentData.imageUrl, contentData.width, contentData.height)
        setMainImageDimensions(contentData.isLandscape, contentData.isPortrait, contentData.width, contentData.height)
    }

    private fun setImage(imageUrl: String, width: Int, height: Int) {
        val imgStringWithHttps = imageUrl.replace("http://", "https://")
        Glide.with(context)
            .load(imgStringWithHttps)
            .centerCrop()
            .override(width, height)
            .into(binding.imageView)
    }

    private fun setMainImageDimensions(isLandscape: Boolean, isPortrait: Boolean, width: Int, height: Int) {
        val dpWidth = dpToPx(context, width)
        val dpHeight = dpToPx(context, height)

        val stretchedWidth = if (!isLandscape) dpWidth else (dpWidth * 2.5).toInt()
        val stretchedHeight = if (!isLandscape) 600 else (dpWidth * 1.5).toInt()

        resizeCard(dpWidth, dpHeight, stretchedWidth, stretchedHeight, false)
    }

    private fun resizeCard(originalWidth: Int, originalHeight: Int, stretchedWidth: Int, stretchedHeight: Int, stretch: Boolean) {
        val targetWidth = if (stretch) stretchedWidth else originalWidth
        val targetHeight = if (stretch) stretchedHeight else originalHeight

        layoutParams = LayoutParams(targetWidth, targetHeight)
        binding.innerLayout.layoutParams = LayoutParams(targetWidth, targetHeight)
        binding.imageView.layoutParams = LayoutParams(targetWidth, targetHeight)
        binding.playerView.layoutParams = LayoutParams(targetWidth, targetHeight)
        binding.focusOverlay.layoutParams = LayoutParams(targetWidth, targetHeight)
    }

    fun updateVideoPlaybackState(state: VideoPlaybackState) {
        when (state) {
            is VideoPlaybackState.Playing -> {
                if (state.itemId == tag) {
                    coroutineScope.launch(Dispatchers.Main) {
                        exoPlayerManager.playVideo(state.videoUrl, this@NewVideoCardView) {
                            showThumbnail()
                        }
                    }
                } else {
                    showThumbnail()
                }
            }
            is VideoPlaybackState.Stopped -> showThumbnail()
        }
    }

    fun showVideo() {
        binding.playerView.visibility = View.VISIBLE
        binding.imageView.visibility = View.GONE
    }

    fun showThumbnail() {
        binding.playerView.visibility = View.GONE
        binding.imageView.visibility = View.VISIBLE
    }

    private fun updateFocusOverlayVisibility(isVisible: Boolean) {
        binding.focusOverlay.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
    }

    fun getPlayerView() = binding.playerView
}

fun dpToPx(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
}