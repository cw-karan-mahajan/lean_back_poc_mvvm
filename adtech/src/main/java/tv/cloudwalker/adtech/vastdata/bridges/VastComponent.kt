package tv.cloudwalker.adtech.vastdata.bridges

import android.content.Context
import tv.cloudwalker.adtech.vastdata.tracking.VastTrackingManager
import tv.cloudwalker.adtech.vastdata.validator.VastErrorHandler
import tv.cloudwalker.adtech.vastdata.di.VastModule
import tv.cloudwalker.adtech.vastdata.network.NetworkConnectivity
import tv.cloudwalker.adtech.vastdata.network.NetworkManager

object VastComponent {
    private lateinit var vastTrackingManager: VastTrackingManager
    private lateinit var vastErrorHandler: VastErrorHandler
    private var isInitialized = false
    private lateinit var networkConnectivity: NetworkConnectivity

    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return

        // Use VastModule providers
        val okHttpClient = VastModule.provideVastHttpClient()
        networkConnectivity = NetworkManager.getInstance(context)
        val adEventTracker = VastModule.provideAdEventTracker(okHttpClient, networkConnectivity)
        val vastCache = VastModule.provideVastCache(context)

        vastTrackingManager = VastModule.provideVastTrackingManager(adEventTracker, vastCache)
        vastErrorHandler = VastModule.provideVastErrorHandler()

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

    private fun checkInitialized() {
        check(isInitialized) { "VastComponent not initialized. Call initialize(context) first." }
    }
}