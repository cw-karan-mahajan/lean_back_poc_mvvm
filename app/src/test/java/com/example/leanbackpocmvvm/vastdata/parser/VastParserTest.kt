package com.example.leanbackpocmvvm.vastdata.parser

import android.util.LruCache
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.MockKAnnotations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class VastParserTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var vastParser: VastParser
    private lateinit var xmlPullParserFactory: XmlPullParserFactory

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        xmlPullParserFactory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        vastParser = VastParser()
        verifyTestResources()
    }

    @Before
    fun verifyTestSetup() {
        val testResource = loadTestResource("vast_mock_response.xml")
        assertNotNull(testResource, "Test resource should be available")
        assertTrue(testResource.available() > 0, "Test resource should contain data")
    }

    private fun verifyTestResources() {
        assertNotNull(loadTestResource("vast_mock_response.xml"))
        assertNotNull(loadTestResource("vast_missing_fields.xml"))
        assertNotNull(loadTestResource("vast_empty_mediafiles.xml"))
    }

    private fun loadTestResource(fileName: String): InputStream {
        return javaClass.classLoader?.getResourceAsStream(fileName)
            ?: throw IllegalStateException("Could not load test resource: $fileName")
    }

    @Test
    fun `verify test resources are available`() {
        assertNotNull(loadTestResource("vast_mock_response.xml"))
        assertNotNull(loadTestResource("vast_missing_fields.xml"))
        assertNotNull(loadTestResource("vast_empty_mediafiles.xml"))
    }

    @Test
    fun `parseVastUrl should parse valid VAST XML successfully`() = runTest {
        // Given
        val inputStream = loadTestResource("vast_mock_response.xml")

        try {
            // When
            val result = vastParser.parseVastXmlTest(inputStream)

            // Then
            assertTrue(result.isSuccess) {
                "Parsing failed: ${result.exceptionOrNull()?.message}"
            }

            result.fold(
                onSuccess = { vastAds ->
                    assertNotNull(vastAds)
                    assertTrue(vastAds.isNotEmpty())

                    val vastAd = vastAds.first()
                    with(vastAd) {
                        assertEquals("1122706-1-n", id)
                        assertEquals(1, sequence)
                        assertEquals("SpringServe", adSystem)
                        assertEquals("4929348", adTitle)
                        assertEquals("00:00:15", duration)
                        assertEquals("596121", creativeId)
                    }

                    // Verify tracking events
                    val trackingEvents = vastAd.trackingEvents
                    assertTrue(trackingEvents.containsKey(VastParser.VastAd.EVENT_START))
                    assertTrue(trackingEvents.containsKey(VastParser.VastAd.EVENT_COMPLETE))

                    // Verify media file details
                    val mediaFile = vastAd.mediaFiles.first()
                    with(mediaFile) {
                        assertEquals("progressive", delivery)
                        assertEquals("video/mp4", type)
                        assertEquals(91, bitrate)
                        assertEquals(240, height)
                        assertEquals(426, width)
                    }
                },
                onFailure = { error ->
                    fail("Should not fail: ${error.message}")
                }
            )
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
        }
    }

    @Test
    fun `parseVastXml should handle dynamic VAST responses`() = runTest {
        try {
            // Test with valid XML
            val result = vastParser.parseVastXmlTest(loadTestResource("vast_mock_response.xml"))
            assertTrue(result.isSuccess)
            assertNotNull(result.getOrNull()?.firstOrNull())
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
        }
    }

    @Test
    fun `parseVastXml should handle malformed XML`() = runTest {
        try {
            // Given
            val malformedXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <VAST>
                <Invalid>
                    <Missing>
                </Invalid>
            </VAST>
        """.trimIndent().byteInputStream()

            // When
            val result = vastParser.parseVastXmlTest(malformedXml)

            // Then
            assertTrue(result.isFailure) { "Expected parsing to fail for malformed XML" }
            val error = result.exceptionOrNull()
            assertNotNull(error) { "Expected an error to be present" }
            assertTrue(error is XmlPullParserException) {
                "Expected XmlPullParserException but got ${error?.javaClass?.simpleName}"
            }
        } catch (e: Exception) {
            fail("Test failed with unexpected exception: ${e.message}")
        }
    }

    @Test
    fun `parseVastXml should handle missing required fields`() = runTest {
        try {
            // Given
            val inputStream = loadTestResource("vast_missing_fields.xml")

            // When
            val result = vastParser.parseVastXmlTest(inputStream)

            // Then
            result.fold(
                onSuccess = { vastAds ->
                    val vastAd = vastAds.firstOrNull()
                    assertNotNull(vastAd)
                    assertTrue(vastAd!!.adTitle.isEmpty() || vastAd.duration.isEmpty())
                    assertNotNull(vastAd.mediaFiles.firstOrNull())
                },
                onFailure = { error ->
                    fail("Should handle missing fields gracefully: ${error.message}")
                }
            )
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
        }
    }

    @Test
    fun `parseVastXml should handle empty media files`() = runTest {
        try {
            // Given
            val inputStream = loadTestResource("vast_empty_mediafiles.xml")

            // When
            val result = vastParser.parseVastXmlTest(inputStream)

            // Then
            result.fold(
                onSuccess = { vastAds ->
                    val vastAd = vastAds.firstOrNull()
                    assertNotNull(vastAd)
                    assertTrue(vastAd!!.mediaFiles.isEmpty())
                },
                onFailure = { error ->
                    fail("Should handle empty media files gracefully: ${error.message}")
                }
            )
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
        }
    }

    @Test
    fun `parseVastXml should validate tracking events`() = runTest {
        try {
            // Given
            val inputStream = loadTestResource("vast_mock_response.xml")

            // When
            val result = vastParser.parseVastXmlTest(inputStream)

            // Then
            result.fold(
                onSuccess = { vastAds ->
                    val vastAd = vastAds.first()
                    val events = vastAd.trackingEvents

                    // Verify all required tracking events exist
                    assertTrue(events.containsKey(VastParser.VastAd.EVENT_START))
                    assertTrue(events.containsKey(VastParser.VastAd.EVENT_FIRST_QUARTILE))
                    assertTrue(events.containsKey(VastParser.VastAd.EVENT_MIDPOINT))
                    assertTrue(events.containsKey(VastParser.VastAd.EVENT_THIRD_QUARTILE))
                    assertTrue(events.containsKey(VastParser.VastAd.EVENT_COMPLETE))

                    // Verify tracking URLs are valid
                    events.values.forEach { url ->
                        assertTrue(url.startsWith("http"))
                    }
                },
                onFailure = { error ->
                    fail("Should not fail: ${error.message}")
                }
            )
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
        }
    }

    @Test
    fun `parseVastXml should validate video clicks`() = runTest {
        try {
            // Given
            val inputStream = loadTestResource("vast_mock_response.xml")

            // When
            val result = vastParser.parseVastXmlTest(inputStream)

            // Then
            result.fold(
                onSuccess = { vastAds ->
                    val vastAd = vastAds.first()

                    // Verify ClickThrough exists and is valid
                    assertNotNull(vastAd.clickThrough)
                    assertTrue(vastAd.clickThrough!!.startsWith("http"))

                    // Verify ClickTracking exists and is valid
                    assertNotNull(vastAd.clickTracking)
                    assertTrue(vastAd.clickTracking!!.startsWith("http"))
                },
                onFailure = { error ->
                    fail("Should not fail: ${error.message}")
                }
            )
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
        }
    }

    @Test
    fun `parseVastXml should handle network errors gracefully`() = runTest {
        try {
            // Given
            val emptyStream = object : InputStream() {
                override fun read(): Int = throw IOException("Simulated network error")
            }

            // When
            val result = vastParser.parseVastXmlTest(emptyStream)

            // Then
            assertTrue(result.isFailure) { "Expected parsing to fail with error" }
            val error = result.exceptionOrNull()
            assertNotNull(error) { "Expected an error to be present" }
            assertTrue(error is IOException || error is XmlPullParserException) {
                "Expected either IOException or XmlPullParserException but got ${error?.javaClass?.simpleName}"
            }
        } catch (e: Exception) {
            fail("Test failed with unexpected exception: ${e.message}")
        }
    }

    @Test
    fun `parseVastXml should parse multiple ads in sequence`() = runTest {
        try {
            // Given
            val inputStream = loadTestResource("vast_mock_response.xml")

            // When
            val result = vastParser.parseVastXmlTest(inputStream)

            // Then
            result.fold(
                onSuccess = { vastAds ->
                    assertTrue(vastAds.size >= 1)
                    vastAds.forEachIndexed { index, ad ->
                        assertEquals(index + 1, ad.sequence)
                    }
                },
                onFailure = { error ->
                    fail("Should not fail: ${error.message}")
                }
            )
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
        }
    }

    @Test
    fun `parseVastXml should handle CDATA sections correctly`() = runTest {
        try {
            // Given
            val xmlWithCDATA = """
                <?xml version="1.0" encoding="UTF-8"?>
                <VAST version="3.0">
                    <Ad id="test_ad">
                        <InLine>
                            <AdSystem>Test System</AdSystem>
                            <AdTitle>Test Ad</AdTitle>
                            <Impression><![CDATA[http://example.com/impression]]></Impression>
                            <Creatives>
                                <Creative>
                                    <Linear>
                                        <Duration>00:00:30</Duration>
                                        <MediaFiles>
                                            <MediaFile delivery="progressive" type="video/mp4" bitrate="500" width="400" height="300">
                                                <![CDATA[http://example.com/video.mp4]]>
                                            </MediaFile>
                                        </MediaFiles>
                                    </Linear>
                                </Creative>
                            </Creatives>
                        </InLine>
                    </Ad>
                </VAST>
            """.trimIndent().byteInputStream()

            // When
            val result = vastParser.parseVastXmlTest(xmlWithCDATA)

            // Then
            assertTrue(result.isSuccess) { "Parsing failed: ${result.exceptionOrNull()?.message}" }
            result.fold(
                onSuccess = { vastAds ->
                    val vastAd = vastAds.firstOrNull()
                    assertNotNull(vastAd)
                    assertEquals("test_ad", vastAd!!.id)
                    assertEquals("Test System", vastAd.adSystem)
                    assertEquals("Test Ad", vastAd.adTitle)
                    assertTrue(vastAd.impression.startsWith("http://"))
                    assertFalse(vastAd.impression.contains("CDATA"))

                    val mediaFile = vastAd.mediaFiles.firstOrNull()
                    assertNotNull(mediaFile)
                    assertTrue(mediaFile!!.url.startsWith("http://"))
                    assertFalse(mediaFile.url.contains("CDATA"))
                },
                onFailure = { error ->
                    fail("Should not fail: ${error.message}")
                }
            )
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
        }
    }

    @Test
    fun `parseVastXml should handle version validation`() = runTest {
        try {
            // Given
            val invalidVersionXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <VAST version="1.0">
                <Ad id="test_ad">
                    <InLine>
                        <AdSystem>Test System</AdSystem>
                        <AdTitle>Test Ad</AdTitle>
                        <Impression>http://example.com/impression</Impression>
                        <Creatives>
                            <Creative id="123">
                                <Linear>
                                    <Duration>00:00:30</Duration>
                                    <MediaFiles>
                                        <MediaFile delivery="progressive" type="video/mp4" bitrate="500" width="400" height="300">
                                            http://example.com/video.mp4
                                        </MediaFile>
                                    </MediaFiles>
                                </Linear>
                            </Creative>
                        </Creatives>
                    </InLine>
                </Ad>
            </VAST>
        """.trimIndent().byteInputStream()

            // When
            val result = vastParser.parseVastXmlTest(invalidVersionXml)

            // Then
            assertTrue(result.isSuccess) { "Parser should accept different versions" }
            result.fold(
                onSuccess = { vastAds ->
                    val vastAd = vastAds.firstOrNull()
                    assertNotNull(vastAd)
                    assertEquals("1.0", vastAd?.version) { "Version should be captured from VAST tag" }
                },
                onFailure = { error ->
                    fail("Should handle version validation gracefully: ${error.message}")
                }
            )
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
        }
    }

    @Test
    fun `parseVastXml should handle extension elements`() = runTest {
        try {
            // Given
            val xmlWithExtensions = """
                <?xml version="1.0" encoding="UTF-8"?>
                <VAST version="3.0">
                    <Ad id="test_ad">
                        <InLine>
                            <AdSystem>Test System</AdSystem>
                            <AdTitle>Test Ad</AdTitle>
                            <Extensions>
                                <Extension type="custom">
                                    <CustomField>CustomValue</CustomField>
                                </Extension>
                            </Extensions>
                        </InLine>
                    </Ad>
                </VAST>
            """.trimIndent().byteInputStream()

            // When
            val result = vastParser.parseVastXmlTest(xmlWithExtensions)

            // Then
            assertTrue(result.isSuccess)
            result.fold(
                onSuccess = { vastAds ->
                    val vastAd = vastAds.firstOrNull()
                    assertNotNull(vastAd)
                    assertTrue(vastAd!!.extensions.isNotEmpty())
                    assertEquals("CustomValue", vastAd.extensions["custom_CustomField"])
                },
                onFailure = { error ->
                    fail("Should handle extensions: ${error.message}")
                }
            )
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
        }
    }

    @Test
    fun `cache should store and retrieve VAST ads correctly`() = runTest {
        try {
            // Given
            val inputStream = loadTestResource("vast_mock_response.xml")
            val tileId = "test_tile_1"

            // Clear and verify cache is empty
            vastParser.clearCache()
            assertNull(vastParser.getFromCache(tileId), "Cache should be empty after clear")

            // Parse XML
            val initialResult = vastParser.parseVastXmlTest(inputStream)
            assertTrue(initialResult.isSuccess, "XML parsing failed")

            val vastAds = initialResult.getOrNull()
            requireNotNull(vastAds) { "Parsed VAST ads should not be null" }
            assertTrue(vastAds.isNotEmpty(), "Parsed VAST ads should not be empty")

            // Debug output before cache operation
            println("Test preparation:")
            println("VastAds count: ${vastAds.size}")
            println("First ad ID: ${vastAds.firstOrNull()?.id}")

            // Store in cache with retries
            var cacheSuccess = false
            repeat(3) { attempt ->
                if (!cacheSuccess) {
                    println("Cache attempt ${attempt + 1}")
                    cacheSuccess = vastParser.addToCache(tileId, vastAds)
                    if (!cacheSuccess) {
                        delay(100) // Small delay between attempts
                    }
                }
            }

            // Assert and provide detailed failure info
            if (!cacheSuccess) {
                println("Cache operation failed after retries")
                println("Original data:")
                println("- VastAds size: ${vastAds.size}")
                println("- First ad: ${vastAds.firstOrNull()}")
                val cachedContent = vastParser.getFromCache(tileId)
                println("Cache state:")
                println("- Cached content: $cachedContent")
            }

            assertTrue(cacheSuccess, "Cache operation should succeed")

            // Verify cached content
            val cachedAds = vastParser.getFromCache(tileId)
            assertNotNull(cachedAds, "Cached content should not be null")
            assertEquals(vastAds.size, cachedAds!!.size, "Cached ads count should match")

        } catch (e: Exception) {
            e.printStackTrace()
            fail("Test failed with exception: ${e.message}")
        }
    }

    @Test
    fun `parseVastUrl should handle timeout correctly`() = runTest {
        try {
            // Given
            val tileId = "test_tile_timeout"
            val longRunningUrl = "http://example.com/vast.xml" // URL that would timeout

            // When
            val result = vastParser.parseVastUrl(tileId, longRunningUrl)

            // Then
            assertTrue(result.isFailure)
            val error = result.exceptionOrNull()
            assertNotNull(error)
            assertTrue(error is TimeoutCancellationException || error is IOException)
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
        }
    }

    @Test
    fun `parseVastXml should handle large XML responses`() = runTest {
        try {
            // Given
            val largeXml = StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<VAST version=\"3.0\">")

            // Add multiple ads to create large XML
            repeat(100) { index ->
                largeXml.append("""
                <Ad id="ad_$index" sequence="${index + 1}">
                    <InLine>
                        <AdSystem>Test System</AdSystem>
                        <AdTitle>Test Ad $index</AdTitle>
                        <Impression>http://example.com/impression_$index</Impression>
                        <Creatives>
                            <Creative id="creative_$index">
                                <Linear>
                                    <Duration>00:00:30</Duration>
                                    <MediaFiles>
                                        <MediaFile delivery="progressive" type="video/mp4" bitrate="500" width="400" height="300">
                                            http://example.com/video_$index.mp4
                                        </MediaFile>
                                    </MediaFiles>
                                </Linear>
                            </Creative>
                        </Creatives>
                    </InLine>
                </Ad>
            """.trimIndent())
            }
            largeXml.append("</VAST>")

            // When
            val result = vastParser.parseVastXmlTest(largeXml.toString().byteInputStream())

            // Then
            assertTrue(result.isSuccess)
            result.fold(
                onSuccess = { vastAds ->
                    assertEquals(100, vastAds.size)
                    vastAds.forEachIndexed { index, ad ->
                        assertEquals("ad_$index", ad.id)
                        assertEquals(index + 1, ad.sequence)
                    }
                },
                onFailure = { error ->
                    fail("Should handle large XML: ${error.message}")
                }
            )
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
        }
    }

    @Test
    fun `parseVastXml should handle concurrent requests`() = runTest {
        try {
            // Given
            val inputStreamBytes = loadTestResource("vast_mock_response.xml").readBytes()

            val requests = (1..5).map { index ->
                async {
                    // Create new input stream for each request
                    vastParser.parseVastXmlTest(inputStreamBytes.inputStream())
                }
            }

            // When
            val results = requests.awaitAll()

            // Then
            results.forEach { result ->
                assertTrue(result.isSuccess)
                result.fold(
                    onSuccess = { vastAds ->
                        assertNotNull(vastAds)
                        assertTrue(vastAds.isNotEmpty())
                    },
                    onFailure = { error ->
                        fail("Concurrent request failed: ${error.message}")
                    }
                )
            }
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
        }
    }

    @Test
    fun `clearCache should remove all cached entries`() = runTest {
        try {
            // Given
            val inputStream = loadTestResource("vast_mock_response.xml")
            val tileId = "test_tile_clear_cache"
            val vastUrl = "https://example.com/vast.xml"  // Added valid URL format

            // When - Parse and cache
            val initialResult = vastParser.parseVastXmlTest(inputStream)
            assertTrue(initialResult.isSuccess)

            // Store in cache manually
            initialResult.onSuccess { vastAds ->
                // Use reflection or expose a test method to add to cache
                val cacheField = VastParser::class.java.getDeclaredField("vastCache")
                cacheField.isAccessible = true
                val cache = cacheField.get(vastParser) as LruCache<String, VastParser.CacheEntry>
                cache.put(tileId, VastParser.CacheEntry(vastAds))
            }

            // Clear cache
            vastParser.clearCache()

            // Try to get from cache via parseVastUrl
            val result = vastParser.parseVastUrl(tileId, vastUrl)

            // Then - Should fail because cache was cleared
            assertTrue(result.isFailure) { "Cache should be empty after clearing" }
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
        }
    }

    class MainDispatcherRule : TestRule {
        private val testDispatcher = StandardTestDispatcher()

        override fun apply(base: Statement, description: Description) = object : Statement() {
            override fun evaluate() {
                Dispatchers.setMain(testDispatcher)
                try {
                    base.evaluate()
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
    }
}