package com.example.leanbackpocmvvm.utils

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory


val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
inline fun <reified T> fromJson(json: String, code: Int) = moshi.adapter(T::class.java).fromJson(json)
