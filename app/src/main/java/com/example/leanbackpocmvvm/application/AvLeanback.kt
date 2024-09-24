package com.example.leanbackpocmvvm.application

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import androidx.multidex.MultiDex
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.MemoryCategory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AvLeanback : Application(), ComponentCallbacks2 {

    private var idleHandler: Handler? = null
    private lateinit var glideBuilder: GlideBuilder
    private var currentIdleStage = 0

    companion object {
        fun forceStopApp(context: Context) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.killBackgroundProcesses(context.packageName)
        }
    }

    override fun onCreate() {
        super.onCreate()
        MultiDex.install(this)
        setupIdleHandler()
        configureGlide()
    }

    private fun configureGlide() {
        glideBuilder = GlideBuilder().apply {
            setMemoryCache(LruResourceCache(calculateActiveCacheSize(this@AvLeanback)))
        }
        Glide.init(this, glideBuilder)
    }

    private fun calculateActiveCacheSize(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryClassBytes = 1024L * 1024L * activityManager.memoryClass
        // Use 1/8th of the available memory for active caching
        return memoryClassBytes / 8L
    }

    private fun calculateIdleCacheSize(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryClassBytes = 1024L * 1024L * activityManager.memoryClass
        // Use 1/16th of the available memory for idle caching
        return memoryClassBytes / 16L
    }

    private fun setupIdleHandler() {
        idleHandler = Handler(Looper.getMainLooper())
    }

    fun resetIdleTimer() {
        idleHandler?.removeCallbacksAndMessages(null)
        currentIdleStage = 0

        // Set to active cache size
        setGlideMemorySize(calculateActiveCacheSize(this))

        idleHandler?.postDelayed({
            currentIdleStage++
            when (currentIdleStage) {
                1 -> setGlideMemorySize(calculateActiveCacheSize(this) * 3 / 4)
                2 -> setGlideMemorySize(calculateActiveCacheSize(this) / 2)
                3 -> setGlideMemorySize(calculateIdleCacheSize(this))
            }
            if (currentIdleStage < 3) {
                resetIdleTimer()
            }
        }, 2 * 60 * 1000) // 2 minutes
    }

    private fun setGlideMemorySize(cacheSize: Long) {
        Glide.get(this).setMemoryCategory(if (cacheSize == calculateActiveCacheSize(this))
            MemoryCategory.NORMAL else MemoryCategory.LOW)
        glideBuilder.setMemoryCache(LruResourceCache(cacheSize))
        Glide.init(this, glideBuilder)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                // Reduce cache size when UI is hidden
                Glide.get(this).setMemoryCategory(MemoryCategory.LOW)
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // Further reduce cache size under memory pressure
                Glide.get(this).clearMemory()
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // Clear cache under severe memory pressure
                Glide.get(this).clearMemory()
                System.gc()
            }
            else -> {
                // No action needed
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Glide.get(this).clearMemory()
    }
}