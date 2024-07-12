package com.example.leanbackpocmvvm.views.presenter

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.leanback.widget.Presenter
import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.views.customview.NewVideoCardView
import com.example.leanbackpocmvvm.views.viewmodel.MainViewModel
import com.example.leanbackpocmvvm.views.viewmodel.VideoPlaybackState

@UnstableApi
class CardLayout1 : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = NewVideoCardView(parent.context)
            .apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val cardView = viewHolder.view as NewVideoCardView
        val customItem = item as? MainViewModel.CustomRowItemX
        if (customItem != null) {
            cardView.bind(customItem, VideoPlaybackState.Stopped)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as NewVideoCardView
        cardView.updateVideoPlaybackState(VideoPlaybackState.Stopped)
        // No need to do anything here, as video playback is managed by ViewModel
    }
}