package com.example.leanbackpocmvvm.repository

import android.content.Context
import android.util.Log
import com.example.leanbackpocmvvm.core.Resource
import com.example.leanbackpocmvvm.models.MyData2
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
interface MainRepository1 {
    fun fetchList(baseUrl: String, headers: Map<String, String> = emptyMap()): Flow<Resource<MyData2>>
}

class MainRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    suspend fun getMyData(): MyData2 {
        val inputStream = context.assets.open("data2.json")
        val json: String = inputStream.bufferedReader().use { it.readText() }
        inputStream.close()
        Log.d("MainRepository", "JSON read: ${json.take(300)}...") // Log first 300 chars
        return gson.fromJson(json, MyData2::class.java)
    }
}