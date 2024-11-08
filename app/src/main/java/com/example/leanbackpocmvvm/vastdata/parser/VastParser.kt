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

    private val vastCache = LruCache<String, VastAd>(20) // Cache up to 20 parsed VAST responses

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

    suspend fun parseVastUrl(vastUrl: String, tileId: String): VastAd? {
        return withContext(Dispatchers.IO) {
            try {
                // Check cache first
                vastCache.get(tileId)?.let {
                    Log.d(TAG, "Cache hit for tileId: $tileId")
                    return@withContext it
                }

                Log.d(TAG, "Cache miss for tileId: $tileId, fetching from URL: $vastUrl")

                val connection = URL(vastUrl).openConnection().apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                connection.getInputStream().use { stream ->
                    val vastAd = parseVastXml(stream)
                    vastAd?.let {
                        vastCache.put(tileId, it)
                        Log.d(TAG, "Successfully parsed and cached VAST for tileId: $tileId")
                    }
                    vastAd
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing VAST URL: ${e.message}", e)
                null
            }
        }
    }

    private fun parseVastXml(inputStream: InputStream): VastAd? {
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            var vastAd: VastAd? = null
            var currentTag = ""

            // Temporary holders for building VastAd
            var id = ""
            var sequence = 0
            var adSystem = ""
            var adTitle = ""
            var impression = ""
            var creativeId = ""
            var duration = ""
            var mediaFiles = mutableListOf<MediaFile>()
            val trackingEvents = mutableMapOf<String, String>()
            var clickThrough: String? = null
            var clickTracking: String? = null
            val extensions = mutableMapOf<String, String>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        when (currentTag) {
                            "Ad" -> {
                                id = parser.getAttributeValue(null, "id") ?: ""
                                sequence = parser.getAttributeValue(null, "sequence")?.toIntOrNull() ?: 0
                            }
                            "AdSystem" -> adSystem = parser.nextText()
                            "AdTitle" -> adTitle = parser.nextText()
                            "Impression" -> impression = getCleanCdataContent(parser)
                            "Creative" -> creativeId = parser.getAttributeValue(null, "id") ?: ""
                            "Duration" -> duration = parser.nextText()
                            "MediaFile" -> {
                                val mediaFile = parseMediaFile(parser)
                                mediaFiles.add(mediaFile)
                            }
                            "Tracking" -> {
                                val event = parser.getAttributeValue(null, "event")
                                if (event != null) {
                                    trackingEvents[event] = getCleanCdataContent(parser)
                                }
                            }
                            "ClickThrough" -> clickThrough = getCleanCdataContent(parser)
                            "ClickTracking" -> clickTracking = getCleanCdataContent(parser)
                            "Extension" -> {
                                val type = parser.getAttributeValue(null, "type")
                                if (type != null) {
                                    parseExtension(parser, type, extensions)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "Ad") {
                            vastAd = VastAd(
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
                            break // We've found our Ad, no need to continue parsing
                        }
                    }
                }
                eventType = parser.next()
            }

            return vastAd
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing VAST XML: ${e.message}", e)
            return null
        }
    }

    private fun parseMediaFile(parser: XmlPullParser): MediaFile {
        val url = getCleanCdataContent(parser)
        return MediaFile(
            url = url,
            bitrate = parser.getAttributeValue(null, "bitrate")?.toIntOrNull() ?: 0,
            width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0,
            height = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0,
            type = parser.getAttributeValue(null, "type") ?: "",
            delivery = parser.getAttributeValue(null, "delivery") ?: ""
        )
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
                    }
                }
            }
            eventType = parser.next()
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