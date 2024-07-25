package com.example.leanbackpocmvvm.views.fragment

import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.R
import com.example.leanbackpocmvvm.views.exoplayer.ExoPlayerManager
import com.example.leanbackpocmvvm.utils.NetworkChangeReceiver
import com.example.leanbackpocmvvm.utils.isConnected
import com.example.leanbackpocmvvm.views.customview.NewVideoCardView
import com.example.leanbackpocmvvm.views.viewmodel.CustomRowItemX
import com.example.leanbackpocmvvm.views.viewmodel.MainViewModel
import com.example.leanbackpocmvvm.views.viewmodel.VideoPlaybackState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MainFragment : BrowseSupportFragment(), isConnected {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var mRowsAdapter: ArrayObjectAdapter
    private val networkChangeReceiver = NetworkChangeReceiver(this)

    @Inject
    lateinit var exoPlayerManager: ExoPlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        requireActivity().registerReceiver(networkChangeReceiver, filter)
        setUI()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.rowsAdapter.observe(viewLifecycleOwner) { rowsAdapter ->
            mRowsAdapter = rowsAdapter
            adapter = mRowsAdapter
            setupEventListeners()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) showProgressBar() else hideProgressBar()
        }

        viewModel.networkStatus.observe(viewLifecycleOwner) { isConnected ->
            updateNetworkUI(isConnected)
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        viewModel.videoPlaybackState.observe(viewLifecycleOwner) { state ->
            updateSelectedItemVideoState(state)
        }

        viewModel.playVideoCommand.observe(viewLifecycleOwner) { command ->
            val cardView = view?.findViewWithTag<NewVideoCardView>(command.tileId)
            cardView?.let { command.playAction(it, command.tileId) }
        }

        viewModel.autoScrollCommand.observe(viewLifecycleOwner) { command ->
            Log.d(
                TAG,
                "Received AutoScrollCommand: rowIndex=${command.rowIndex}, itemIndex=${command.itemIndex}"
            )
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
    }

    private fun updateSelectedItemVideoState(state: VideoPlaybackState) {
        when (state) {
            is VideoPlaybackState.Playing -> {
                val cardView = view?.findViewWithTag<NewVideoCardView>(state.itemId)
                //cardView?.startVideoPlayback()
            }

            is VideoPlaybackState.Stopped -> {
                // Handle stopped state if needed
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        requireActivity().unregisterReceiver(networkChangeReceiver)
        viewModel.stopVideoPlayback()
        exoPlayerManager.onLifecycleDestroy()
    }

    private fun setUI() {
        title = "CloudTV"
        headersState = HEADERS_DISABLED
    }

    private fun setupEventListeners() {
        setOnItemViewSelectedListener { itemViewHolder, item, rowViewHolder, row ->
            when (item) {
                is CustomRowItemX -> {
                    val cardView = itemViewHolder?.view as? NewVideoCardView
                    if (cardView != null) {
                        cardView.setExoPlayerManager(exoPlayerManager)
                        val rowIndex = mRowsAdapter.indexOf(row)
                        val itemIndex = findItemIndex(row as? ListRow, item)
                        viewModel.onItemFocused(item, rowIndex, itemIndex)
                    }
                }

                else -> {
                    viewModel.stopAutoScroll()
                    viewModel.stopVideoPlayback()
                }
            }
        }

        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is CustomRowItemX -> {
                    viewModel.onItemClicked(item)
                }
            }
        }
        setOnSearchClickedListener {
            viewModel.onSearchClicked()
        }
        // Add scroll listener to the main RecyclerView
    }

    private fun findItemIndex(listRow: ListRow?, item: CustomRowItemX): Int {
        if (listRow == null) return -1
        val adapter = listRow.adapter as? ArrayObjectAdapter ?: return -1
        for (i in 0 until adapter.size()) {
            if (adapter.get(i) == item) {
                return i
            }
        }
        return -1
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
                }, 200) // Delay to ensure horizontal scroll has completed
            }, 200) // Delay to ensure vertical scroll has completed
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
        itemView?.let {
            Log.d(TAG, "Requesting focus for item at index $itemIndex")
            it.requestFocus()
        } ?: Log.e(TAG, "Failed to find item view at index $itemIndex")
    }

    override fun connected() {
        viewModel.setNetworkStatus(true)
        viewModel.loadData(viewLifecycleOwner)
    }

    override fun notconnected() {
        viewModel.setNetworkStatus(false)
    }

    private fun updateNetworkUI(isConnected: Boolean) {
        val offlineText = requireActivity().findViewById<ImageView>(R.id.offline_text)
        val wifiLogo = requireActivity().findViewById<ImageView>(R.id.wifilogo)

        if (isConnected) {
            offlineText.visibility = View.GONE
            val drawable =
                ContextCompat.getDrawable(requireContext(), R.drawable.wifi_connected_white_filled)
            wifiLogo.setImageDrawable(drawable)
        } else {
            offlineText.visibility = View.VISIBLE
            val drawable =
                ContextCompat.getDrawable(requireContext(), R.drawable.wifi_disconnected_white)
            wifiLogo.setImageDrawable(drawable)
        }
    }

    private fun showProgressBar() {
        requireActivity().findViewById<ProgressBar>(R.id.progress_bar).visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        requireActivity().findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        exoPlayerManager.releasePlayer()
    }

    companion object {
        private const val TAG = "MainFragment"
    }
}