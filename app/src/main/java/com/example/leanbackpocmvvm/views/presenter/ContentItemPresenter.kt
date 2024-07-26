package com.example.leanbackpocmvvm.views.presenter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.example.leanbackpocmvvm.databinding.ItemNewCardViewBinding
import com.example.leanbackpocmvvm.views.viewmodel.CustomRowItemX

class ContentItemPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val binding =
            ItemNewCardViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContentItemViewHolder(binding)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val customRowItem = item as CustomRowItemX
        val holder = viewHolder as ContentItemViewHolder
        val binding = holder.binding
        val contentData = customRowItem.contentData

        // Set dimensions based on ContentData
        binding.innerLayout.layoutParams.apply {
            width = contentData.width
            height = contentData.height
        }

        val imageString = if (item.layout == "landscape") {
            item.rowItemX.poster ?: ""
        } else {
            item.rowItemX.portrait ?: ""
        }
        val imgStringWithHttps = imageString.replace("http://", "https://")
        // Load image based on ContentData
        Glide.with(binding.imageView.context)
            .load(imgStringWithHttps)
            .centerCrop()
            .into(binding.imageView)

        binding.root.post {
            // Set up focus change listener
            binding.root.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    binding.focusOverlay.visibility = View.VISIBLE
                    val scaleFactor = if (contentData.isLandscape) 1.05f else 1.1f
                    binding.root.animate().scaleX(scaleFactor).scaleY(scaleFactor).duration = 300
                } else {
                    binding.focusOverlay.visibility = View.INVISIBLE
                    binding.root.animate().scaleX(1f).scaleY(1f).duration = 300
                }
            }
        }

        // Handle landscape/portrait specific logic
        if (contentData.isLandscape) {
            // Additional setup for landscape items
        } else if (contentData.isPortrait) {
            // Additional setup for portrait items
        }

    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val holder = viewHolder as ContentItemViewHolder
        Glide.with(holder.binding.imageView.context).clear(holder.binding.imageView)
        holder.binding.root.onFocusChangeListener = null
        holder.binding.root.setOnClickListener(null)
    }

    class ContentItemViewHolder(val binding: ItemNewCardViewBinding) : ViewHolder(binding.root)
}