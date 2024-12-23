package tv.cloudwalker.adtech.utils

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.TypedValue
import androidx.annotation.RequiresApi

fun isAndroidVersion9Supported(): Boolean {
    return Build.VERSION.SDK_INT == Build.VERSION_CODES.P
}

@SuppressLint("NewApi")
fun getBandwidthBasedMaxBitrate(context: Context): Int {
    // Implementation to determine max bitrate based on network type
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return when (connectivityManager.activeNetwork?.let { network ->
        connectivityManager.getNetworkCapabilities(network)
    }?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
        true -> 8_000_000 // 8Mbps for WiFi
        else -> 2_000_000 // 2Mbps for mobile
    }
}

fun getSupportedCodecs(): List<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        codecList.codecInfos
            .filter { it.isEncoder }
            .flatMap { codec -> codec.supportedTypes.toList() }
    } else {
        listOf("avc1", "mp4v", "hevc", "vp8", "vp9")
        //"avc",
        //"mp4v",
        //"VP8",
        //"VP9",
        //"hevc"
    }
}

