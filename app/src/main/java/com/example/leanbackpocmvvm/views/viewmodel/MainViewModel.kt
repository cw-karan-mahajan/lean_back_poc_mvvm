package com.example.leanbackpocmvvm.views.viewmodel

import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.*
import androidx.leanback.widget.*
import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.models.MyData2
import com.example.leanbackpocmvvm.models.RowItemX
import com.example.leanbackpocmvvm.repository.MainRepository
import com.example.leanbackpocmvvm.utils.ExoPlayerManager
import com.example.leanbackpocmvvm.views.customview.NewVideoCardView
import com.example.leanbackpocmvvm.views.presenter.CardLayout1
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MainRepository,
    private val exoPlayerManager: ExoPlayerManager,
    private val coroutineScope: CoroutineScope
) : ViewModel() {

    private val _rowsAdapter = MutableLiveData<ArrayObjectAdapter>()
    val rowsAdapter: LiveData<ArrayObjectAdapter> = _rowsAdapter

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _networkStatus = MutableLiveData<Boolean>()
    val networkStatus: LiveData<Boolean> = _networkStatus

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    private val _videoPlaybackState = MutableLiveData<VideoPlaybackState>()
    val videoPlaybackState: LiveData<VideoPlaybackState> = _videoPlaybackState

    private val _playVideoCommand = MutableLiveData<PlayVideoCommand>()
    val playVideoCommand: LiveData<PlayVideoCommand> = _playVideoCommand

    private val _autoScrollCommand = MutableLiveData<AutoScrollCommand>()
    val autoScrollCommand: LiveData<AutoScrollCommand> = _autoScrollCommand

    private val _shrinkCardCommand = MutableLiveData<String>()
    val shrinkCardCommand: LiveData<String> = _shrinkCardCommand

    private val _resetCardCommand = MutableLiveData<String>()
    val resetCardCommand: LiveData<String> = _resetCardCommand

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
    private var currentPlayingTileId: String? = null
    private var lastPlayedNonScrollableTileId: String? = null
    private var currentlyPlayingVideoTileId: String? = null
    private var lastInteractionTime = 0L

    private val AUTO_SCROLL_DELAY = 5000L // 5 seconds
    private val VIDEO_START_DELAY = 5000L // 5 seconds delay before playing video
    private val USER_IDLE_DELAY = 5000L // 5 seconds

    fun loadData(lifecycleOwner: LifecycleOwner) {
        coroutineScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            try {
                val myData2 = repository.getMyData()
                createRowsAdapter(myData2, lifecycleOwner)
            } catch (e: Exception) {
                _toastMessage.postValue("Error loading data: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    private suspend fun createRowsAdapter(myData2: MyData2, lifecycleOwner: LifecycleOwner) {
        withContext(Dispatchers.Default) {
            val lrp = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE, false)
            lrp.shadowEnabled = false
            lrp.selectEffectEnabled = false
            val rowsAdapter = ArrayObjectAdapter(lrp)

            for (i in 0 until myData2.rowCount) {
                val headerString = myData2.rows[i].rowHeader
                val gridItemPresenterHeader = HeaderItem(i.toLong(), headerString)
                val mGridPresenter = createCardLayout1(lifecycleOwner)
                val gridRowAdapter = ArrayObjectAdapter(mGridPresenter)

                val layout = myData2.rows[i].rowLayout
                val s = myData2.rows[i].rowItems.size
                for (j in 0 until s) {
                    val customRowItem =
                        CustomRowItemX(myData2.rows[i].rowItems[j], layout, headerString)
                    gridRowAdapter.add(customRowItem)
                }

                if (headerString == "bannerAd") {
                    rowsAdapter.add(ListRow(gridRowAdapter))
                } else {
                    rowsAdapter.add(ListRow(gridItemPresenterHeader, gridRowAdapter))
                }
            }

            _rowsAdapter.postValue(rowsAdapter)
        }
    }

    fun createCardLayout1(lifecycleOwner: LifecycleOwner): CardLayout1 {
        return CardLayout1(lifecycleOwner, exoPlayerManager, this)
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
        lastInteractionTime = System.currentTimeMillis()

        if (rowIndex != currentPlayingRowIndex || itemIndex != currentPlayingItemIndex) {
            stopAndShrinkPreviousItem()
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

    private fun isAutoScrollableRow(rowIndex: Int): Boolean {
        val row = _rowsAdapter.value?.get(rowIndex) as? ListRow
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
            val rowAdapter = _rowsAdapter.value?.get(currentAutoScrollRowIndex) as? ListRow
            val itemAdapter = rowAdapter?.adapter as? ArrayObjectAdapter
            if (itemAdapter != null && itemAdapter.size() > 0) {
                currentAutoScrollItemIndex = (currentAutoScrollItemIndex + 1) % itemAdapter.size()
                val nextItem = itemAdapter.get(currentAutoScrollItemIndex) as? CustomRowItemX
                if (nextItem != null) {
                    Log.d(TAG, "Auto-scrolling to: rowIndex=$currentAutoScrollRowIndex, itemIndex=$currentAutoScrollItemIndex")
                    _autoScrollCommand.value = AutoScrollCommand(currentAutoScrollRowIndex, currentAutoScrollItemIndex)
                    _shrinkCardCommand.value = nextItem.rowItemX.tid

                    if (nextItem.rowItemX.videoUrl != null) {
                        scheduleVideoPlay(nextItem, currentAutoScrollRowIndex, currentAutoScrollItemIndex)
                    } else {
                        // For non-video tiles, wait 5 seconds before moving to the next item
                        delayJob = launch {
                            delay(AUTO_SCROLL_DELAY)
                            if (System.currentTimeMillis() - lastInteractionTime >= USER_IDLE_DELAY) {
                                scheduleNextAutoScrollItem()
                            } else {
                                scheduleAutoScrollResume(currentAutoScrollRowIndex, currentAutoScrollItemIndex)
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
            _videoPlaybackState.value = VideoPlaybackState.Playing(item.rowItemX.tid, videoUrl)
            currentlyPlayingVideoTileId = item.rowItemX.tid
            _playVideoCommand.value = PlayVideoCommand(videoUrl, item.rowItemX.tid) { cardView, tileId ->
                viewModelScope.launch {
                    try {
                        cardView.setTileId(tileId)
                        isVideoPlaying = true
                        exoPlayerManager.playVideo(videoUrl, cardView, tileId)
                        // Pause auto-scroll while video is playing
                        pauseAutoScroll()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error playing video: ${e.message}")
                        handleVideoEnded(tileId)
                    }
                }
            }
        }
    }

    fun stopVideoPlayback() {
        exoPlayerManager.releasePlayer()
        _videoPlaybackState.value = VideoPlaybackState.Stopped
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

    private fun handleVideoEnded(tileId: String) {
        _videoPlaybackState.value = VideoPlaybackState.Stopped
        _shrinkCardCommand.value = tileId
        isVideoPlaying = false
        currentlyPlayingVideoTileId = null

        if (isCurrentRowAutoScrollable) {
            // For auto-scrollable rows, wait 5 seconds before resuming auto-scroll
            delayJob = viewModelScope.launch {
                delay(AUTO_SCROLL_DELAY)
                scheduleAutoScrollResume(currentPlayingRowIndex, currentPlayingItemIndex)
            }
        }
        // For non-scrollable rows, do nothing after video ends
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
    val tileId: String,
    val playAction: (NewVideoCardView, String) -> Unit
)

data class AutoScrollCommand(val rowIndex: Int, val itemIndex: Int)