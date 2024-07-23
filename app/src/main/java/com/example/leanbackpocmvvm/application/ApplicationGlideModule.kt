package com.example.leanbackpocmvvm.application

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.MemoryCategory
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import com.example.leanbackpocmvvm.R


@GlideModule
class YourAppGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val rq: RequestOptions = RequestOptions()
            .centerInside()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .skipMemoryCache(true)
            .error(R.drawable.movie)
            .format(DecodeFormat.PREFER_RGB_565)
        builder.setDefaultRequestOptions(rq)
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        glide.setMemoryCategory(MemoryCategory.LOW)
        super.registerComponents(context, glide, registry)
    }
}