package com.example.leanbackpocmvvm.views.presenter

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.leanback.widget.Presenter
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.utils.ExoPlayerManager
import com.example.leanbackpocmvvm.views.customview.NewVideoCardView
import com.example.leanbackpocmvvm.views.viewmodel.MainViewModel
import com.example.leanbackpocmvvm.views.viewmodel.VideoPlaybackState

@UnstableApi
class CardLayout1(
    private val lifecycleOwner: LifecycleOwner,
    private val exoPlayerManager: ExoPlayerManager,
    private val mainViewModel: MainViewModel
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = NewVideoCardView(parent.context)
            .apply {
                isFocusable = true
                isFocusableInTouchMode = true
                setLifecycleOwner(lifecycleOwner)
                setExoPlayerManager(exoPlayerManager)
                setMainViewModel(mainViewModel)
            }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val cardView = viewHolder.view as NewVideoCardView
        val customItem = item as? MainViewModel.CustomRowItemX
        if (customItem != null) {
            cardView.setImage(
                customItem.contentData.imageUrl,
                customItem.contentData.width,
                customItem.contentData.height
            )
            cardView.setMainImageDimensions(
                customItem.contentData.isLandscape,
                customItem.contentData.isPortrait,
                customItem.contentData.width,
                customItem.contentData.height
            )
            cardView.tag = customItem.rowItemX.tid
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as NewVideoCardView
        cardView.stopVideoPlayback()
        //cardView.updateVideoPlaybackState(VideoPlaybackState.Stopped)
        // No need to do anything here, as video playback is managed by ViewModel
    }
}