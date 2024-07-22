package com.example.leanbackpocmvvm.views.customview

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.example.leanbackpocmvvm.R
import com.example.leanbackpocmvvm.models.VideoTileItem
import com.example.leanbackpocmvvm.views.fragment.VideoTileFragment
import com.example.leanbackpocmvvm.views.viewmodel.MainViewModel.Companion.TAG
import com.example.leanbackpocmvvm.views.viewmodel.VideoTileViewModel

class VideoCardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val thumbnailView: ImageView
    val playerView: PlayerView
    lateinit var videoTileItem: VideoTileItem

    init {
        LayoutInflater.from(context).inflate(R.layout.video_card_view, this, true)
        thumbnailView = findViewById(R.id.thumbnailView)
        playerView = findViewById(R.id.playerView)

        playerView.useController = false
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM

        layoutParams = LayoutParams(
            400,
            400
        )
    }

    fun setContent(item: VideoTileItem) {
        videoTileItem = item
        Glide.with(context)
            .load(item.poster)
            .into(thumbnailView)
    }

    fun showVideo() {
        post {
            thumbnailView.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            playerView.alpha = 1f // Ensure full opacity
            Log.d(TAG, "Showing video, PlayerView visibility: ${playerView.visibility}")
        }
    }

    fun showThumbnail() {
        post {
            playerView.visibility = View.GONE
            thumbnailView.visibility = View.VISIBLE
            Log.d(TAG, "Showing thumbnail, ThumbnailView visibility: ${thumbnailView.visibility}")
        }
    }

    fun resetCardState() {
        showThumbnail()
        playerView.player = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        playerView.player = null
    }

    companion object {
        private const val TAG = "VideoCardView"
    }
}