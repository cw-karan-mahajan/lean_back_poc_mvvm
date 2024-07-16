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

    private var autoScrollJob: Job? = null
    private var currentAutoScrollRowIndex = -1
    private var currentAutoScrollItemIndex = -1
    private val AUTO_SCROLL_DELAY = 5000L // 5 seconds
    private val VIDEO_START_DELAY = 5000L // 5 seconds delay before playing video

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

    fun onItemFocused(item: CustomRowItemX, rowIndex: Int) {
        if (isAutoScrollableRow(rowIndex)) {
            startAutoScroll(rowIndex)
        } else {
            stopAutoScroll()
        }
    }

    private fun isAutoScrollableRow(rowIndex: Int): Boolean {
        val row = _rowsAdapter.value?.get(rowIndex) as? ListRow
        val firstItem = row?.adapter?.get(0) as? CustomRowItemX
        return firstItem?.layout == "landscape" && firstItem?.rowHeader == "bannerAd"
    }

    private fun startAutoScroll(rowIndex: Int) {
        if (currentAutoScrollRowIndex != rowIndex) {
            stopAutoScroll()
            currentAutoScrollRowIndex = rowIndex
            currentAutoScrollItemIndex = -1
            scrollToNextItem()
        }
    }

    fun stopAutoScroll() {
        autoScrollJob?.cancel()
        currentAutoScrollRowIndex = -1
        currentAutoScrollItemIndex = -1
    }

    private fun scrollToNextItem() {
        if (currentAutoScrollRowIndex == -1) return

        val rowAdapter = _rowsAdapter.value?.get(currentAutoScrollRowIndex) as? ListRow
        val itemAdapter = rowAdapter?.adapter as? ArrayObjectAdapter
        if (itemAdapter != null && itemAdapter.size() > 0) {
            currentAutoScrollItemIndex = (currentAutoScrollItemIndex + 1) % itemAdapter.size()
            val nextItem = itemAdapter.get(currentAutoScrollItemIndex) as? CustomRowItemX
            if (nextItem != null) {
                Log.d(TAG, "Emitting AutoScrollCommand: rowIndex=$currentAutoScrollRowIndex, itemIndex=$currentAutoScrollItemIndex")
                _autoScrollCommand.value = AutoScrollCommand(currentAutoScrollRowIndex, currentAutoScrollItemIndex)

                // Always show thumbnail first
                _shrinkCardCommand.value = nextItem.rowItemX.tid

                viewModelScope.launch {
                    delay(AUTO_SCROLL_DELAY)
                    if (nextItem.rowItemX.videoUrl != null) {
                        playVideo(nextItem)
                    } else {
                        scheduleNextAutoScroll()
                    }
                }
            }
        }
    }

    private fun scheduleNextAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = viewModelScope.launch {
            delay(AUTO_SCROLL_DELAY)
            scrollToNextItem()
        }
    }

    private fun playVideo(item: CustomRowItemX) {
        item.rowItemX.videoUrl?.let { videoUrl ->
            _videoPlaybackState.value = VideoPlaybackState.Playing(item.rowItemX.tid, videoUrl)
            _playVideoCommand.value = PlayVideoCommand(videoUrl, item.rowItemX.tid) { cardView, tileId ->
                viewModelScope.launch {
                    try {
                        cardView.setTileId(tileId)
                        exoPlayerManager.playVideo(videoUrl, cardView, tileId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error playing video: ${e.message}")
                        _videoPlaybackState.value = VideoPlaybackState.Stopped
                        _toastMessage.value = "Failed to play video. Please try again."
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
        scheduleNextAutoScroll()
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayerManager.releasePlayer()
        stopAutoScroll()
    }

    companion object {
        const val TAG = "MainViewModel"
    }
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