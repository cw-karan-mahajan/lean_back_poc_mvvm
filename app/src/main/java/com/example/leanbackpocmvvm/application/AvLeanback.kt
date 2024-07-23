package com.example.leanbackpocmvvm.application

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.utils.ExoPlayerManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject


@HiltAndroidApp
class AvLeanback : Application(), ComponentCallbacks2 {

    @Inject
    lateinit var exoPlayerManager: ExoPlayerManager
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.d(TAG, "onTrimMemory: moderate")
                // Release any memory that your app doesn't need to run
                releaseMemory(false)
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Log.d(TAG, "onTrimMemory: UI hidden")
                // Release any UI objects that currently hold memory
                releaseMemory(false)
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.d(TAG, "onTrimMemory: critical")
                // Release as much memory as possible
                releaseMemory(true)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun releaseMemory(critical: Boolean) {
        // Release non-critical resources
        exoPlayerManager.releasePlayer()

        // Clear any caches
        // For example, if you're using Glide for image loading:
        // Glide.get(this).clearMemory()

        if (critical) {
            // Release more aggressive resources if the situation is critical
            // For example, clear all in-memory caches, release any large objects, etc.
            System.gc()
        }
    }

    companion object {
        private const val TAG = "MyApplication"
    }

}