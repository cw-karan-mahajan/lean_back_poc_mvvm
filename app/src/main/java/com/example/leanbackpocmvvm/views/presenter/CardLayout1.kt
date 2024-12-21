package com.example.leanbackpocmvvm.views.presenter

import android.view.ViewGroup
import androidx.leanback.widget.Presenter
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.views.customview.NewVideoCardView
import com.example.leanbackpocmvvm.views.viewmodel.CustomRowItemX
import com.example.leanbackpocmvvm.application.GlideApp

@UnstableApi
class CardLayout1(
    private val lifecycleOwner: LifecycleOwner
) : Presenter() {

    class CustomViewHolder(val cardView: NewVideoCardView) : Presenter.ViewHolder(cardView) {
        var boundItemId: String? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = NewVideoCardView(parent.context)
            .apply {
                isFocusable = true
                isFocusableInTouchMode = true
                setLifecycleOwner(lifecycleOwner)
            }
        return CustomViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val customViewHolder = viewHolder as CustomViewHolder
        val cardView = customViewHolder.cardView
        val customItem = item as? CustomRowItemX
        when {
            customItem != null -> {
                // Check if this ViewHolder is already bound to this item
                if (customViewHolder.boundItemId != customItem.rowItemX.tid) {
                    customViewHolder.boundItemId = customItem.rowItemX.tid

                    val isAdImage = customItem.rowItemX.adsServer != null
                    val imageUrl = if (isAdImage) customItem.rowItemX.adImageUrl else customItem.contentData.imageUrl

                    cardView.setImage(
                        imageUrl,
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
                    cardView.customItem = customItem
                }
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val customViewHolder = viewHolder as CustomViewHolder
        customViewHolder.boundItemId = null
        val cardView = customViewHolder.cardView
        cardView.resetCardState()
        try {
            GlideApp.with(cardView.context).clear(cardView)
        } catch (e: IllegalArgumentException) {
             //Activity was already destroyed, ignore
        }
    }
}