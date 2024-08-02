package com.example.leanbackpocmvvm.views.fragment

import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        setupScrollListener()
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
                            Toast.makeText(context, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        viewModel.playVideoCommand.observe(viewLifecycleOwner) { command ->
            val cardView = view?.findViewWithTag<NewVideoCardView>(command.tileId)
            cardView?.let { prepareVideoPlayback(it, command.videoUrl, command.tileId) }
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

    private fun prepareVideoPlayback(cardView: NewVideoCardView, videoUrl: String, tileId: String) {
        // Stop any currently playing video
        stopVideoPlayback()

        // Set the new current playing card
        currentPlayingCard = cardView

        // Remove PlayerView from its current parent
        (sharedPlayerView.parent as? ViewGroup)?.removeView(sharedPlayerView)

        cardView.prepareForVideoPlayback()
        cardView.videoPlaceholder.addView(sharedPlayerView)

        exoPlayerManager.prepareVideo(videoUrl, sharedPlayerView,
            onReady = { isReady ->
                if (isReady) {
                    cardView.startVideoPlayback()
                }
            },
            onEnded = {
                viewModel.onVideoEnded(tileId)
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
            selectEffectEnabled = false
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
            Log.d(TAG, "Scrolling to item: rowIndex=$rowIndex, itemIndex=$itemIndex")
            val verticalGridView = view?.findViewById<VerticalGridView>(androidx.leanback.R.id.container_list)
            verticalGridView?.smoothScrollToPosition(rowIndex)

            verticalGridView?.postDelayed({
                val rowView = findRowView(verticalGridView, rowIndex)
                val horizontalGridView = rowView?.findViewById<HorizontalGridView>(androidx.leanback.R.id.row_content)
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

    private fun setupScrollListener() {
        val verticalGridView = view?.findViewById<VerticalGridView>(androidx.leanback.R.id.container_list)
        verticalGridView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val visibleItemCount = verticalGridView.childCount
                Log.d(TAG, "visibleItemCount $visibleItemCount")

                preloadVisibleItems()
            }
        })
    }

    private fun preloadVisibleItems() {
        val verticalGridView = view?.findViewById<VerticalGridView>(androidx.leanback.R.id.container_list)
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
                val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.wifi_connected_white_filled)
                wifiLogo.setImageDrawable(drawable)
            } else {
                offlineText.visibility = View.VISIBLE
                val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.wifi_disconnected_white)
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
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
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