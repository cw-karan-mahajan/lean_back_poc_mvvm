package com.example.leanbackpocmvvm.views.viewmodel

import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.*
import androidx.leanback.widget.*
import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.core.Resource
import com.example.leanbackpocmvvm.models.AdResponse
import com.example.leanbackpocmvvm.models.MyData2
import com.example.leanbackpocmvvm.models.RowItemX
import com.example.leanbackpocmvvm.repository.AdRepository
import com.example.leanbackpocmvvm.repository.MainRepository
import com.example.leanbackpocmvvm.repository.MainRepository1
import com.example.leanbackpocmvvm.repository.VastRepository
import com.example.leanbackpocmvvm.vastdata.parser.VastParser
import com.example.leanbackpocmvvm.views.exoplayer.ExoPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Collections
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MainRepository,
    private val apiRepository1: MainRepository1,
    private val adRepository: AdRepository,
    private val exoPlayerManager: ExoPlayerManager,
    private val vastRepository: VastRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _networkStatus = MutableLiveData<Boolean>()
    val networkStatus: LiveData<Boolean> = _networkStatus

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    private val _playVideoCommand = MutableLiveData<PlayVideoCommand>()
    val playVideoCommand: LiveData<PlayVideoCommand> = _playVideoCommand

    private val _autoScrollCommand = MutableLiveData<AutoScrollCommand>()
    val autoScrollCommand: LiveData<AutoScrollCommand> = _autoScrollCommand

    private val _shrinkCardCommand = MutableLiveData<String>()
    val shrinkCardCommand: LiveData<String> = _shrinkCardCommand

    private val _resetCardCommand = MutableLiveData<String>()
    val resetCardCommand: LiveData<String> = _resetCardCommand

    private val _videoPlaybackState = MutableLiveData<VideoPlaybackState>()
    val videoPlaybackState: LiveData<VideoPlaybackState> = _videoPlaybackState

    private val _preloadVideoCommand = MutableLiveData<PreloadVideoCommand>()
    val preloadVideoCommand: LiveData<PreloadVideoCommand> = _preloadVideoCommand

    private var autoScrollJob: Job? = null
    private var playbackJob: Job? = null
    private var delayJob: Job? = null
    private var userInteractionJob: Job? = null
    private var currentAutoScrollRowIndex = -1
    private var currentAutoScrollItemIndex = -1
    private var currentPlayingRowIndex: Int = -1
    private var currentPlayingItemIndex: Int = -1
    private var isCurrentRowAutoScrollable: Boolean = false
    private var isVideoPlaying = false
    private var pendingVideoPlay: CustomRowItemX? = null
    private var currentlyPlayingVideoTileId: String? = null
    private var lastInteractionTime = 0L

    private val AUTO_SCROLL_DELAY = 5000L
    private val VIDEO_START_DELAY = 5000L
    private val USER_IDLE_DELAY = 5000L
    var mRowsAdapter: ArrayObjectAdapter? = null
    private val _fullyVisibleTileIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val _playedVideoTileIds = mutableSetOf<String>()
    val playedVideoTileIds: Set<String> = _playedVideoTileIds

    private val _pendingImpressions = Collections.synchronizedList(mutableListOf<Pair<String, String>>())
    private val _trackedImpressionTileIds = Collections.synchronizedSet(mutableSetOf<String>())
    private var impressionTrackingJob: Job? = null
    private var currentlyPlayingAdTileId: String? = null

    fun loadData() {
        viewModelScope.launch {
            try {
                val myData2 = repository.getMyData()
                val adUrls = myData2.rows
                    .filter { it.rowLayout == "landscape" && it.rowAdConfig != null && it.rowAdConfig.rowAdType == "typeAdsBanner" }
                    .flatMap { it.rowItems }
                    .mapNotNull { it.adsServer }
                Log.d("Debug", "AdUrls prepared: ${adUrls.size}")
                val adResponses = adRepository.fetchAds(adUrls)
                Log.d("Debug", "Ad responses received")
                updateDataWithAds(myData2, adResponses)
                Log.d("Debug", "Data updated with ads")
                _uiState.value = UiState.Success(myData2)
            } catch (e: Exception) {
                _toastMessage.value = "Error loading data: ${e.message}"
            }
        }
    }

    private fun updateDataWithAds(
        data: MyData2,
        adResponses: List<Pair<String, Resource<AdResponse>>>
    ) {
        data.rows.forEach { row ->
            if (row.rowLayout == "landscape" && row.rowAdConfig != null && row.rowAdConfig.rowAdType == "typeAdsBanner") {
                row.rowItems.forEach { item ->
                    item.adsServer?.let { adUrl ->
                        val adResponse = adResponses.find { it.first == adUrl }?.second
                        if (adResponse is Resource.Success) {
                            val imageUrl = adResponse.data.seatbid
                                ?.firstOrNull()?.bid
                                ?.firstOrNull()?.parsedImageUrl
                            item.adImageUrl = imageUrl
                        }
                    }
                }
            }
        }
    }

    fun fetchData() {
        viewModelScope.launch {
            apiRepository1.fetchList().distinctUntilChanged().collect { response ->
                when (response) {
                    is Resource.Success -> {
                        val myData2 = response.data
                        val adUrls = myData2.rows
                            .filter { it.rowLayout == "landscape" && it.rowAdConfig != null && it.rowAdConfig.rowAdType == "typeAdsBanner" }
                            .flatMap { it.rowItems }
                            .mapNotNull { it.adsServer }
                        val adResponses = adRepository.fetchAds(adUrls)
                        updateDataWithAds(myData2, adResponses)
                        _uiState.value = UiState.Success(myData2)
                    }

                    is Resource.Error -> {
                        _uiState.value = UiState.Error(response.message)
                        _toastMessage.postValue("Error loading data: ${response.message}")
                    }

                    is Resource.Loading -> {}
                }
            }
        }
    }

    fun setNetworkStatus(isConnected: Boolean) {
        if (isConnected) {
            _toastMessage.value = "Network connected"
        } else {
            _toastMessage.value = "Network disconnected"
        }
        _networkStatus.value = isConnected
    }

    fun onItemFocused(item: CustomRowItemX, rowIndex: Int, itemIndex: Int) {
        viewModelScope.launch {
            lastInteractionTime = System.currentTimeMillis()

            if (rowIndex != currentPlayingRowIndex || itemIndex != currentPlayingItemIndex) {
                withContext(Dispatchers.Main) {
                    stopAndShrinkPreviousItem()
                }
                cancelPendingPlayback()
                pauseAutoScroll()

                // Handle VAST video ads
                if (item.rowItemX.tileType == "typeAdsVideoBanner" && !item.rowItemX.adsVideoUrl.isNullOrEmpty()) {
                    handleVastAd(item)
                } else {
                    // Existing video handling
                    isCurrentRowAutoScrollable = isAutoScrollableRow(rowIndex)
                    if (isCurrentRowAutoScrollable) {
                        if (item.rowItemX.videoUrl != null) {
                            scheduleVideoPlay(item, rowIndex, itemIndex)
                        } else {
                            scheduleAutoScrollResume(rowIndex, itemIndex)
                        }
                    } else if (item.rowItemX.videoUrl != null) {
                        scheduleVideoPlay(item, rowIndex, itemIndex)
                    }
                }
            }

            currentPlayingRowIndex = rowIndex
            currentPlayingItemIndex = itemIndex
        }
    }

    private fun handleVastAd(item: CustomRowItemX) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting VAST ad processing for tileId: ${item.rowItemX.tid}")
                Log.d(TAG, "VAST URL: ${item.rowItemX.adsVideoUrl}")

                vastRepository.parseVastAd(item.rowItemX.adsVideoUrl ?: "", item.rowItemX.tid)
                    .collect { resource ->
                        when (resource) {
                            is Resource.Success -> {
                                val vastAd = resource.data
                                //Log.d(TAG, "VAST Media Files: ${item.rowItemX.adsVideoUrl}")
                                //logVastAdDetails(vastAd)

                                // For now, just log the media files
                                // Later we'll handle media selection based on network speed
                            }

                            is Resource.Error -> {
                                Log.e(TAG, "Error loading VAST ad: ${resource.message}")
                                _toastMessage.value = "Error loading video ad"
                            }

                            is Resource.Loading -> {
                                Log.d(TAG, "Loading VAST ad...")
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling VAST ad: ${e.message}")
                _toastMessage.value = "Error processing video ad"
            }
        }
    }

    private fun logVastAdDetails(vastAd: VastParser.VastAd) {
        Log.d(TAG, "=== VAST Ad Details ===")
        Log.d(TAG, "Ad TITLE: ${vastAd.adTitle}")
        Log.d(TAG, "Ad ID: ${vastAd.id}")
        Log.d(TAG, "Duration: ${vastAd.duration}")

        Log.d(TAG, "mediaFiles: ${vastAd.mediaFiles.size}")

        Log.d(TAG, "--- Available MediaFiles ---")
        vastAd.mediaFiles.forEach { mediaFile ->
            Log.d(
                TAG, """
                MediaFile:
                URL: ${mediaFile.url}
                Bitrate: ${mediaFile.bitrate}
                Resolution: ${mediaFile.width}x${mediaFile.height}
                Type: ${mediaFile.type}
                Delivery: ${mediaFile.delivery}
            """.trimIndent()
            )
        }

        // Log highest quality file
        vastAd.mediaFiles.maxByOrNull { it.bitrate }?.let { highestQuality ->
            Log.d(TAG, "Highest quality MediaFile:")
            Log.d(TAG, "- Bitrate: ${highestQuality.bitrate}")
            Log.d(TAG, "- Resolution: ${highestQuality.width}x${highestQuality.height}")
            Log.d(TAG, "- URL: ${highestQuality.url}")
        }

        Log.d(TAG, "========================")
    }

    private fun isAutoScrollableRow(rowIndex: Int): Boolean {
        val row = mRowsAdapter?.get(rowIndex) as? ListRow
        val firstItem = row?.adapter?.get(0) as? CustomRowItemX
        return firstItem?.layout == "landscape" && firstItem.rowAdType == "typeAdsBanner"
    }

    private fun pauseAutoScroll() {
        autoScrollJob?.cancel()
        delayJob?.cancel()
    }

    private fun scheduleAutoScrollResume(rowIndex: Int, itemIndex: Int) {
        userInteractionJob?.cancel()
        userInteractionJob = viewModelScope.launch {
            delay(USER_IDLE_DELAY)
            if (System.currentTimeMillis() - lastInteractionTime >= USER_IDLE_DELAY) {
                startAutoScroll(rowIndex, itemIndex)
            }
        }
    }

    private fun startAutoScroll(rowIndex: Int, itemIndex: Int) {
        currentAutoScrollRowIndex = rowIndex
        currentAutoScrollItemIndex = itemIndex
        scheduleNextAutoScrollItem()
    }

    private fun scheduleNextAutoScrollItem() {
        autoScrollJob?.cancel()
        delayJob?.cancel()

        autoScrollJob = viewModelScope.launch {
            val rowAdapter = mRowsAdapter?.get(currentAutoScrollRowIndex) as? ListRow
            val itemAdapter = rowAdapter?.adapter as? ArrayObjectAdapter
            if (itemAdapter != null && itemAdapter.size() > 0) {
                val lastIndex = itemAdapter.size() - 1
                val isJumpingToStart = currentAutoScrollItemIndex == lastIndex
                currentAutoScrollItemIndex =
                    if (isJumpingToStart) 0 else (currentAutoScrollItemIndex + 1)
                val nextItem = itemAdapter[currentAutoScrollItemIndex] as? CustomRowItemX
                if (nextItem != null) {
                    Log.d(
                        TAG,
                        "Auto-scrolling to: rowIndex=$currentAutoScrollRowIndex, itemIndex=$currentAutoScrollItemIndex"
                    )
                    _autoScrollCommand.value = AutoScrollCommand(
                        currentAutoScrollRowIndex, currentAutoScrollItemIndex, isJumpingToStart
                    )
                    _shrinkCardCommand.value = nextItem.rowItemX.tid

                    if (nextItem.rowItemX.tileType == "typeAdsVideoBanner" && !nextItem.rowItemX.adsVideoUrl.isNullOrEmpty()) {
                        //handleVastAd(nextItem)
                    } else {
                        delayJob = launch {
                            delay(AUTO_SCROLL_DELAY)
                            if (System.currentTimeMillis() - lastInteractionTime >= USER_IDLE_DELAY) {
                                scheduleNextAutoScrollItem()
                            } else {
                                scheduleAutoScrollResume(
                                    currentAutoScrollRowIndex,
                                    currentAutoScrollItemIndex
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun scheduleVideoPlay(item: CustomRowItemX, rowIndex: Int, itemIndex: Int) {
        cancelPendingPlayback()
        pendingVideoPlay = item
        playbackJob = viewModelScope.launch {
            delay(VIDEO_START_DELAY)
            if (currentPlayingRowIndex == rowIndex && currentPlayingItemIndex == itemIndex) {
                playVideo(item, rowIndex, itemIndex)
            }
        }
    }

    private fun playVideo(item: CustomRowItemX, rowIndex: Int, itemIndex: Int) {
        item.rowItemX.videoUrl?.let { videoUrl ->
            addPlayedVideoTileId(item.rowItemX.tid)
            _videoPlaybackState.value = VideoPlaybackState.Playing(item.rowItemX.tid, videoUrl)
            currentlyPlayingVideoTileId = item.rowItemX.tid
            _playVideoCommand.value = PlayVideoCommand(videoUrl, item.rowItemX.tid)
        }
    }

    fun stopVideoPlayback() {
        viewModelScope.launch(Dispatchers.Main) {
            exoPlayerManager.releasePlayer()
            _videoPlaybackState.value = VideoPlaybackState.Stopped
            currentlyPlayingVideoTileId?.let { tileId ->
                _resetCardCommand.value = tileId
            }
            isVideoPlaying = false
            currentlyPlayingVideoTileId = null
            cancelPendingPlayback()
        }
    }

    fun onItemClicked(item: CustomRowItemX) {
        // Handle item click
    }

    fun onSearchClicked() {
        // Handle search click
    }

    fun onVideoEnded(tileId: String) {
        handleVideoEnded(tileId)
        currentlyPlayingAdTileId = null
    }

    fun onUserFocus(item: CustomRowItemX) {
        // Check if this tile's impression hasn't been tracked yet
        if (_trackedImpressionTileIds.add(item.rowItemX.tid)) {
            item.rowItemX.adsServer?.let { adsServerUrl ->
                Log.d(TAG, "Tracking impression for focused tile: ${item.rowItemX.tid}")
                val impTrackerUrls = adRepository.getImpressionTrackerUrls(adsServerUrl)
                impTrackerUrls.forEach { impTrackerUrl ->
                    Log.d(TAG, "impTrackerUrl: $impTrackerUrl")
                    _pendingImpressions.add(item.rowItemX.tid to impTrackerUrl)
                }
                if (impTrackerUrls.isNotEmpty()) {
                    scheduleImpressionTracking()
                }
            }
        }
    }

    fun addFullyVisibleTileId(tileId: String, customItem: CustomRowItemX?) {
        if (_fullyVisibleTileIds.add(tileId) && _trackedImpressionTileIds.add(tileId)) {
            customItem?.rowItemX?.adsServer?.let { adsServerUrl ->
                Log.d(TAG, "Added new fully visible tile ID: $tileId")
                // Get the impression tracker URLs for this ads_server URL
                val impTrackerUrls = adRepository.getImpressionTrackerUrls(adsServerUrl)
                impTrackerUrls.forEach { impTrackerUrl ->
                    _pendingImpressions.add(tileId to impTrackerUrl)
                }
                scheduleImpressionTracking()
            }
        }
    }

    private fun addPlayedVideoTileId(tileId: String) {
        if (_playedVideoTileIds.add(tileId)) {
            Log.d(TAG, "Added new played video tile ID: $tileId")
        }
    }

    private fun handleVideoEnded(tileId: String) {
        viewModelScope.launch(Dispatchers.Main) {
            _videoPlaybackState.value = VideoPlaybackState.Stopped
            _shrinkCardCommand.value = tileId
            isVideoPlaying = false
            currentlyPlayingVideoTileId = null

            if (isCurrentRowAutoScrollable) {
                delay(AUTO_SCROLL_DELAY)
                scheduleAutoScrollResume(currentPlayingRowIndex, currentPlayingItemIndex)
            }
        }
    }

    private fun stopAndShrinkPreviousItem() {
        currentlyPlayingVideoTileId?.let { tileId ->
            exoPlayerManager.releasePlayer()
            _videoPlaybackState.value = VideoPlaybackState.Stopped
            _resetCardCommand.value = tileId
            isVideoPlaying = false
            currentlyPlayingVideoTileId = null
        }
    }

    private fun cancelPendingPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        pendingVideoPlay = null
    }

    fun stopAutoScroll() {
        autoScrollJob?.cancel()
        delayJob?.cancel()
        userInteractionJob?.cancel()
        autoScrollJob = null
        delayJob = null
        userInteractionJob = null
        currentAutoScrollRowIndex = -1
        currentAutoScrollItemIndex = -1
    }

    fun preloadVideo(item: CustomRowItemX) {
        if (item.rowItemX.tileType == "typeAdsVideoBanner" && !item.rowItemX.adsVideoUrl.isNullOrEmpty()) {
            viewModelScope.launch {
                vastRepository.preloadVastAd(item.rowItemX.adsVideoUrl ?: "", item.rowItemX.tid)
                    .collect { resource ->
                        when (resource) {
                            is Resource.Success -> {
                                Log.d(
                                    TAG,
                                    "Successfully preloaded VAST ad for tileId: ${item.rowItemX.tid}"
                                )
                            }

                            is Resource.Error -> {
                                Log.e(TAG, "Error preloading VAST ad: ${resource.message}")
                            }

                            is Resource.Loading -> {
                                Log.d(TAG, "Preloading VAST ad...")
                            }
                        }
                    }
            }
        } else {
            item.rowItemX.videoUrl?.let { videoUrl ->
                _preloadVideoCommand.value = PreloadVideoCommand(videoUrl)
            }
        }
    }

    private fun scheduleImpressionTracking() {
        if (impressionTrackingJob?.isActive != true) {
            impressionTrackingJob = viewModelScope.launch {
                delay(100) // Small delay to batch impressions
                trackPendingImpressions()
            }
        }
    }

    private suspend fun trackPendingImpressions() {
        val impressions = _pendingImpressions.toList()
        _pendingImpressions.clear()

        if (impressions.isNotEmpty()) {
            try {
                val results = adRepository.trackImpressions(impressions)
                results.forEach { (tileId, result) ->
                    when (result) {
                        is Resource.Success -> {
                            Log.d(TAG, "Impression tracked successfully for tile: $tileId")
                        }

                        is Resource.Error -> {
                            Log.e(
                                TAG,
                                "Failed to track impression for tile: $tileId. Error: ${result.message}"
                            )
                            // If tracking failed, we might want to re-add to pending impressions
                            _pendingImpressions.add(tileId to impressions.first { it.first == tileId }.second)
                        }

                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error tracking impressions: ${e.message}", e)
                // Re-add all impressions back to the pending list
                _pendingImpressions.addAll(impressions)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
        cancelPendingPlayback()
        exoPlayerManager.releasePlayer()
        stopAutoScroll()
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}

data class CustomRowItemX(val rowItemX: RowItemX, val layout: String, val rowAdType: String?) {
    val contentData: ContentData
        get() = ContentData(
            imageUrl = if (layout == "landscape") rowItemX.poster else rowItemX.portrait ?: "",
            width = rowItemX.tileWidth?.toIntOrNull() ?: 300,
            height = rowItemX.tileHeight?.toIntOrNull() ?: 225,
            isLandscape = layout == "landscape" && rowAdType == "typeAdsBanner",
            isPortrait = layout == "portrait"
        )
}

sealed class VideoPlaybackState {
    data class Playing(val itemId: String, val videoUrl: String) : VideoPlaybackState()
    object Stopped : VideoPlaybackState()
}

sealed class UiState {
    object Loading : UiState()
    data class Success(val data: MyData2) : UiState()
    data class Error(val message: String) : UiState()
}

data class ContentData(
    val imageUrl: String,
    val width: Int,
    val height: Int,
    val isLandscape: Boolean,
    val isPortrait: Boolean
)

@OptIn(UnstableApi::class)
data class PlayVideoCommand(
    val videoUrl: String,
    val tileId: String
)

data class AutoScrollCommand(val rowIndex: Int, val itemIndex: Int, val isJumping: Boolean)
data class PreloadVideoCommand(val videoUrl: String)
