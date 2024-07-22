package com.example.leanbackpocmvvm.views.presenter

import android.view.ViewGroup
import androidx.leanback.widget.Presenter
import androidx.lifecycle.LifecycleOwner
import com.example.leanbackpocmvvm.models.VideoTileItem
import com.example.leanbackpocmvvm.views.customview.VideoCardView
import com.example.leanbackpocmvvm.views.fragment.VideoTileFragment
import com.example.leanbackpocmvvm.views.viewmodel.VideoTileViewModel

class VideoTilePresenter(
    private val fragment: VideoTileFragment
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = VideoCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val cardView = viewHolder.view as VideoCardView
        val videoItem = item as? VideoTileItem
        if (videoItem != null) {
            cardView.setContent(videoItem)
            fragment.registerCardView(videoItem.tid, cardView)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as VideoCardView
        cardView.resetCardState()
    }
}