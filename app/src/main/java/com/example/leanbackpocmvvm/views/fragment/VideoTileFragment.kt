package com.example.leanbackpocmvvm.views.fragment

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.viewModels
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import com.example.leanbackpocmvvm.models.VideoTileItem
import com.example.leanbackpocmvvm.utils.VideoExoPlayerManager
import com.example.leanbackpocmvvm.views.customview.VideoCardView
import com.example.leanbackpocmvvm.views.viewmodel.VideoTileViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VideoTileFragment : BrowseSupportFragment() {

    private val viewModel: VideoTileViewModel by viewModels()

    @Inject
    lateinit var videoExoPlayerManager: VideoExoPlayerManager

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private val cardViewMap = mutableMapOf<String, VideoCardView>()
    private var lastFocusedItem: VideoTileItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.setFragment(this)
        setupAdapter()
        observeViewModel()
        setupEventListeners()
        viewModel.loadData(viewLifecycleOwner)
    }

    private fun setupUI() {
        title = "Video Tiles"
        headersState = HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = false
    }

    private fun setupAdapter() {
        val listRowPresenter = ListRowPresenter()
        rowsAdapter = ArrayObjectAdapter(listRowPresenter)
        adapter = rowsAdapter
    }

    private fun observeViewModel() {
        viewModel.rowsAdapter.observe(viewLifecycleOwner) { newRowsAdapter ->
            rowsAdapter.clear()
            for (i in 0 until newRowsAdapter.size()) {
                val row = newRowsAdapter.get(i) as? ListRow
                if (row != null) {
                    rowsAdapter.add(row)
                }
            }
            Log.d(TAG, "Rows added: ${newRowsAdapter.size()}")
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Show or hide loading indicator
            Log.d(TAG, "Loading state: $isLoading")
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            // Show toast message
            Log.d(TAG, "Toast message: $message")
        }
    }

    private fun setupEventListeners() {
        setOnItemViewSelectedListener { _, item, _, _ ->
            if (item is VideoTileItem) {
                Log.d(TAG, "Item selected: ${item.title}")
                lastFocusedItem?.let {
                    onItemUnfocused(it)
                }
                onItemFocused(item)
                lastFocusedItem = item

                // Preload videos for items about to come on screen
                preloadUpcomingVideos(item)
            }
        }

        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is VideoTileItem) {
                Log.d(TAG, "Item clicked: ${item.title}")
                viewModel.onItemClicked(item)
            }
        }
    }

    fun onItemFocused(item: VideoTileItem) {
        Log.d(TAG, "Item focused: ${item.title}")
        val cardView = getCardView(item.tid)
        if (cardView != null) {
            viewModel.onItemFocused(item, cardView)
        } else {
            Log.e(TAG, "CardView not found for tileId: ${item.tid}")
        }
    }

    fun onItemUnfocused(item: VideoTileItem) {
        Log.d(TAG, "Item unfocused: ${item.title}")
        val cardView = getCardView(item.tid)
        if (cardView != null) {
            viewModel.onItemUnfocused(item, cardView)
        } else {
            Log.e(TAG, "CardView not found for tileId: ${item.tid}")
        }
    }

    private fun preloadUpcomingVideos(currentItem: VideoTileItem) {
        viewModel.getUpcomingItems(currentItem, 5).forEach { upcomingItem ->
            upcomingItem.videoUrl?.let { url ->
                videoExoPlayerManager.preloadVideo(url)
            }
        }
    }

    fun registerCardView(tileId: String, cardView: VideoCardView) {
        cardViewMap[tileId] = cardView
        Log.d(TAG, "CardView registered for tileId: $tileId")
    }

    private fun getCardView(tileId: String): VideoCardView? {
        return cardViewMap[tileId]
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopVideo()
        Log.d(TAG, "onDestroyView called")
    }

    companion object {
        private const val TAG = "VideoTileFragment"
    }
}