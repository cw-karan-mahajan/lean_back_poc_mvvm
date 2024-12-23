package tv.cloudwalker.adtech.vastdata.validator

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import tv.cloudwalker.adtech.vastdata.parser.VastParser
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.*

class VastMediaSelectorTest {

    private lateinit var context: Context
    private lateinit var windowManager: WindowManager
    private lateinit var displayMetrics: DisplayMetrics
    private lateinit var vastMediaSelector: VastMediaSelector

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        windowManager = mockk(relaxed = true)
        displayMetrics = DisplayMetrics().apply {
            widthPixels = 1920
            heightPixels = 1080
        }

        every { context.getSystemService(Context.WINDOW_SERVICE) } returns windowManager
        every { windowManager.defaultDisplay.getMetrics(any()) } answers {
            firstArg<DisplayMetrics>().apply {
                widthPixels = 1920
                heightPixels = 1080
            }
        }

        vastMediaSelector = VastMediaSelector(
            context = context,
            preferredMimeTypes = listOf("video/mp4", "video/webm"),
            maxBitrate = 1000 // Set to 1000 kbps for testing
        )
    }

    @Test
    fun `selectBestMediaFile should select media file with appropriate bitrate and resolution`() {
        // Create media files matching your VAST XML
        val mediaFiles = listOf(
            createMediaFile(
                url = "https://assets.springserve.com/video_creatives/000/971/207/videoplayback.1733406858609.publer.io-7243804.mp4",
                bitrate = 640,
                width = 640,
                height = 360,
                type = "video/mp4"
            ),
            createMediaFile(
                url = "https://assets.springserve.com/video_creatives/000/971/207/videoplayback.1733406858609.publer.io.mp4",
                bitrate = 627,
                width = 640,
                height = 360,
                type = "video/mp4"
            )
        )

        val vastAd = createVastAd(mediaFiles)

        // Select best media file
        val selectedFile = vastMediaSelector.selectBestMediaFile(vastAd)

        // Verify selection
        assertNotNull(selectedFile)
        assertEquals(640, selectedFile?.width)
        assertEquals(360, selectedFile?.height)
        assertEquals("video/mp4", selectedFile?.type)
    }

    @Test
    fun `selectBestMediaFile should handle empty media files`() {
        val vastAd = createVastAd(emptyList())
        val result = vastMediaSelector.selectBestMediaFile(vastAd)
        assertNull(result)
    }

    @Test
    fun `selectBestMediaFile should prefer progressive delivery`() {
        val mediaFiles = listOf(
            createMediaFile(delivery = "progressive"),
            createMediaFile(delivery = "streaming")
        )

        val vastAd = createVastAd(mediaFiles)
        val selectedFile = vastMediaSelector.selectBestMediaFile(vastAd)

        assertEquals("progressive", selectedFile?.delivery)
    }

    @Test
    fun `selectBestMediaFile should respect bitrate limits`() {
        val mediaFiles = listOf(
            createMediaFile(bitrate = 500),  // Within limit
            createMediaFile(bitrate = 1500)  // Above limit
        )

        val vastAd = createVastAd(mediaFiles)
        val selectedFile = vastMediaSelector.selectBestMediaFile(vastAd)

        assertNotNull(selectedFile)
        assertTrue(selectedFile!!.bitrate <= 1000)
    }

    @Test
    fun `selectBestMediaFile should prefer supported mime types`() {
        val mediaFiles = listOf(
            createMediaFile(type = "video/mp4"),
            createMediaFile(type = "video/webm"),
            createMediaFile(type = "video/x-unknown")
        )

        val vastAd = createVastAd(mediaFiles)
        val selectedFile = vastMediaSelector.selectBestMediaFile(vastAd)

        assertNotNull(selectedFile)
        assertTrue(selectedFile!!.type in listOf("video/mp4", "video/webm"))
    }

    @Test
    fun `selectBestMediaFile should handle resolution exceeding screen size`() {
        val mediaFiles = listOf(
            createMediaFile(width = 3840, height = 2160), // 4K
            createMediaFile(width = 1920, height = 1080)  // 1080p
        )

        val vastAd = createVastAd(mediaFiles)
        val selectedFile = vastMediaSelector.selectBestMediaFile(vastAd)

        assertNotNull(selectedFile)
        assertEquals(1920, selectedFile?.width)
        assertEquals(1080, selectedFile?.height)
    }

    @Test
    fun `selectBestMediaFile should handle invalid media file urls`() {
        // Given
        val mediaFiles = listOf(
            createMediaFile(
                url = "",  // Empty URL - Invalid
                bitrate = 0,
                width = 0,
                height = 0,
                type = "video/invalid",
                delivery = "invalid"
            ),
            createMediaFile(
                url = "http://test.com/video.mp4",  // Valid URL with valid properties
                bitrate = 500,
                width = 640,
                height = 360,
                type = "video/mp4",
                delivery = "progressive"
            )
        )

        val vastAd = VastParser.VastAd(
            id = "test_ad",
            sequence = 1,
            adSystem = "SpringServe",
            adTitle = "Test Ad",
            impression = "http://test.com/impression",
            creativeId = "123",
            duration = "00:00:30",
            mediaFiles = mediaFiles,
            trackingEvents = mapOf(
                "start" to "http://test.com/start",
                "complete" to "http://test.com/complete"
            ),
            clickThrough = "http://test.com/click",
            clickTracking = "http://test.com/track",
            extensions = emptyMap(),
            version = "3.0"
        )

        // When - force the coroutine to complete
        val selectedFile = vastMediaSelector.selectBestMediaFile(vastAd)

        // Then
        assertNotNull(selectedFile, "Selected file should not be null")
        assertEquals(
            "http://test.com/video.mp4",
            selectedFile?.url,
            "Should select the valid URL"
        )
        assertEquals(640, selectedFile?.width, "Should select valid width")
        assertEquals(360, selectedFile?.height, "Should select valid height")
        assertEquals(500, selectedFile?.bitrate, "Should select valid bitrate")
        assertEquals("video/mp4", selectedFile?.type, "Should select valid type")
        assertEquals("progressive", selectedFile?.delivery, "Should select valid delivery")

        // Additional verification
        val invalidFile = mediaFiles.first()
        assertNotEquals(invalidFile.url, selectedFile?.url, "Should not select the invalid URL")
    }

    @Test
    fun `selectBestMediaFile should handle all unsupported mime types`() {
        val mediaFiles = listOf(
            createMediaFile(type = "video/unknown"),
            createMediaFile(type = "video/invalid")
        )

        val vastAd = createVastAd(mediaFiles)
        val selectedFile = vastMediaSelector.selectBestMediaFile(vastAd)

        // Should return first media file when no supported types are found
        assertNotNull(selectedFile)
        assertEquals(mediaFiles.first(), selectedFile)
    }

    @Test
    fun `selectBestMediaFile should handle zero or negative dimensions`() {
        val mediaFiles = listOf(
            createMediaFile(width = 0, height = 0, bitrate = 300),
            createMediaFile(width = -1, height = -1, bitrate = 400),
            createMediaFile(  // Valid media file
                width = 640,
                height = 360,
                bitrate = 500,
                type = "video/mp4",
                url = "http://test.com/video.mp4"
            )
        )

        val vastAd = createVastAd(mediaFiles)
        val selectedFile = vastMediaSelector.selectBestMediaFile(vastAd)

        assertNotNull(selectedFile, "Selected file should not be null")
        assertEquals(640, selectedFile?.width, "Should select the valid width")
        assertEquals(360, selectedFile?.height, "Should select the valid height")
        assertEquals(500, selectedFile?.bitrate, "Should select the valid bitrate")
        assertEquals("video/mp4", selectedFile?.type, "Should select the valid type")
    }

    @Test
    fun `selectBestMediaFile should handle zero or negative bitrates`() {
        val mediaFiles = listOf(
            createMediaFile(bitrate = 0),
            createMediaFile(bitrate = -1),
            createMediaFile(bitrate = 500)
        )

        val vastAd = createVastAd(mediaFiles)
        val selectedFile = vastMediaSelector.selectBestMediaFile(vastAd)

        assertNotNull(selectedFile)
        assertEquals(500, selectedFile?.bitrate)
    }

    @Test
    fun `selectBestMediaFile should select closest resolution to screen size`() {
        val mediaFiles = listOf(
            createMediaFile(width = 426, height = 240),  // 240p
            createMediaFile(width = 640, height = 360),  // 360p
            createMediaFile(width = 854, height = 480),  // 480p
            createMediaFile(width = 1280, height = 720), // 720p
            createMediaFile(width = 1920, height = 1080) // 1080p
        )

        val vastAd = createVastAd(mediaFiles)
        val selectedFile = vastMediaSelector.selectBestMediaFile(vastAd)

        assertNotNull(selectedFile)
        // Should select 1080p since our test display is 1920x1080
        assertEquals(1920, selectedFile?.width)
        assertEquals(1080, selectedFile?.height)
    }

    private fun createMediaFile(
        url: String = "http://test.com/video.mp4",
        bitrate: Int = 500,
        width: Int = 640,
        height: Int = 360,
        type: String = "video/mp4",
        delivery: String = "progressive"
    ) = VastParser.MediaFile(
        url = url,
        bitrate = bitrate,
        width = width,
        height = height,
        type = type,
        delivery = delivery
    )

    private fun createVastAd(mediaFiles: List<VastParser.MediaFile>) = VastParser.VastAd(
        id = "1500075-1-n",
        sequence = 1,
        adSystem = "SpringServe",
        adTitle = "videoplayback",
        impression = "https://vid-io-sin.springserve.com/vd/i?event=vast_impression",
        creativeId = "971207",
        duration = "00:00:25",
        mediaFiles = mediaFiles,
        trackingEvents = mapOf(
            "start" to "https://vid-io-sin.springserve.com/vd/i?event=js_start",
            "firstQuartile" to "https://vid-io-sin.springserve.com/vd/i?event=js_first_quartile",
            "midpoint" to "https://vid-io-sin.springserve.com/vd/i?event=js_midpoint",
            "thirdQuartile" to "https://vid-io-sin.springserve.com/vd/i?event=js_third_quartile",
            "complete" to "https://vid-io-sin.springserve.com/vd/i?event=js_complete"
        ),
        clickThrough = "https://www.loreal.com/en/",
        clickTracking = "https://vid-io-sin.springserve.com/vd/i?event=js_click",
        extensions = mapOf("springserve_SpringServeCreativeId" to "681a1e5e65dbc04489e7fcd578925ade")
    )
}