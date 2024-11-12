package com.example.leanbackpocmvvm.application

import android.app.ActivityManager
import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.MemoryCategory
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import com.example.leanbackpocmvvm.R


@GlideModule
class YourAppGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {

        val memoryCacheSizeBytes = calculateMemoryCacheSize(context)
        builder.setMemoryCache(LruResourceCache(memoryCacheSizeBytes))

        val rq: RequestOptions = RequestOptions()
            .centerInside()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .skipMemoryCache(true)
            .error(R.drawable.app_icon_your_company)
            .format(DecodeFormat.PREFER_RGB_565)
        builder.setDefaultRequestOptions(rq)
    }

    private fun calculateMemoryCacheSize(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryClassBytes = 1024L * 1024L * activityManager.memoryClass
        // Use 1/8th of the available memory for caching
        return memoryClassBytes / 16L
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        glide.setMemoryCategory(MemoryCategory.LOW)
        super.registerComponents(context, glide, registry)
    }
}