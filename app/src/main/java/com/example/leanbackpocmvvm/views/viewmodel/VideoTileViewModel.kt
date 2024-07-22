package com.example.leanbackpocmvvm.views.viewmodel

import android.util.Log
import androidx.lifecycle.*
import androidx.leanback.widget.*
import com.example.leanbackpocmvvm.models.MyData2
import com.example.leanbackpocmvvm.models.VideoTileItem
import com.example.leanbackpocmvvm.repository.MainRepository
import com.example.leanbackpocmvvm.utils.VideoExoPlayerManager
import com.example.leanbackpocmvvm.views.customview.VideoCardView
import com.example.leanbackpocmvvm.views.fragment.VideoTileFragment
import com.example.leanbackpocmvvm.views.presenter.VideoTilePresenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import javax.inject.Inject

@HiltViewModel
class VideoTileViewModel @Inject constructor(
    private val repository: MainRepository,
    private val videoExoPlayerManager: VideoExoPlayerManager
) : ViewModel() {

    private val _rowsAdapter = MutableLiveData<ArrayObjectAdapter>()
    val rowsAdapter: LiveData<ArrayObjectAdapter> = _rowsAdapter

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    private var fragment: VideoTileFragment? = null

    fun setFragment(fragment: VideoTileFragment) {
        this.fragment = fragment
    }

    fun loadData(lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val myData2 = repository.getMyData()
                createRowsAdapter(myData2, lifecycleOwner)
            } catch (e: Exception) {
                _toastMessage.value = "Error loading data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun createRowsAdapter(myData2: MyData2, lifecycleOwner: LifecycleOwner) {
        withContext(Dispatchers.Default) {
            val listRowPresenter = ListRowPresenter()
            val rowsAdapter = ArrayObjectAdapter(listRowPresenter)

            myData2.rows.forEachIndexed { index, row ->
                val videoTilePresenter = VideoTilePresenter(fragment!!)
                val listRowAdapter = ArrayObjectAdapter(videoTilePresenter)

                row.rowItems.forEach { item ->
                    listRowAdapter.add(
                        VideoTileItem(
                            tid = item.tid,
                            title = item.title,
                            poster = item.poster,
                            videoUrl = item.videoUrl ?: ""
                        )
                    )
                }

                if (listRowAdapter.size() > 0) {
                    val header = HeaderItem(index.toLong(), row.rowHeader)
                    val listRow = ListRow(header, listRowAdapter)
                    rowsAdapter.add(listRow)
                }
            }

            _rowsAdapter.postValue(rowsAdapter)
        }
    }

    fun onItemFocused(item: VideoTileItem, cardView: VideoCardView) {
        viewModelScope.launch {
            item.videoUrl?.let { url ->
                Log.d(TAG, "Focusing on item: ${item.title}, URL: $url")
                cardView.showVideo() // Show video before playing
                videoExoPlayerManager.playVideo(url, cardView, item.tid)

                // Preload adjacent videos
                preloadAdjacentVideos(item)
            } ?: run {
                cardView.showThumbnail()
            }
        }
    }

    fun onItemUnfocused(item: VideoTileItem, cardView: VideoCardView) {
        viewModelScope.launch {
            videoExoPlayerManager.stopPlayback()
        }
    }

    fun onItemClicked(item: VideoTileItem) {
        // Handle item click
        Log.d(TAG, "Item clicked: ${item.title}")
        // You can add more functionality here, like opening a detail view
    }

    fun stopVideo() {
        videoExoPlayerManager.stopPlayback()
    }

    private fun preloadAdjacentVideos(currentItem: VideoTileItem) {
        val currentRow = findRowContainingItem(currentItem)
        currentRow?.let { row ->
            val currentIndex = row.indexOf(currentItem)

            // Preload next video
            if (currentIndex < row.size - 1) {
                (row[currentIndex + 1] as? VideoTileItem)?.videoUrl?.let { nextUrl ->
                    videoExoPlayerManager.preloadVideo(nextUrl)
                }
            }

            // Preload previous video
            if (currentIndex > 0) {
                (row[currentIndex - 1] as? VideoTileItem)?.videoUrl?.let { prevUrl ->
                    videoExoPlayerManager.preloadVideo(prevUrl)
                }
            }
        }
    }

    private fun findRowContainingItem(item: VideoTileItem): List<Any>? {
        return rowsAdapter.value?.let { adapter ->
            for (i in 0 until adapter.size()) {
                val row = adapter.get(i) as? ListRow
                row?.adapter?.let { rowAdapter ->
                    for (j in 0 until rowAdapter.size()) {
                        if (rowAdapter.get(j) == item) {
                            return@let (0 until rowAdapter.size()).map { rowAdapter.get(it) }
                        }
                    }
                }
            }
            null
        }
    }

    fun getUpcomingItems(currentItem: VideoTileItem, count: Int): List<VideoTileItem> {
        val allItems = getAllItems()
        val currentIndex = allItems.indexOf(currentItem)
        return allItems.subList(currentIndex + 1, minOf(currentIndex + 1 + count, allItems.size))
    }

    private fun getAllItems(): List<VideoTileItem> {
        val allItems = mutableListOf<VideoTileItem>()
        rowsAdapter.value?.let { adapter ->
            for (i in 0 until adapter.size()) {
                val row = adapter.get(i) as? ListRow
                row?.adapter?.let { rowAdapter ->
                    for (j in 0 until rowAdapter.size()) {
                        (rowAdapter.get(j) as? VideoTileItem)?.let { allItems.add(it) }
                    }
                }
            }
        }
        return allItems
    }

    override fun onCleared() {
        super.onCleared()
        videoExoPlayerManager.releasePlayer()
    }

    companion object {
        private const val TAG = "VideoTileViewModel"
    }
}