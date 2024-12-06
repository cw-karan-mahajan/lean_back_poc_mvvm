package com.example.leanbackpocmvvm.vastdata.parser

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.MockKAnnotations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    //private val testDispatcher = StandardTestDispatcher()
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

    private fun verifyTestResources() {
        assertNotNull(loadTestResource("vast_mock_response.xml"))
        assertNotNull(loadTestResource("vast_missing_fields.xml"))
        assertNotNull(loadTestResource("vast_empty_mediafiles.xml"))
    }

    private fun loadTestResource(fileName: String): InputStream {
        return javaClass.classLoader?.getResourceAsStream(fileName)
            ?: throw IllegalStateException("Could not load resource: $fileName")
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
            assertTrue(error is Exception) { "Expected error to be an Exception" }
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
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
            // Check for both exception types since either could be valid
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