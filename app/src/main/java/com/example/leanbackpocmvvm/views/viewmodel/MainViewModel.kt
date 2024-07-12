package com.example.leanbackpocmvvm.views.viewmodel

import android.util.Log
import androidx.lifecycle.*
import androidx.leanback.widget.*
import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.models.MyData2
import com.example.leanbackpocmvvm.models.RowItemX
import com.example.leanbackpocmvvm.repository.MainRepository
import com.example.leanbackpocmvvm.utils.ExoPlayerManager
import com.example.leanbackpocmvvm.utils.ExoPlayerManager.Companion.TAG
import com.example.leanbackpocmvvm.views.customview.NewVideoCardView
import com.example.leanbackpocmvvm.views.presenter.CardLayout1
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    fun loadData() {
        coroutineScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            try {
                val myData2 = repository.getMyData()
                createRowsAdapter(myData2)
            } catch (e: Exception) {
                _toastMessage.postValue("Error loading data: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    private suspend fun createRowsAdapter(myData2: MyData2) {
        withContext(Dispatchers.Default) {
            val lrp = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE, false)
            lrp.shadowEnabled = false
            lrp.selectEffectEnabled = false
            val rowsAdapter = ArrayObjectAdapter(lrp)

            for (i in 0 until myData2.rowCount) {
                val headerString = myData2.rows[i].rowHeader
                val gridItemPresenterHeader = HeaderItem(i.toLong(), headerString)
                val mGridPresenter = CardLayout1()
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

    fun setNetworkStatus(isConnected: Boolean) {
        if (isConnected) {
            loadData()
            _toastMessage.value = "Network connected"
        } else {
            _toastMessage.value = "Network disconnected"
        }
        _networkStatus.value = isConnected
    }

    fun onItemSelected(item: CustomRowItemX) {
        item.rowItemX.videoUrl?.let { videoUrl ->
            Log.d(TAG, "MainViewModel: Selected item with video URL: $videoUrl")
            _videoPlaybackState.value = VideoPlaybackState.Playing(item.rowItemX.tid, videoUrl)

            _playVideoCommand.value = PlayVideoCommand(videoUrl) { cardView ->
                exoPlayerManager.playVideo(videoUrl, cardView) {
                    _videoPlaybackState.value = VideoPlaybackState.Stopped
                }
            }
        }
    }

    // Not in Use but may required later
    fun setupVideoPlayback(item: CustomRowItemX, cardView: NewVideoCardView) {
        item.rowItemX.videoUrl?.let { videoUrl ->
            cardView.setupForVideoPlayback(exoPlayerManager, videoUrl) {
                _videoPlaybackState.value = VideoPlaybackState.Stopped
            }
            _videoPlaybackState.value = VideoPlaybackState.Playing(item.rowItemX.tid, videoUrl)
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

data class PlayVideoCommand(
    val videoUrl: String,
    val playAction: (NewVideoCardView) -> Unit
)