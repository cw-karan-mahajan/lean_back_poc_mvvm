package com.example.leanbackpocmvvm.remote

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeaderInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val deviceInfo = getDeviceInfo()
        Log.d("Headers", "" + deviceInfo)


        val modifiedRequest = originalRequest.newBuilder()
            .apply {
                deviceInfo.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .build()

        return chain.proceed(modifiedRequest)
    }

    private fun getDeviceInfo(): Map<String, String> {
        val deviceInfo = mutableMapOf<String, String>()

        // Get device-specific information using the getSystemProperty function
        deviceInfo["host"] = "dev-cloudwalkerx2.do.cloudwalker.tv"
        deviceInfo["x-real-ip"] = "103.189.184.185"
        deviceInfo["x-forwarded-for"] = "103.189.184.185"
        deviceInfo["x-client-verify"] = "SUCCESS"
        deviceInfo["connection"] = "close"
        deviceInfo["emac"] = "00:25:92:84:F7:47"
        deviceInfo["wmac"] = "D4:9E:3B:37:BA:07"
        deviceInfo["mboard"] = ""
        deviceInfo["panel"] = ""
        deviceInfo["model"] = "SMART TV"
        deviceInfo["lversion"] = "project2"
        deviceInfo["model"] = "SMART TV"

        deviceInfo["cotaversion"] = "" // Set as needed
        deviceInfo["fotaversion"] = ""
        deviceInfo["accept-version"] = "3.0.0" // Set as needed
        deviceInfo["package"] = "tv.cloudwalker.cwnxt.launcher.com" // Set as needed
        deviceInfo["kidsafe"] = "false" // Set as needed
        deviceInfo["Keymd5"] = "FD889462A56360ED250705AF8603A602" // Set as needed
        deviceInfo["factory"] = ""
        deviceInfo["ram"] = "1" // Set as needed
        deviceInfo["appversion"] = "project2" // Set as needed
        deviceInfo["accept-encoding"] = "gzip"
        deviceInfo["user-agent"] = "okhttp/4.9.1"

        return deviceInfo
    }

    private fun getSystemProperty(key: String): String {
        var value = ""
        try {
            value = Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
                .invoke(null, key) as String
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return value
    }
}
