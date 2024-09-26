package com.example.leanbackpocmvvm.views.fragment

import android.content.IntentFilter
import android.graphics.Rect
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.leanbackpocmvvm.R
import com.example.leanbackpocmvvm.models.MyData2
import com.example.leanbackpocmvvm.utils.NetworkChangeReceiver
import com.example.leanbackpocmvvm.utils.isConnected
import com.example.leanbackpocmvvm.views.activity.MainActivity
import com.example.leanbackpocmvvm.views.customview.NewVideoCardView
import com.example.leanbackpocmvvm.views.exoplayer.ExoPlayerManager
import com.example.leanbackpocmvvm.views.presenter.CardLayout1
import com.example.leanbackpocmvvm.views.viewmodel.CustomRowItemX
import com.example.leanbackpocmvvm.views.viewmodel.MainViewModel
import com.example.leanbackpocmvvm.views.viewmodel.PlayVideoCommand
import com.example.leanbackpocmvvm.views.viewmodel.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MainFragment : BrowseSupportFragment(), isConnected {

    private val viewModel: MainViewModel by viewModels()
    private var networkChangeReceiver: NetworkChangeReceiver? = null
    private lateinit var rowsAdapter: ArrayObjectAdapter

    @Inject
    lateinit var exoPlayerManager: ExoPlayerManager
    private var isFragmentDestroyed = false

    private lateinit var sharedPlayerView: PlayerView
    private var currentPlayingCard: NewVideoCardView? = null
    private var previousVisibleCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        networkChangeReceiver = NetworkChangeReceiver(this)
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        requireActivity().registerReceiver(networkChangeReceiver, filter)
        setUI()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        sharedPlayerView = PlayerView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            useController = false
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        setupBackPressHandler()
        view.post { setupScrollListener(view) }
    }

    private fun setUI() {
        title = "CloudTV"
        headersState = HEADERS_DISABLED
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Loading -> showProgressBar()
                        is UiState.Success -> {
                            hideProgressBar()
                            populateRows(state.data)
                            viewModel.mRowsAdapter = rowsAdapter
                            setupEventListeners()
                        }

                        is UiState.Error -> {
                            hideProgressBar()
                            Toast.makeText(context, "Error: ${state.message}", Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }
            }
        }

        viewModel.playVideoCommand.observe(viewLifecycleOwner) { command ->
            val cardView = view?.findViewWithTag<NewVideoCardView>(command.tileId)
            cardView?.let { prepareVideoPlayback(it, command) }
            // Log the current set of played video tile IDs
            Log.d(TAG, "Current played video tile IDs: ${viewModel.playedVideoTileIds.joinToString()}")
        }

        viewModel.autoScrollCommand.observe(viewLifecycleOwner) { command ->
            scrollToItem(command.rowIndex, command.itemIndex)
        }

        viewModel.shrinkCardCommand.observe(viewLifecycleOwner) { tileId ->
            val cardToUpdate = view?.findViewWithTag<NewVideoCardView>(tileId)
            cardToUpdate?.showThumbnail()
            cardToUpdate?.shrinkCard()
        }

        viewModel.resetCardCommand.observe(viewLifecycleOwner) { tileId ->
            val cardToReset = view?.findViewWithTag<NewVideoCardView>(tileId)
            cardToReset?.resetCardState()
        }

        viewModel.networkStatus.observe(viewLifecycleOwner) { isConnected ->
            updateNetworkUI(isConnected)
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        viewModel.preloadVideoCommand.observe(viewLifecycleOwner) { command ->
            exoPlayerManager.preloadVideo(command.videoUrl)
        }
    }

    private fun prepareVideoPlayback(cardView: NewVideoCardView, command: PlayVideoCommand) {
        stopVideoPlayback()
        currentPlayingCard = cardView
        (sharedPlayerView.parent as? ViewGroup)?.removeView(sharedPlayerView)

        cardView.prepareForVideoPlayback()
        cardView.videoPlaceholder.addView(sharedPlayerView)

        exoPlayerManager.prepareVideo(
            videoUrl = command.videoUrl,
            adsVideoUrl = command.adsVideoUrl,
            tileType = command.tileType,
            playerView = sharedPlayerView,
            onReady = { isReady ->
                if (isReady) {
                    cardView.startVideoPlayback(command.tileType == "typeAdsVideo")
                }
            },
            onEnded = {
                viewModel.onVideoEnded(command.tileId)
            }
        )
    }

    private fun setupEventListeners() {
        setOnItemViewSelectedListener { itemViewHolder, item, rowViewHolder, row ->
            when (item) {
                is CustomRowItemX -> {
                    val cardView = itemViewHolder?.view as? NewVideoCardView
                    if (cardView != null) {
                        val rowIndex = rowsAdapter.indexOf(row)
                        val itemIndex = findItemIndex(row as? ListRow, item)
                        Log.d(TAG, "OnUserFocus")
                        viewModel.onUserFocus(item)
                        viewModel.onItemFocused(item, rowIndex, itemIndex)
                    }
                }
                else -> {
                    viewModel.stopAutoScroll()
                    stopVideoPlayback()
                }
            }
        }

        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is CustomRowItemX) {
                viewModel.onItemClicked(item)
            }
        }

        setOnSearchClickedListener {
            viewModel.onSearchClicked()
        }
    }

    private fun populateRows(data: MyData2) {
        val lrp = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE, false).apply {
            shadowEnabled = false
            enableChildRoundedCorners(true)
            selectEffectEnabled = true
        }
        rowsAdapter = ArrayObjectAdapter(lrp)
        val presenter = createCardLayout(viewLifecycleOwner)

        data.rows.forEachIndexed { index, row ->
            val headerItem = HeaderItem(index.toLong(), row.rowHeader)
            val listRowAdapter = ArrayObjectAdapter(presenter)

            row.rowItems.forEach { item ->
                val customRowItem = CustomRowItemX(item, row.rowLayout, row.rowHeader)
                listRowAdapter.add(customRowItem)
            }

            if (row.rowHeader == "bannerAd") {
                rowsAdapter.add(ListRow(listRowAdapter))
            } else {
                rowsAdapter.add(ListRow(headerItem, listRowAdapter))
            }
        }
        adapter = rowsAdapter
    }

    private fun createCardLayout(lifecycleOwner: LifecycleOwner): CardLayout1 {
        return CardLayout1(lifecycleOwner, exoPlayerManager, viewModel)
    }

    private fun findItemIndex(listRow: ListRow?, item: CustomRowItemX): Int {
        if (listRow == null) return -1
        val adapter = listRow.adapter as? ArrayObjectAdapter ?: return -1
        return (0 until adapter.size()).firstOrNull { adapter.get(it) == item } ?: -1
    }

    private fun scrollToItem(rowIndex: Int, itemIndex: Int) {
        view?.post {
            val verticalGridView =
                view?.findViewById<VerticalGridView>(androidx.leanback.R.id.container_list)
            verticalGridView?.smoothScrollToPosition(rowIndex)

            verticalGridView?.postDelayed({
                val rowView = findRowView(verticalGridView, rowIndex)
                val horizontalGridView =
                    rowView?.findViewById<HorizontalGridView>(androidx.leanback.R.id.row_content)
                horizontalGridView?.smoothScrollToPosition(itemIndex)

                horizontalGridView?.postDelayed({
                    focusOnItem(horizontalGridView, itemIndex)
                }, 200)
            }, 200)
        }
    }

    private fun findRowView(verticalGridView: VerticalGridView?, rowIndex: Int): ViewGroup? {
        if (verticalGridView == null) return null
        for (i in 0 until verticalGridView.childCount) {
            val child = verticalGridView.getChildAt(i)
            if (verticalGridView.getChildAdapterPosition(child) == rowIndex) {
                return child as? ViewGroup
            }
        }
        return null
    }

    private fun focusOnItem(horizontalGridView: HorizontalGridView?, itemIndex: Int) {
        val viewHolder = horizontalGridView?.findViewHolderForAdapterPosition(itemIndex)
        val itemView = viewHolder?.itemView as? NewVideoCardView
        itemView?.requestFocus()
    }

    // scroll listener
    private fun setupScrollListener(fragmentView: View) {
        fragmentView.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                fragmentView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val verticalGridView =
                    fragmentView.findViewById<VerticalGridView>(androidx.leanback.R.id.container_list)
                if (verticalGridView == null) {
                    Log.e(TAG, "VerticalGridView not found")
                    return
                }

                Log.d(TAG, "VerticalGridView found, observing changes")

                observeVerticalGridView(verticalGridView)
            }
        })
    }

    private fun observeVerticalGridView(verticalGridView: VerticalGridView) {
        verticalGridView.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val layoutManager = verticalGridView.layoutManager
                if (layoutManager != null) {
                    verticalGridView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    Log.d(TAG, "LayoutManager initialized, setting up scroll listener")
                    setupVerticalGridViewScrollListener(verticalGridView)
                    forceLayoutAndUpdateCount(verticalGridView)
                }
            }
        })
    }

    private fun setupVerticalGridViewScrollListener(verticalGridView: VerticalGridView) {
        Log.d(TAG, "Setting up scroll listener")

        verticalGridView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        Log.d(TAG, "Scroll State: IDLE")
                        updateVisibleItemsCount(verticalGridView)
                    }

                    RecyclerView.SCROLL_STATE_DRAGGING -> Log.d(TAG, "Scroll State: DRAGGING")
                    RecyclerView.SCROLL_STATE_SETTLING -> Log.d(TAG, "Scroll State: SETTLING")
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy != 0) {
                    updateVisibleItemsCount(verticalGridView)
                }
            }
        })

        // This method ensures we get an initial count as soon as possible without an arbitrary delay,
        // and then updates the count during scrolling. It's more efficient and avoids potential
        // redundancy while still ensuring we have an accurate count from the start.
        verticalGridView.postDelayed({
            updateVisibleItemsCount(verticalGridView)
        }, 500) // 500ms delay
    }

    // This method retrieves fully visible items and partially visible items separately.
    private fun updateVisibleItemsCount(verticalGridView: VerticalGridView) {
        val layoutManager = verticalGridView.layoutManager
        if (layoutManager == null) {
            Log.e(TAG, "LayoutManager is null, retrying...")
            verticalGridView.post { updateVisibleItemsCount(verticalGridView) }
            return
        }

        Log.d(TAG, "LayoutManager type: ${layoutManager.javaClass.simpleName}")

        val itemCount = verticalGridView.adapter?.itemCount ?: 0
        Log.d(TAG, "Total row count: $itemCount")

        val visibleRowCount = verticalGridView.childCount
        Log.d(TAG, "Visible row count: $visibleRowCount")

        var totalFullyVisibleItemsCount = 0
        var totalPartiallyVisibleItemsCount = 0

        for (i in 0 until visibleRowCount) {
            val rowView = verticalGridView.getChildAt(i)
            Log.d(TAG, "Row $i view type: ${rowView.javaClass.simpleName}")

            // Find the HorizontalGridView within the row
            val horizontalGridView = findHorizontalGridView(rowView)

            if (horizontalGridView != null) {
                val rowAdapter = horizontalGridView.adapter
                val rowItemCount = rowAdapter?.itemCount ?: 0
                val rowVisibleItemCount = horizontalGridView.childCount

                var rowFullyVisibleItemsCount = 0
                var rowPartiallyVisibleItemsCount = 0

                // Count fully and partially visible items in this row
                for (j in 0 until rowVisibleItemCount) {
                    val itemView = horizontalGridView.getChildAt(j)
                    if (itemView != null) {
                        when {
                            isViewFullyVisible(horizontalGridView, itemView) -> {
                                rowFullyVisibleItemsCount++
                                val cardView = itemView as? NewVideoCardView
                                cardView?.customItem?.rowItemX?.tid?.let { tileId ->
                                    viewModel.addFullyVisibleTileId(tileId, cardView.customItem)
                                }
                            }
                            isViewPartiallyVisible(horizontalGridView, itemView) -> rowPartiallyVisibleItemsCount++
                        }
                    }
                    Log.d(
                        TAG,
                        "Row $i, Item $j - Fully visible: ${
                            isViewFullyVisible(
                                horizontalGridView,
                                itemView
                            )
                        }, Partially visible: ${
                            isViewPartiallyVisible(
                                horizontalGridView,
                                itemView
                            )
                        }"
                    )
                }

                totalFullyVisibleItemsCount += rowFullyVisibleItemsCount
                totalPartiallyVisibleItemsCount += rowPartiallyVisibleItemsCount

                Log.d(
                    TAG,
                    "Row $i - Total items: $rowItemCount, Visible items: $rowVisibleItemCount," +
                            " Fully visible items: $rowFullyVisibleItemsCount, Partially visible items:" +
                            " $rowPartiallyVisibleItemsCount"
                )
            } else {
                Log.d(TAG, "Row $i - HorizontalGridView not found")
            }
        }

        Log.d(TAG, "Total fully visible items across all rows: $totalFullyVisibleItemsCount")
        Log.d(
            TAG,
            "Total partially visible items across all rows: $totalPartiallyVisibleItemsCount"
        )
        Log.d(
            TAG,
            "Total visible items (fully + partially) across all rows: " +
                    "${totalFullyVisibleItemsCount + totalPartiallyVisibleItemsCount}"
        )

        // Log the height of the VerticalGridView
        Log.d(TAG, "VerticalGridView height: ${verticalGridView.height}")
    }


    // This method retrieves only fully visible items, not partially.
    /* private fun updateVisibleItemsCount(verticalGridView: VerticalGridView) {
         val layoutManager = verticalGridView.layoutManager
         if (layoutManager == null) {
             Log.e(TAG, "LayoutManager is null, retrying...")
             verticalGridView.post { updateVisibleItemsCount(verticalGridView) }
             return
         }

         Log.d(TAG, "LayoutManager type: ${layoutManager.javaClass.simpleName}")

         val itemCount = verticalGridView.adapter?.itemCount ?: 0
         Log.d(TAG, "Total row count: $itemCount")

         val visibleRowCount = verticalGridView.childCount
         Log.d(TAG, "Visible row count: $visibleRowCount")

         var fullyVisibleItemsCount = 0

         for (i in 0 until visibleRowCount) {
             val rowView = verticalGridView.getChildAt(i)
             Log.d(TAG, "Row $i view type: ${rowView.javaClass.simpleName}")

             // Find the HorizontalGridView within the row
             val horizontalGridView = findHorizontalGridView(rowView)

             if (horizontalGridView != null) {
                 val rowAdapter = horizontalGridView.adapter
                 val rowItemCount = rowAdapter?.itemCount ?: 0
                 val rowVisibleItemCount = horizontalGridView.childCount

                 var rowFullyVisibleItemsCount = 0
                 // Count fully visible items in this row
                 for (j in 0 until rowVisibleItemCount) {
                     val itemView = horizontalGridView.getChildAt(j)
                     if (itemView != null && isViewFullyVisible(horizontalGridView, itemView)) {
                         rowFullyVisibleItemsCount++
                     }
                 }

                 fullyVisibleItemsCount += rowFullyVisibleItemsCount

                 Log.d(
                     TAG,
                     "Row $i - Total items: $rowItemCount, Fully visible items: $rowFullyVisibleItemsCount"
                 )
             } else {
                 Log.d(TAG, "Row $i - HorizontalGridView not found")
             }
         }

         Log.d(TAG, "Fully visible items across all rows: $fullyVisibleItemsCount")

         // Log the height of the VerticalGridView
         Log.d(TAG, "VerticalGridView height: ${verticalGridView.height}")
     } */


    // This method retrieves all visible items, whether fully or partially.
    /* private fun updateVisibleItemsCount(verticalGridView: VerticalGridView) {
         val layoutManager = verticalGridView.layoutManager
         if (layoutManager == null) {
             Log.e(TAG, "LayoutManager is null, retrying...")
             verticalGridView.post { updateVisibleItemsCount(verticalGridView) }
             return
         }

         Log.d(TAG, "LayoutManager type: ${layoutManager.javaClass.simpleName}")

         val itemCount = verticalGridView.adapter?.itemCount ?: 0
         Log.d(TAG, "Total row count: $itemCount")

         val visibleRowCount = verticalGridView.childCount
         Log.d(TAG, "Visible row count: $visibleRowCount")

         var totalVisibleItemsCount = 0
         var fullyVisibleItemsCount = 0

         for (i in 0 until visibleRowCount) {
             val rowView = verticalGridView.getChildAt(i)
             Log.d(TAG, "Row $i view type: ${rowView.javaClass.simpleName}")

             // Find the HorizontalGridView within the row
             val horizontalGridView = findHorizontalGridView(rowView)

             if (horizontalGridView != null) {
                 val rowAdapter = horizontalGridView.adapter
                 val rowItemCount = rowAdapter?.itemCount ?: 0
                 val rowVisibleItemCount = horizontalGridView.childCount

                 totalVisibleItemsCount += rowVisibleItemCount

                 // Count fully visible items in this row
                 for (j in 0 until rowVisibleItemCount) {
                     val itemView = horizontalGridView.getChildAt(j)
                     if (itemView != null && isViewFullyVisible(horizontalGridView, itemView)) {
                         fullyVisibleItemsCount++
                     }
                 }

                 Log.d(TAG, "Row $i - Total items: $rowItemCount, Visible items: $rowVisibleItemCount")
             } else {
                 Log.d(TAG, "Row $i - HorizontalGridView not found")
             }
         }

         Log.d(TAG, "Total visible items across all rows: $totalVisibleItemsCount")
         Log.d(TAG, "Fully visible items across all rows: $fullyVisibleItemsCount")

         // Log the height of the VerticalGridView
         Log.d(TAG, "VerticalGridView height: ${verticalGridView.height}")
     } */

    private fun findHorizontalGridView(view: View): HorizontalGridView? {
        if (view is HorizontalGridView) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val result = findHorizontalGridView(child)
                if (result != null) {
                    return result
                }
            }
        }
        return null
    }

    /* This ensures that we only count an item as fully or partially visible if its entire area
       unction ensures that we only count items that are completely within the bounds of their parent
       HorizontalGridView. */

    /* private fun isViewFullyVisible(parent: ViewGroup, view: View): Boolean {
        val parentBounds = Rect()
        val viewBounds = Rect()

        parent.getGlobalVisibleRect(parentBounds)
        view.getGlobalVisibleRect(viewBounds)

        return viewBounds.left >= parentBounds.left &&
                viewBounds.right <= parentBounds.right &&
                viewBounds.top >= parentBounds.top &&
                viewBounds.bottom <= parentBounds.bottom
    } */

    /* This ensures that we only count an item as fully visible if its entire area is within the
     parent's bounds and no part of it is cut off. */
    private fun isViewFullyVisible(parent: ViewGroup, view: View): Boolean {
        val parentBounds = Rect()
        val viewBounds = Rect()

        parent.getGlobalVisibleRect(parentBounds)
        view.getGlobalVisibleRect(viewBounds)

        // Check if the view is completely within the parent's bounds
        return viewBounds.left >= parentBounds.left &&
                viewBounds.right <= parentBounds.right &&
                viewBounds.top >= parentBounds.top &&
                viewBounds.bottom <= parentBounds.bottom &&
                viewBounds.width() == view.width &&  // Ensure the full width is visible
                viewBounds.height() == view.height   // Ensure the full height is visible
    }

    private fun isViewPartiallyVisible(parent: ViewGroup, view: View): Boolean {
        val parentBounds = Rect()
        val viewBounds = Rect()

        parent.getGlobalVisibleRect(parentBounds)
        view.getGlobalVisibleRect(viewBounds)

        // Check if the view intersects with the parent's bounds but is not fully visible
        return Rect.intersects(parentBounds, viewBounds) && !isViewFullyVisible(parent, view)
    }

    private fun forceLayoutAndUpdateCount(verticalGridView: VerticalGridView) {
        verticalGridView.requestLayout()
        verticalGridView.post {
            Log.d(TAG, "Forced layout pass, updating count")
            updateVisibleItemsCount(verticalGridView)
        }
    }

    private fun preloadVisibleItems() {
        val verticalGridView =
            view?.findViewById<VerticalGridView>(androidx.leanback.R.id.container_list)
        if (verticalGridView == null) return

        val layoutManager = verticalGridView.layoutManager as? LinearLayoutManager ?: return
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()


        if (firstVisiblePosition == RecyclerView.NO_POSITION || lastVisiblePosition == RecyclerView.NO_POSITION) return

        for (i in firstVisiblePosition..lastVisiblePosition) {
            val row = rowsAdapter.get(i) as? ListRow ?: continue
            val adapter = row.adapter as? ArrayObjectAdapter ?: continue

            for (j in 0 until adapter.size()) {
                val item = adapter.get(j) as? CustomRowItemX ?: continue
                viewModel.preloadVideo(item)
            }
        }
    }

    private fun stopVideoPlayback() {
        exoPlayerManager.releasePlayer()
        (sharedPlayerView.parent as? ViewGroup)?.removeView(sharedPlayerView)
        currentPlayingCard?.endVideoPlayback()
        currentPlayingCard = null
    }

    override fun connected() {
        viewModel.setNetworkStatus(true)
        viewModel.loadData()
    }

    override fun notconnected() {
        viewModel.setNetworkStatus(false)
    }

    private fun updateNetworkUI(isConnected: Boolean) {
        if (isFragmentDestroyed) return

        (requireActivity() as? MainActivity)?.safelyUseGlide {
            val offlineText = requireActivity().findViewById<ImageView>(R.id.offline_text)
            val wifiLogo = requireActivity().findViewById<ImageView>(R.id.wifilogo)

            if (isConnected) {
                offlineText.visibility = View.GONE
                val drawable = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.wifi_connected_white_filled
                )
                wifiLogo.setImageDrawable(drawable)
            } else {
                offlineText.visibility = View.VISIBLE
                val drawable =
                    ContextCompat.getDrawable(requireContext(), R.drawable.wifi_disconnected_white)
                wifiLogo.setImageDrawable(drawable)
            }
        }
    }

    private fun showProgressBar() {
        requireActivity().findViewById<ProgressBar>(R.id.progress_bar).visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        requireActivity().findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    cleanupAndExit()
                }
            })
    }

    private fun cleanupAndExit() {
        viewModel.stopAutoScroll()
        viewModel.stopVideoPlayback()
        exoPlayerManager.releasePlayer()
        unregisterNetworkReceiver()

        // Use Handler to post the finish call to ensure all cleanup is done
        Handler(Looper.getMainLooper()).post {
            requireActivity().finishAndRemoveTask()
        }
    }

    override fun onPause() {
        super.onPause()
        stopVideoPlayback()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.DESTROYED) {
                viewLifecycleOwner.lifecycleScope.coroutineContext.cancelChildren()
            }
        }

        viewModel.networkStatus.removeObservers(viewLifecycleOwner)
        viewModel.toastMessage.removeObservers(viewLifecycleOwner)
        viewModel.playVideoCommand.removeObservers(viewLifecycleOwner)
        viewModel.autoScrollCommand.removeObservers(viewLifecycleOwner)
        viewModel.shrinkCardCommand.removeObservers(viewLifecycleOwner)
        viewModel.resetCardCommand.removeObservers(viewLifecycleOwner)
        viewModel.preloadVideoCommand.removeObservers(viewLifecycleOwner)

        rowsAdapter = ArrayObjectAdapter()

        view?.let {
            Glide.with(this).clear(it)
        }

        (view as? ViewGroup)?.removeAllViews()

        Log.d(TAG, "onDestroyView called")
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopAutoScroll()
        stopVideoPlayback()
        exoPlayerManager.onLifecycleDestroy()

        adapter = null
        unregisterNetworkReceiver()

        Log.d(TAG, "onDestroy called")
    }

    private fun unregisterNetworkReceiver() {
        networkChangeReceiver?.let { receiver ->
            try {
                requireActivity().unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "NetworkChangeReceiver not registered or already unregistered", e)
            }
            networkChangeReceiver = null
        }
    }

    companion object {
        private const val TAG = "MainFragment"
    }
}