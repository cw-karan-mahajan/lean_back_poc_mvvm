package com.example.leanbackpocmvvm.remote

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeaderInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val deviceInfo = getDeviceInfo()
        Timber.d("Headers", "" + deviceInfo)


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
        //deviceInfo["host"] = "dev-cloudwalkerx2.do.cloudwalker.tv"
        //deviceInfo["x-real-ip"] = "103.189.184.185"
        //deviceInfo["x-forwarded-for"] = "103.189.184.185"
        //deviceInfo["x-client-verify"] = "SUCCESS"
        //deviceInfo["connection"] = "close"
        deviceInfo["emac"] = "18:18:18:18:E5:18"
        //deviceInfo["wmac"] = "D4:9E:3B:37:BA:07"
        deviceInfo["mboard"] = "TP.SK518D.PB802"
        deviceInfo["brand"] = "TSERIES"
        deviceInfo["model"] = "CWT43SUX216"
        deviceInfo["lversion"] = "project2"
        deviceInfo["model"] = "SMART TV"

        deviceInfo["cotaversion"] = "20200818_181525" // Set as needed
        deviceInfo["fotaversion"] = "20190902_184636"
        deviceInfo["accept-version"] = "1.0.0" // Set as needed
        deviceInfo["package"] = "tv.cloudwalker.cwnxt.launcher.com" // Set as needed
        deviceInfo["kidsafe"] = "false" // Set as needed
        deviceInfo["Keymd5"] = "FD889462A56360ED250705AF8603A602" // Set as needed
        deviceInfo["factory"] = ""
        deviceInfo["ram"] = "1" // Set as needed
        deviceInfo["appversion"] = "project2" // Set as needed
        deviceInfo["accept-encoding"] = "gzip"
        deviceInfo["user-agent"] = "okhttp/4.9.1"

        //mboard:TP.SK518D.PB802
        //model:CWT43SUX216
        //emac:18:18:18:18:E5:18
        //lversion:project2
        //brand:TSERIES
        //accept-version:1.0.0
        //cotaversion:20200818_181525
        //fotaversion:20190902_184636
        //appVersion:project2

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
