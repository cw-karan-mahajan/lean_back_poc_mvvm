package com.example.leanbackpocmvvm.utils

import retrofit2.Response


inline fun <reified T> Response<T>.getResponse(): T {
    val responseBody = body()
    return if (this.isSuccessful && responseBody != null) {
        responseBody
    } else {
        fromJson<T>(errorBody()!!.string(), code())!!
    }
}
