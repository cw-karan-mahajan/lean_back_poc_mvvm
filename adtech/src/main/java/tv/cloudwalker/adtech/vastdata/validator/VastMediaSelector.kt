package tv.cloudwalker.adtech.vastdata.validator

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.leanbackpocmvvm.utils.getBandwidthBasedMaxBitrate
import com.example.leanbackpocmvvm.utils.getSupportedCodecs
import tv.cloudwalker.adtech.vastdata.parser.VastParser
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class VastMediaSelector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferredMimeTypes: List<String> = listOf(
        "video/mp4",
        "video/webm",
        "application/x-mpegURL"
    ),
    private val maxBitrate: Int = getBandwidthBasedMaxBitrate(context),
    private val supportedCodecs: List<String> = getSupportedCodecs()
) {

    private val displayMetrics: DisplayMetrics by lazy {
        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(metrics)
        metrics
    }

    data class MediaSelection(
        val mediaFile: VastParser.MediaFile,
        val score: Int,
        val reason: String
    )

    fun selectLowestBitrateMediaFile(vastAd: VastParser.VastAd): VastParser.MediaFile? {
        return vastAd.mediaFiles
            .filter { it.type == "video/mp4" }
            .minByOrNull { it.bitrate }
    }

    fun selectBestMediaFile(vastAd: VastParser.VastAd): VastParser.MediaFile? {
        try {
            val selections = vastAd.mediaFiles.mapNotNull { mediaFile ->
                scoreMediaFile(mediaFile)?.let { score ->
                    MediaSelection(mediaFile, score, getSelectionReason(mediaFile, score))
                }
            }

            if (selections.isEmpty()) {
                Timber.w(TAG, "No suitable media files found for VAST ad ${vastAd.id}")
                return vastAd.mediaFiles.firstOrNull()
            }

            val bestSelection = selections.maxByOrNull { it.score }
            bestSelection?.let {
                Timber.d(TAG, "Selected media file: ${it.reason}")
            }

            return bestSelection?.mediaFile
        } catch (e: Exception) {
            Timber.e(TAG, "Error selecting media file: ${e.message}")
            return vastAd.mediaFiles.firstOrNull()
        }
    }

    private fun scoreMediaFile(mediaFile: VastParser.MediaFile): Int? {
        var score = 0

        // Check if mime type is supported
        if (mediaFile.type !in preferredMimeTypes) {
            return null
        }

        // Score based on mime type preference
        score += (preferredMimeTypes.size - preferredMimeTypes.indexOf(mediaFile.type)) * 100

        // Check if dimensions are suitable
        if (!areDimensionsSuitable(mediaFile.width, mediaFile.height)) {
            return null
        }

        // Score based on resolution match
        score += scoreResolution(mediaFile.width, mediaFile.height)

        // Check and score bitrate
        if (mediaFile.bitrate > maxBitrate) {
            return null
        }
        score += scoreBitrate(mediaFile.bitrate)

        // Progressive delivery preferred
        if (mediaFile.delivery == "progressive") {
            score += 50
        }

        return score
    }

    private fun areDimensionsSuitable(width: Int, height: Int): Boolean {
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Allow if dimensions are within 20% larger than screen
        val maxWidth = (screenWidth * 1.2).toInt()
        val maxHeight = (screenHeight * 1.2).toInt()

        return width <= maxWidth && height <= maxHeight
    }

    private fun scoreResolution(width: Int, height: Int): Int {
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Calculate how close the resolution is to screen resolution
        val widthDiff = abs(screenWidth - width)
        val heightDiff = abs(screenHeight - height)

        // Convert to a score where closer to screen resolution is better
        return (1000 - (widthDiff + heightDiff) / 2).coerceAtLeast(0)
    }

    private fun scoreBitrate(bitrate: Int): Int {
        // Score higher for bitrates closer to but not exceeding max bitrate
        val bitrateScore = ((bitrate.toFloat() / maxBitrate) * 100).toInt()
        return if (bitrateScore > 100) 0 else bitrateScore
    }

    private fun getSelectionReason(mediaFile: VastParser.MediaFile, score: Int): String {
        return buildString {
            append("Selected: ${mediaFile.type} ${mediaFile.width}x${mediaFile.height} ")
            append("@${mediaFile.bitrate}kbps (Score: $score)")
            append(" - Delivery: ${mediaFile.delivery}")
        }
    }

    companion object {
        private const val TAG = "VastMediaSelector"
    }
}