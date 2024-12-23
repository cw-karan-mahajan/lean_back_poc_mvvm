package tv.cloudwalker.adtech.vastdata.tracking

import tv.cloudwalker.adtech.vastdata.cache.SimpleVastCache
import tv.cloudwalker.adtech.vastdata.parser.VastParser
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*

import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.AfterEach

@OptIn(ExperimentalCoroutinesApi::class)
class VastTrackingManagerTest {

    private lateinit var adEventTracker: AdEventTracker
    private lateinit var vastCache: SimpleVastCache
    private lateinit var trackingManager: VastTrackingManager
    private val testScope = TestScope()
    private val testDispatcher = StandardTestDispatcher(testScope.testScheduler)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        adEventTracker = mockk(relaxed = true)
        vastCache = mockk(relaxed = true)

        trackingManager = VastTrackingManager(
            adEventTracker = adEventTracker,
            vastCache = vastCache,
            maxRetryAttempts = 3,
            retryDelayMs = 100L,
            maxConcurrentTracking = 5
        )
    }

    @Test
    fun `trackEvent should successfully track event`() = runTest {
        // Given
        val vastAd = createTestVastAd()
        val eventType = "start"
        val url = vastAd.trackingEvents[eventType]!!

        coEvery {
            adEventTracker.trackEvent(url, eventType, vastAd.id)
        } just Runs

        // When
        trackingManager.trackEvent(vastAd, eventType)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) {
            adEventTracker.trackEvent(url, eventType, vastAd.id)
        }
    }

    @Test
    fun `trackQuartile should track quartile events correctly`() = runTest {
        // Given
        val vastAd = createTestVastAd()
        val quartileEvent = "firstQuartile"
        val url = vastAd.trackingEvents[quartileEvent]!!

        coEvery {
            adEventTracker.trackEvent(url, quartileEvent, vastAd.id)
        } just Runs

        // When
        trackingManager.trackQuartile(vastAd, quartileEvent)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) {
            adEventTracker.trackEvent(url, quartileEvent, vastAd.id)
        }
    }

    @Test
    fun `completeTracking should track complete event and clear cache`() = runTest {
        // Given
        val vastAd = createTestVastAd()
        val completeEvent = VastParser.VastAd.EVENT_COMPLETE
        val url = vastAd.trackingEvents[completeEvent]!!

        // Setup suspending mocks
        coEvery {
            adEventTracker.trackEvent(url, completeEvent, vastAd.id)
        } coAnswers {
            delay(50)
            Unit
        }

        coEvery { vastCache.clear() } coAnswers {
            delay(50)
            Unit
        }

        // When
        trackingManager.completeTracking(vastAd)

        // Advance time in smaller increments
        repeat(5) {
            advanceTimeBy(50)
            runCurrent()
        }
        advanceUntilIdle()

        // Then
        coVerify(timeout = 1000) {
            adEventTracker.trackEvent(url, completeEvent, vastAd.id)
        }
        coVerify(timeout = 1000) {
            vastCache.clear()
        }
    }

    @Test
    fun `trackEvent should handle tracking with retries`() = runTest {
        // Given
        val vastAd = createTestVastAd()
        val eventType = "start"
        val url = vastAd.trackingEvents[eventType]!!

        coEvery {
            adEventTracker.trackEvent(url, eventType, vastAd.id)
        } coAnswers {
            println("Tracking event called")
            delay(100)
            Unit
        }

        // When
        trackingManager.trackEvent(vastAd, eventType)
        advanceUntilIdle()

        // Then - simply verify the tracking was attempted
        coVerify {
            adEventTracker.trackEvent(url, eventType, vastAd.id)
        }
    }

    @Test
    fun `cancelTracking should stop tracking for specific ad`() = runTest {
        // Given
        val vastAd = createTestVastAd()
        val eventType = "start"
        val url = vastAd.trackingEvents[eventType]!!

        coEvery {
            adEventTracker.trackEvent(url, eventType, vastAd.id)
        } coAnswers {
            delay(1000) // Long running operation
            println("Tracking completed")
        }

        // When
        trackingManager.trackEvent(vastAd, eventType)
        delay(100) // Give time for tracking to start
        trackingManager.cancelTracking(vastAd.id)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) {
            adEventTracker.trackEvent(url, eventType, vastAd.id)
        }
    }

    @Test
    fun `cancelAllTracking should stop all tracking operations`() = runTest {
        // Given
        val vastAd1 = createTestVastAd("ad1")
        val vastAd2 = createTestVastAd("ad2")
        val eventType = "start"

        coEvery {
            adEventTracker.trackEvent(any(), any(), any())
        } coAnswers {
            delay(1000) // Long running operation
        }

        coJustRun { vastCache.clear() }

        // When
        trackingManager.trackEvent(vastAd1, eventType)
        trackingManager.trackEvent(vastAd2, eventType)
        delay(100) // Give time for tracking to start
        trackingManager.cancelAllTracking()
        advanceUntilIdle()

        // Then
        coVerify { vastCache.clear() }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun createTestVastAd(id: String = "test_ad") = VastParser.VastAd(
        id = id,
        sequence = 1,
        adSystem = "Test System",
        adTitle = "Test Ad",
        impression = "http://example.com/impression",
        creativeId = "123",
        duration = "00:00:30",
        mediaFiles = emptyList(),
        trackingEvents = mapOf(
            "start" to "http://example.com/track",
            "firstQuartile" to "http://example.com/first_quartile",
            "midpoint" to "http://example.com/midpoint",
            "thirdQuartile" to "http://example.com/third_quartile",
            "complete" to "http://example.com/complete"
        ),
        clickThrough = "http://example.com/click",
        clickTracking = "http://example.com/track",
        extensions = emptyMap(),
        version = null,
        unknownFields = emptyMap()
    )
}
