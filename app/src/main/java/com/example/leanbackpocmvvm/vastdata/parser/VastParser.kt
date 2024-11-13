package com.example.leanbackpocmvvm.vastdata.parser

import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VastParser @Inject constructor() {

    private val vastCache = LruCache<String, List<VastAd>>(20)

    data class VastAd(
        val id: String,
        val sequence: Int,
        val adSystem: String,
        val adTitle: String,
        val impression: String,
        val creativeId: String,
        val duration: String,
        val mediaFiles: List<MediaFile>,
        val trackingEvents: Map<String, String>,
        val clickThrough: String?,
        val clickTracking: String?,
        val extensions: Map<String, String>
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
        val url: String,
        val bitrate: Int,
        val width: Int,
        val height: Int,
        val type: String,
        val delivery: String
    )

    suspend fun parseVastUrl(vastUrl: String, tileId: String): List<VastAd>? {
        return withContext(Dispatchers.IO) {
            try {
                // Check cache first
                vastCache[tileId]?.let {
                    Log.d(TAG, "Cache hit for tileId: $tileId")
                    return@withContext it
                }

                Log.d(TAG, "Cache miss for tileId: $tileId, fetching from URL: $vastUrl")

                val connection = URL(vastUrl).openConnection().apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                connection.getInputStream().use { stream ->
                    val vastAds = parseVastXml(stream)
                    vastAds?.let {
                        vastCache.put(tileId, it)
                        Log.d(TAG, "Successfully parsed and cached ${it.size} VAST ads for tileId: $tileId")
                    }
                    vastAds
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing VAST URL: ${e.message}", e)
                null
            }
        }
    }

    private fun parseVastXml(inputStream: InputStream): List<VastAd>? {
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            val vastAds = mutableListOf<VastAd>()
            var currentTag = ""

            // Temporary holders for each ad
            var currentAd = AdBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        when (currentTag) {
                            "Ad" -> {
                                currentAd = AdBuilder()
                                currentAd.id = parser.getAttributeValue(null, "id") ?: ""
                                currentAd.sequence = parser.getAttributeValue(null, "sequence")?.toIntOrNull() ?: 0
                                Log.d(TAG, "Started parsing Ad ID: ${currentAd.id}, Sequence: ${currentAd.sequence}")
                            }
                            "AdSystem" -> currentAd.adSystem = parser.nextText()
                            "AdTitle" -> currentAd.adTitle = parser.nextText()
                            "Impression" -> currentAd.impression = getCleanCdataContent(parser)
                            "Creative" -> currentAd.creativeId = parser.getAttributeValue(null, "id") ?: ""
                            "Duration" -> currentAd.duration = parser.nextText()
                            "MediaFile" -> {
                                val mediaFile = parseMediaFile(parser)
                                if (mediaFile.url.isNotEmpty()) {
                                    currentAd.mediaFiles.add(mediaFile)
                                    Log.d(TAG, "Added MediaFile: $mediaFile")
                                }
                            }
                            "Tracking" -> {
                                val event = parser.getAttributeValue(null, "event")
                                if (event != null) {
                                    currentAd.trackingEvents[event] = getCleanCdataContent(parser)
                                }
                            }
                            "ClickThrough" -> currentAd.clickThrough = getCleanCdataContent(parser)
                            "ClickTracking" -> currentAd.clickTracking = getCleanCdataContent(parser)
                            "Extension" -> {
                                val type = parser.getAttributeValue(null, "type")
                                if (type != null) {
                                    parseExtension(parser, type, currentAd.extensions)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "Ad") {
                            currentAd.build()?.let { ad ->
                                vastAds.add(ad)
                                Log.d(TAG, """
                                    Completed parsing Ad:
                                    ID: ${ad.id}
                                    Sequence: ${ad.sequence}
                                    Title: ${ad.adTitle}
                                    MediaFiles: ${ad.mediaFiles.size}
                                """.trimIndent())
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            Log.d(TAG, "Total Ads parsed: ${vastAds.size}")
            return vastAds.sortedBy { it.sequence }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing VAST XML: ${e.message}", e)
            return null
        }
    }

    private fun parseMediaFile(parser: XmlPullParser): MediaFile {
        val attributes = mutableMapOf<String, String>()
        // Get all attributes first
        for (i in 0 until parser.attributeCount) {
            attributes[parser.getAttributeName(i)] = parser.getAttributeValue(i)
        }

        val url = getCleanCdataContent(parser)
        Log.d(TAG, "Parsing MediaFile - URL: $url")
        Log.d(TAG, "MediaFile attributes: $attributes")

        return MediaFile(
            url = url,
            bitrate = attributes["bitrate"]?.toIntOrNull() ?: 0,
            width = attributes["width"]?.toIntOrNull() ?: 0,
            height = attributes["height"]?.toIntOrNull() ?: 0,
            type = attributes["type"] ?: "",
            delivery = attributes["delivery"] ?: ""
        ).also {
            Log.d(TAG, "Created MediaFile: $it")
        }
    }

    private fun parseExtension(parser: XmlPullParser, type: String, extensions: MutableMap<String, String>) {
        var eventType = parser.eventType
        var currentTag = ""

        while (!(eventType == XmlPullParser.END_TAG && parser.name == "Extension")) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag != "Extension") {
                        val value = parser.nextText()
                        extensions["${type}_${currentTag}"] = value
                        Log.d(TAG, "Added extension: ${type}_${currentTag} = $value")
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private class AdBuilder {
        var id: String = ""
        var sequence: Int = 0
        var adSystem: String = ""
        var adTitle: String = ""
        var impression: String = ""
        var creativeId: String = ""
        var duration: String = ""
        val mediaFiles = mutableListOf<MediaFile>()
        val trackingEvents = mutableMapOf<String, String>()
        var clickThrough: String? = null
        var clickTracking: String? = null
        val extensions = mutableMapOf<String, String>()

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
                    extensions = extensions.toMap()
                )
            } else null
        }
    }

    private fun getCleanCdataContent(parser: XmlPullParser): String {
        val content = parser.nextText()
        return content.trim().removeSurrounding("<![CDATA[", "]]>")
    }

    fun clearCache() {
        vastCache.evictAll()
    }

    companion object {
        private const val TAG = "VastParser"
    }
}