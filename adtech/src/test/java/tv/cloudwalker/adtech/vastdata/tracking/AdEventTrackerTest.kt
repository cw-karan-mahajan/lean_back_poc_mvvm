package tv.cloudwalker.adtech.vastdata.tracking

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Before
import org.junit.Test
import tv.cloudwalker.adtech.vastdata.network.NetworkConnectivity
import tv.cloudwalker.adtech.vastdata.parser.VastParser
import java.io.IOException
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class AdEventTrackerTest {

    private lateinit var httpClient: OkHttpClient
    private lateinit var networkConnectivity: NetworkConnectivity
    private lateinit var adEventTracker: AdEventTracker
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        httpClient = mockk()
        networkConnectivity = mockk()
        adEventTracker = AdEventTracker(
            httpClient = httpClient,
            networkConnectivity = networkConnectivity,
            retryAttempts = 3,
            retryDelayMs = 100L
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `trackEvent should successfully track event`() = runTest {
        // Given
        val url = "http://example.com/track"
        val eventType = "start"
        val vastAdId = "test_ad"
        val call = mockk<Call>()
        val response = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

        every { networkConnectivity.isConnected() } returns true
        every { httpClient.newCall(any()) } returns call
        coEvery { call.execute() } returns response

        // When
        adEventTracker.trackEvent(url, eventType, vastAdId)
        advanceUntilIdle()

        // Then
        verify {
            httpClient.newCall(match { request ->
                request.url.toString() == url
            })
        }
        coVerify(exactly = 1) { call.execute() }
    }

    @Test
    fun `trackEvent should retry on failure`() = runTest {
        // Given
        val url = "http://example.com/track"
        val eventType = "start"
        val vastAdId = "test_ad"
        val call = mockk<Call>()

        every { networkConnectivity.isConnected() } returns true
        every { httpClient.newCall(any()) } returns call

        val responses = mutableListOf(
            Response.Builder()
                .request(Request.Builder().url(url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Error")
                .build(),
            Response.Builder()
                .request(Request.Builder().url(url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        )

        coEvery { call.execute() } coAnswers {
            responses.removeFirst().also {
                delay(50) // Add small delay to simulate network call
            }
        }

        // When
        launch {
            adEventTracker.trackEvent(url, eventType, vastAdId)
        }

        // Advance enough to handle initial call and retry
        advanceTimeBy(500)
        runCurrent()

        // Then
        verify {
            httpClient.newCall(match { request ->
                request.url.toString() == url
            })
        }

        coVerify {
            call.execute()
            call.execute() // Verify called twice
        }
    }

    @Test
    fun `trackEvent should handle network connectivity`() = runTest {
        // Given
        val url = "http://example.com/track"
        val eventType = "start"
        val vastAdId = "test_ad"

        every { networkConnectivity.isConnected() } returns false

        // When
        adEventTracker.trackEvent(url, eventType, vastAdId)
        advanceUntilIdle()

        // Then
        verify(exactly = 0) { httpClient.newCall(any()) }
    }

    @Test
    fun `trackClick should track click events`() = runTest {
        // Given
        val vastAd = createTestVastAd()
        val call = mockk<Call>()
        val response = Response.Builder()
            .request(Request.Builder().url(vastAd.clickTracking!!).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

        every { networkConnectivity.isConnected() } returns true
        every { httpClient.newCall(any()) } returns call
        coEvery { call.execute() } returns response

        // When
        adEventTracker.trackClick(vastAd)
        advanceUntilIdle()

        // Then
        verify {
            httpClient.newCall(match { request ->
                request.url.toString() == vastAd.clickTracking
            })
        }
        coVerify(exactly = 1) { call.execute() }
    }

    @Test
    fun `trackEvent should handle timeout exceptions`() = runTest {
        // Given
        val url = "http://example.com/track"
        val eventType = "start"
        val vastAdId = "test_ad"
        val call = mockk<Call>()

        every { networkConnectivity.isConnected() } returns true
        every { httpClient.newCall(any()) } returns call
        coEvery { call.execute() } throws SocketTimeoutException("Timeout")

        // When
        adEventTracker.trackEvent(url, eventType, vastAdId)
        advanceUntilIdle()

        // Then
        coVerify(atLeast = 1, atMost = 3) { call.execute() }
    }

    @Test
    fun `trackEvent should handle IO exceptions`() = runTest {
        // Given
        val url = "http://example.com/track"
        val eventType = "start"
        val vastAdId = "test_ad"
        val call = mockk<Call>()

        every { networkConnectivity.isConnected() } returns true
        every { httpClient.newCall(any()) } returns call
        coEvery { call.execute() } throws IOException("Network error")

        // When
        adEventTracker.trackEvent(url, eventType, vastAdId)
        advanceUntilIdle()

        // Then
        coVerify(atLeast = 1, atMost = 3) { call.execute() }
    }

    private fun createTestVastAd() = VastParser.VastAd(
        id = "test_ad",
        sequence = 1,
        adSystem = "Test System",
        adTitle = "Test Ad",
        impression = "http://example.com/impression",
        creativeId = "123",
        duration = "00:00:30",
        mediaFiles = emptyList(),
        trackingEvents = mapOf(
            "start" to "http://example.com/start",
            "complete" to "http://example.com/complete"
        ),
        clickThrough = "http://example.com/click",
        clickTracking = "http://example.com/track",
        extensions = emptyMap(),
        version = null,
        unknownFields = emptyMap()
    )
}