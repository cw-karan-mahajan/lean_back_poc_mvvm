package com.example.leanbackpocmvvm.vastdata.parser

import android.util.Log
import android.util.LruCache
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VastParser @Inject constructor() {
    private val vastCache = LruCache<String, CacheEntry>(20)

    private data class CacheEntry(
        val vastAds: List<VastAd>,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class VastAd(
        val id: String = "",
        val sequence: Int = 0,
        val adSystem: String = "",
        val adTitle: String = "",
        val impression: String = "",
        val creativeId: String = "",
        val duration: String = "",
        val mediaFiles: List<MediaFile> = emptyList(),
        val trackingEvents: Map<String, String> = emptyMap(),
        val clickThrough: String? = null,
        val clickTracking: String? = null,
        val extensions: Map<String, String> = emptyMap(),
        val version: String? = null,
        val unknownFields: Map<String, Any> = emptyMap()
    ) {
        companion object {
            const val EVENT_START = "start"
            const val EVENT_FIRST_QUARTILE = "firstQuartile"
            const val EVENT_MIDPOINT = "midpoint"
            const val EVENT_THIRD_QUARTILE = "thirdQuartile"
            const val EVENT_COMPLETE = "complete"
        }
    }

    data class MediaFile(
        val url: String = "",
        val bitrate: Int = 0,
        val width: Int = 0,
        val height: Int = 0,
        val type: String = "",
        val delivery: String = ""
    )

    private class AdBuilder {
        var id: String = ""
        var sequence: Int = 0
        var adSystem: String = ""
        var adTitle: String = ""
        var impression: String = ""
        var creativeId: String = ""
        var duration: String = ""
        var version: String? = null
        val mediaFiles = mutableListOf<MediaFile>()
        val trackingEvents = mutableMapOf<String, String>()
        var clickThrough: String? = null
        var clickTracking: String? = null
        val extensions = mutableMapOf<String, String>()
        val unknownFields = mutableMapOf<String, Any>()

        fun build(): VastAd? {
            return if (id.isNotEmpty()) {
                VastAd(
                    id = id,
                    sequence = sequence,
                    adSystem = adSystem,
                    adTitle = adTitle,
                    impression = impression,
                    creativeId = creativeId,
                    duration = duration,
                    mediaFiles = mediaFiles.toList(),
                    trackingEvents = trackingEvents.toMap(),
                    clickThrough = clickThrough,
                    clickTracking = clickTracking,
                    extensions = extensions.toMap(),
                    version = version,
                    unknownFields = unknownFields.toMap()
                )
            } else null
        }
    }

    @VisibleForTesting
    internal fun parseVastXmlTest(inputStream: InputStream): Result<List<VastAd>> {
        return try {
            Log.d(TAG, "Starting test XML parsing")
            val factory = runCatching {
                XmlPullParserFactory.newInstance().apply {
                    isNamespaceAware = false
                }
            }.getOrElse { e ->
                Log.e(TAG, "Error creating XmlPullParserFactory: ${e.message}")
                return Result.failure(e)
            }

            try {
                val parser = factory.newPullParser().apply {
                    try {
                        setInput(inputStream, null)
                    } catch (e: IOException) {
                        Log.e(TAG, "IOException during parsing: ${e.message}")
                        return Result.failure(e)
                    }
                }
                parseVastXml(parser)
            } catch (e: IOException) {
                Log.e(TAG, "IOException during parsing: ${e.message}")
                Result.failure(e)
            } catch (e: Exception) {
                Log.e(TAG, "Error in XML parsing: ${e.message}")
                Result.failure(e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in test setup: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseVastXml(parser: XmlPullParser): Result<List<VastAd>> {
        return try {
            Log.d(TAG, "Starting XML parsing")
            val vastAds = mutableListOf<VastAd>()
            var currentAd: AdBuilder? = null

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "VAST" -> {
                                val version = parser.getAttributeValue(null, "version")
                                Log.d(TAG, "Found VAST tag with version: $version")
                            }
                            "Ad" -> {
                                currentAd = AdBuilder().apply {
                                    id = parser.getAttributeValue(null, "id") ?: ""
                                    sequence = parser.getAttributeValue(null, "sequence")?.toIntOrNull() ?: 0
                                }
                                Log.d(TAG, "Started parsing Ad ID: ${currentAd.id}, Sequence: ${currentAd.sequence}")
                            }
                            "AdSystem" -> {
                                val adSystem = extractTextContent(parser)
                                currentAd?.adSystem = adSystem
                                Log.d(TAG, "Parsed AdSystem: $adSystem")
                            }
                            "AdTitle" -> {
                                val adTitle = extractTextContent(parser)
                                currentAd?.adTitle = adTitle
                                Log.d(TAG, "Parsed AdTitle: $adTitle")
                            }
                            "Impression" -> {
                                val impression = extractTextContent(parser)
                                currentAd?.impression = impression
                                Log.d(TAG, "Parsed Impression: $impression")
                            }
                            "Creative" -> {
                                val creativeId = parser.getAttributeValue(null, "id")
                                currentAd?.creativeId = creativeId ?: ""
                                Log.d(TAG, "Parsed Creative ID: $creativeId")
                            }
                            "Duration" -> {
                                val duration = extractTextContent(parser)
                                currentAd?.duration = duration
                                Log.d(TAG, "Parsed Duration: $duration")
                            }
                            "MediaFile" -> {
                                if (currentAd != null) {
                                    parseMediaFile(parser)?.let { mediaFile ->
                                        currentAd?.mediaFiles?.add(mediaFile)
                                        Log.d(TAG, "Added MediaFile: $mediaFile")
                                    }
                                }
                            }
                            "Tracking" -> {
                                val event = parser.getAttributeValue(null, "event")
                                val url = extractTextContent(parser)
                                if (event != null && url.isNotEmpty()) {
                                    currentAd?.trackingEvents?.put(event, url)
                                    Log.d(TAG, "Added Tracking - Event: $event, URL: $url")
                                }
                            }
                            "ClickThrough" -> {
                                val clickThrough = extractTextContent(parser)
                                if (clickThrough.isNotEmpty()) {
                                    currentAd?.clickThrough = clickThrough
                                    Log.d(TAG, "Added ClickThrough: $clickThrough")
                                }
                            }
                            "ClickTracking" -> {
                                val clickTracking = extractTextContent(parser)
                                if (clickTracking.isNotEmpty()) {
                                    currentAd?.clickTracking = clickTracking
                                    Log.d(TAG, "Added ClickTracking: $clickTracking")
                                }
                            }
                            "Extension" -> {
                                val type = parser.getAttributeValue(null, "type")
                                if (type != null && currentAd != null) {
                                    parseExtensionContent(parser, type, currentAd.extensions)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "Ad") {
                            currentAd?.build()?.let { ad ->
                                vastAds.add(ad)
                                Log.d(TAG, "Completed parsing Ad: ${ad.id}")
                                logVastAdDetails(ad)
                            }
                            currentAd = null
                        }
                    }
                }
                eventType = parser.next()
            }

            Log.d(TAG, "Completed XML parsing. Total Ads parsed: ${vastAds.size}")
            Result.success(vastAds.sortedBy { it.sequence })

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing VAST XML: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun parseVastUrl(vastUrl: String, tileId: String): Result<List<VastAd>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting to fetch VAST data for tileId: $tileId from URL: $vastUrl")

            // Always fetch fresh data
            withTimeout(TIMEOUT_MS) {
                val connection = URL(vastUrl).openConnection().apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    useCaches = false
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("Pragma", "no-cache")
                }

                connection.getInputStream().use { stream ->
                    val xmlContent = stream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "Received XML content length: ${xmlContent.length}")

                    val factory = XmlPullParserFactory.newInstance().apply {
                        isNamespaceAware = false
                    }
                    val parser = factory.newPullParser().apply {
                        setInput(xmlContent.byteInputStream(), null)
                    }

                    val result = parseVastXml(parser)

                    result.onSuccess { vastAds ->
                        vastCache.put(tileId, CacheEntry(vastAds))
                        Log.d(TAG, "Successfully parsed and cached ${vastAds.size} VAST ads for tileId: $tileId")
                    }.onFailure { error ->
                        Log.e(TAG, "Failed to parse VAST XML: ${error.message}")
                    }
                    result
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in parseVastUrl: ${e.message}")
            e.printStackTrace()

            // On error, try to return cached data if available
            vastCache[tileId]?.let { cachedEntry ->
                Log.d(TAG, "Returning cached data for tileId: $tileId")
                return@withContext Result.success(cachedEntry.vastAds)
            }

            Result.failure(e)
        }
    }

    private fun extractTextContent(parser: XmlPullParser): String {
        var content = ""
        try {
            var event = parser.next()
            while (event != XmlPullParser.END_TAG) {
                if (event == XmlPullParser.TEXT) {
                    val text = parser.text?.trim() ?: ""
                    if (text.isNotEmpty()) {
                        content = text
                        if (content.startsWith("<![CDATA[") && content.endsWith("]]>")) {
                            content = content.substring(9, content.length - 3).trim()
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text content: ${e.message}")
        }
        return content
    }

    private fun parseMediaFile(parser: XmlPullParser): MediaFile? {
        try {
            val attributes = mutableMapOf<String, String>()
            for (i in 0 until parser.attributeCount) {
                attributes[parser.getAttributeName(i)] = parser.getAttributeValue(i)
            }

            val url = extractTextContent(parser)
            if (url.isNotEmpty()) {
                return MediaFile(
                    url = url,
                    bitrate = attributes["bitrate"]?.toIntOrNull() ?: 0,
                    width = attributes["width"]?.toIntOrNull() ?: 0,
                    height = attributes["height"]?.toIntOrNull() ?: 0,
                    type = attributes["type"] ?: "",
                    delivery = attributes["delivery"] ?: ""
                ).also {
                    Log.d(TAG, """
                        Created MediaFile:
                        URL: ${it.url}
                        Bitrate: ${it.bitrate}
                        Resolution: ${it.width}x${it.height}
                        Type: ${it.type}
                        Delivery: ${it.delivery}
                    """.trimIndent())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MediaFile: ${e.message}")
        }
        return null
    }

    private fun parseExtensionContent(parser: XmlPullParser, type: String, extensions: MutableMap<String, String>) {
        try {
            var eventType = parser.eventType
            var currentTag = ""

            while (!(eventType == XmlPullParser.END_TAG && parser.name == "Extension")) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        if (currentTag != "Extension") {
                            val value = extractTextContent(parser)
                            if (value.isNotEmpty()) {
                                extensions["${type}_${currentTag}"] = value
                                Log.d(TAG, "Added extension: ${type}_${currentTag} = $value")
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Extension: ${e.message}")
        }
    }

    private fun logVastAdDetails(vastAd: VastAd) {
        Log.d(TAG, """
            ========== VAST Ad Details ==========
            Ad ID: ${vastAd.id}
            Sequence: ${vastAd.sequence}
            Version: ${vastAd.version}
            Ad System: ${vastAd.adSystem}
            Ad Title: ${vastAd.adTitle}
            Duration: ${vastAd.duration}
            
            ------- MediaFiles (${vastAd.mediaFiles.size}) -------
            ${vastAd.mediaFiles.joinToString("\n") { mediaFile ->
            """
                    MediaFile:
                    - URL: ${mediaFile.url}
                    - Bitrate: ${mediaFile.bitrate}
                    - Resolution: ${mediaFile.width}x${mediaFile.height}
                    - Type: ${mediaFile.type}
                    - Delivery: ${mediaFile.delivery}
                """.trimIndent()
        }}
            
            ------- Tracking Events -------
            ${vastAd.trackingEvents.entries.joinToString("\n") { (event, url) ->
            "Event: $event -> URL: $url"
        }}
            
            ------- Click Information -------
            ClickThrough: ${vastAd.clickThrough}
            ClickTracking: ${vastAd.clickTracking}
            
            ${if (vastAd.extensions.isNotEmpty()) """
                ------- Extensions -------
                ${vastAd.extensions.entries.joinToString("\n") { (key, value) ->
            "$key: $value"
        }}
            """.trimIndent() else ""}
            ===================================
        """.trimIndent())
    }

    /**
     * Clear all cached VAST data
     */
    fun clearCache() {
        Log.d(TAG, "Clearing VAST parser cache")
        vastCache.evictAll()
    }

    companion object {
        private const val TAG = "VastParser"
        private const val TIMEOUT_MS = 10000L
    }
}