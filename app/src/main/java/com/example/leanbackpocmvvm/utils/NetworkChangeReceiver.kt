package com.example.leanbackpocmvvm.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager

class NetworkChangeReceiver(private val listener : isConnected) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetworkInfo

            if (activeNetwork != null && activeNetwork.isConnectedOrConnecting) {
                listener.connected()
            } else {
                listener.notconnected()
            }
        }
    }
}
interface isConnected{
    fun connected()
    fun notconnected()
}
