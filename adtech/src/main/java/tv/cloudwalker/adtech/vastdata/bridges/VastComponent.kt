package tv.cloudwalker.adtech.vastdata.bridges

import android.content.Context
import dagger.hilt.EntryPoint
import tv.cloudwalker.adtech.vastdata.tracking.VastTrackingManager
import tv.cloudwalker.adtech.vastdata.validator.VastErrorHandler
import tv.cloudwalker.adtech.vastdata.network.NetworkConnectivity
import tv.cloudwalker.adtech.vastdata.network.NetworkManager
import tv.cloudwalker.adtech.vastdata.tracking.AdEventTracker
import tv.cloudwalker.adtech.vastdata.cache.SimpleVastCache
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import okhttp3.OkHttpClient

object VastComponent {
    private lateinit var vastTrackingManager: VastTrackingManager
    private lateinit var vastErrorHandler: VastErrorHandler
    private var isInitialized = false
    private lateinit var networkConnectivity: NetworkConnectivity

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface VastDependencies {
        fun okHttpClient(): OkHttpClient
        fun vastTrackingManager(): VastTrackingManager
        fun vastErrorHandler(): VastErrorHandler
        fun adEventTracker(): AdEventTracker
        fun vastCache(): SimpleVastCache
    }

    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            VastDependencies::class.java
        )

        // Initialize network connectivity
        networkConnectivity = NetworkManager.getInstance(context)

        // Get dependencies from Hilt
        val okHttpClient = entryPoint.okHttpClient()
        val adEventTracker = entryPoint.adEventTracker()
        val vastCache = entryPoint.vastCache()

        // Initialize managers
        vastTrackingManager = entryPoint.vastTrackingManager()
        vastErrorHandler = entryPoint.vastErrorHandler()

        isInitialized = true
    }

    fun getVastTrackingManager(): VastTrackingManager {
        checkInitialized()
        return vastTrackingManager
    }

    fun getVastErrorHandler(): VastErrorHandler {
        checkInitialized()
        return vastErrorHandler
    }

    fun getNetworkConnectivity(): NetworkConnectivity {
        checkInitialized()
        return networkConnectivity
    }

    private fun checkInitialized() {
        check(isInitialized) { "VastComponent not initialized. Call initialize(context) first." }
    }

    // Testing purposes only
    @Synchronized
    internal fun reset() {
        isInitialized = false
    }
}