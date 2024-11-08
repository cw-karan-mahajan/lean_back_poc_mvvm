package com.example.leanbackpocmvvm.views.viewmodel

import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.*
import androidx.leanback.widget.*
import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.core.Resource
import com.example.leanbackpocmvvm.models.MyData2
import com.example.leanbackpocmvvm.models.RowItemX
import com.example.leanbackpocmvvm.repository.MainRepository
import com.example.leanbackpocmvvm.repository.MainRepository1
import com.example.leanbackpocmvvm.views.exoplayer.ExoPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MainRepository,
    private val apiRepository1: MainRepository1,
    private val exoPlayerManager: ExoPlayerManager,
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

    private val AUTO_SCROLL_DELAY = 5000L // 5 seconds
    private val VIDEO_START_DELAY = 5000L // 5 seconds delay before playing video
    private val USER_IDLE_DELAY = 5000L // 5 seconds
    var mRowsAdapter: ArrayObjectAdapter? = null
    private val _fullyVisibleTileIds = mutableSetOf<String>()

    private val _playedVideoTileIds = mutableSetOf<String>()
    val playedVideoTileIds: Set<String> = _playedVideoTileIds


    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val myData2 = repository.getMyData()
                _uiState.value = UiState.Success(myData2)
            } catch (e: Exception) {
                _toastMessage.postValue("Error loading data: ${e.message}")
            }
        }
    }

    fun fetchData() {
        viewModelScope.launch {
            apiRepository1.fetchList().distinctUntilChanged().collect { response ->
                when (response) {
                    is Resource.Success -> {
                        _uiState.value = UiState.Success(response.data)
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
            }

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

            currentPlayingRowIndex = rowIndex
            currentPlayingItemIndex = itemIndex
        }
    }

    private fun isAutoScrollableRow(rowIndex: Int): Boolean {
        val row = mRowsAdapter?.get(rowIndex) as? ListRow
        val firstItem = row?.adapter?.get(0) as? CustomRowItemX
        return firstItem?.layout == "landscape" && firstItem.rowHeader == "bannerAd"
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
                currentAutoScrollItemIndex = (currentAutoScrollItemIndex + 1) % itemAdapter.size()
                val nextItem = itemAdapter.get(currentAutoScrollItemIndex) as? CustomRowItemX
                if (nextItem != null) {
                    Log.d(
                        TAG,
                        "Auto-scrolling to: rowIndex=$currentAutoScrollRowIndex, itemIndex=$currentAutoScrollItemIndex"
                    )
                    _autoScrollCommand.value =
                        AutoScrollCommand(currentAutoScrollRowIndex, currentAutoScrollItemIndex)
                    _shrinkCardCommand.value = nextItem.rowItemX.tid

                    if (nextItem.rowItemX.videoUrl != null) {
                        scheduleVideoPlay(
                            nextItem,
                            currentAutoScrollRowIndex,
                            currentAutoScrollItemIndex
                        )
                    } else {
                        // For non-video tiles, wait 5 seconds before moving to the next item
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
    }

    fun addFullyVisibleTileId(tileId: String) {
        if (_fullyVisibleTileIds.add(tileId)) {
            // The add() method returns true if the element was added to the set
            Log.d(TAG, "Added new fully visible tile ID: $tileId")
        }
    }

    private fun addPlayedVideoTileId(tileId: String) {
        if (_playedVideoTileIds.add(tileId)) {
            // The add() method returns true if the element was added to the set
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
                // For auto-scrollable rows, wait 5 seconds before resuming auto-scroll
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
        item.rowItemX.videoUrl?.let { videoUrl ->
            _preloadVideoCommand.value = PreloadVideoCommand(videoUrl)
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
        const val TAG = "MainViewModel"
    }
}

data class CustomRowItemX(val rowItemX: RowItemX, val layout: String, val rowHeader: String) {
    val contentData: ContentData
        get() = ContentData(
            imageUrl = if (layout == "landscape") rowItemX.poster else rowItemX.portrait ?: "",
            width = rowItemX.tileWidth?.toIntOrNull() ?: 300,
            height = rowItemX.tileHeight?.toIntOrNull() ?: 225,
            isLandscape = layout == "landscape" && rowHeader == "bannerAd",
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

data class AutoScrollCommand(val rowIndex: Int, val itemIndex: Int)

data class PreloadVideoCommand(val videoUrl: String)