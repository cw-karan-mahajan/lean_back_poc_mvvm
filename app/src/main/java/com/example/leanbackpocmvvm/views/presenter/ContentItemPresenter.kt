package com.example.leanbackpocmvvm.views.presenter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.example.leanbackpocmvvm.application.GlideApp
import com.example.leanbackpocmvvm.databinding.ItemNewCardViewBinding
import com.example.leanbackpocmvvm.utils.dpToPx
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

        val mWidth = dpToPx(viewHolder.view.context,contentData.width)
        val mHeight = dpToPx(viewHolder.view.context,contentData.height)



        val imgStringWithHttps = contentData.imageUrl.replace("http://", "https://")
        GlideApp.with(binding.thumbnail.context).load(imgStringWithHttps).centerCrop()
            .override(mWidth, mHeight).into(binding.thumbnail)


        val layoutParams: ViewGroup.LayoutParams = viewHolder.view.layoutParams
        layoutParams.apply {
            width = mWidth
            height = mHeight
        }
        viewHolder.view.layoutParams = layoutParams

    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val holder = viewHolder as ContentItemViewHolder
        //Glide.with(holder.binding.thumbnail.context).clear(holder.binding.thumbnail)
        //holder.binding.root.onFocusChangeListener = null
        //holder.binding.root.setOnClickListener(null)
        GlideApp.with(holder.binding.thumbnail.context).clear(holder.binding.thumbnail)
    }

    class ContentItemViewHolder(val binding: ItemNewCardViewBinding) : ViewHolder(binding.root)
}