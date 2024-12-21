package com.example.leanbackpocmvvm.vastdata.network

import android.net.NetworkInfo

interface NetworkConnectivity {
    fun getNetworkInfo(): NetworkInfo?
    fun isConnected(): Boolean
}