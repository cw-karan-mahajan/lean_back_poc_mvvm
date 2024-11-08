package com.example.leanbackpocmvvm.vastdata.cache

import android.content.Context
import android.util.Log
import com.example.leanbackpocmvvm.vastdata.parser.VastParser
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimpleVastCache @Inject constructor(
    private val context: Context,
    private val maxCacheSize: Long,
    private val cacheDir: File,
    private val expirationTimeMs: Long
) {
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val gson = Gson()

    init {
        setupCache()
    }

    data class CacheEntry(
        val vastAd: VastParser.VastAd,
        val timestamp: Long = System.currentTimeMillis()
    )

    private fun setupCache() {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        cleanupExpiredCache()
    }

    fun put(key: String, vastAd: VastParser.VastAd) {
        try {
            val entry = CacheEntry(vastAd)
            cache[key] = entry

            // Persist to disk
            val file = File(cacheDir, "$key.json")
            file.writeText(gson.toJson(entry))

            // Check cache size and cleanup if needed
            enforceMaxCacheSize()
        } catch (e: Exception) {
            Log.e(TAG, "Error caching VAST ad: ${e.message}")
        }
    }

    fun get(key: String): VastParser.VastAd? {
        try {
            // Check memory cache first
            cache[key]?.let { entry ->
                if (isEntryValid(entry)) {
                    return entry.vastAd
                }
            }

            // Check disk cache
            val file = File(cacheDir, "$key.json")
            if (file.exists()) {
                val entry = gson.fromJson(file.readText(), CacheEntry::class.java)
                if (isEntryValid(entry)) {
                    cache[key] = entry
                    return entry.vastAd
                } else {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving VAST ad from cache: ${e.message}")
        }
        return null
    }

    private fun isEntryValid(entry: CacheEntry): Boolean {
        return System.currentTimeMillis() - entry.timestamp < expirationTimeMs
    }

    private fun enforceMaxCacheSize() {
        var totalSize = 0L
        val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return

        for (file in files) {
            totalSize += file.length()
        }

        if (totalSize > maxCacheSize) {
            for (file in files) {
                file.delete()
                totalSize -= file.length()
                val key = file.nameWithoutExtension
                cache.remove(key)

                if (totalSize <= maxCacheSize) break
            }
        }
    }

    private fun cleanupExpiredCache() {
        val currentTime = System.currentTimeMillis()

        // Clean memory cache
        cache.entries.removeIf { (_, entry) ->
            currentTime - entry.timestamp > expirationTimeMs
        }
        // Clean disk cache
        cacheDir.listFiles()?.forEach { file ->
            try {
                val entry = gson.fromJson(file.readText(), CacheEntry::class.java)
                if (currentTime - entry.timestamp > expirationTimeMs) {
                    file.delete()
                }
            } catch (e: Exception) {
                file.delete()
            }
        }
    }

    fun clear() {
        cache.clear()
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private const val TAG = "SimpleVastCache"
    }
}