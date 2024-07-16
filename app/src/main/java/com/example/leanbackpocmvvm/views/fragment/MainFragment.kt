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
import com.example.leanbackpocmvvm.utils.ExoPlayerManager
import com.example.leanbackpocmvvm.utils.NetworkChangeReceiver
import com.example.leanbackpocmvvm.utils.isConnected
import com.example.leanbackpocmvvm.views.customview.NewVideoCardView
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
        setupEventListeners()
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.rowsAdapter.observe(viewLifecycleOwner) { rowsAdapter ->
            mRowsAdapter = rowsAdapter
            adapter = mRowsAdapter
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
            val cardToShrink = view?.findViewWithTag<NewVideoCardView>(tileId)
            cardToShrink?.let {
                it.shrinkCard()
                it.showThumbnail()
            }
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
        exoPlayerManager.releasePlayer()
    }

    private fun setUI() {
        title = "CloudTV"
        headersState = HEADERS_DISABLED
    }

    private fun setupEventListeners() {
        setOnItemViewSelectedListener { itemViewHolder, item, rowViewHolder, row ->
            when (item) {
                is MainViewModel.CustomRowItemX -> {
                    val cardView = itemViewHolder?.view as? NewVideoCardView
                    if (cardView != null) {
                        cardView.setExoPlayerManager(exoPlayerManager)
                        val rowIndex = mRowsAdapter.indexOf(row)
                        viewModel.onItemFocused(item, rowIndex)
                    }
                }

                else -> {
                    viewModel.stopAutoScroll()
                    viewModel.stopVideoPlayback()
                }
            }
        }

        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is MainViewModel.CustomRowItemX) {
                viewModel.onItemClicked(item)
            }
        }

        setOnSearchClickedListener {
            viewModel.onSearchClicked()
        }
    }

    private fun scrollToItem(rowIndex: Int, itemIndex: Int) {
        view?.post {
            Log.d(TAG, "Scrolling to item: rowIndex=$rowIndex, itemIndex=$itemIndex")
            val verticalGridView =
                view?.findViewById<VerticalGridView>(androidx.leanback.R.id.container_list)
            val rowView = verticalGridView?.getChildAt(rowIndex) as? ViewGroup
            val horizontalGridView =
                rowView?.findViewById<HorizontalGridView>(androidx.leanback.R.id.row_content)
            horizontalGridView?.smoothScrollToPosition(itemIndex)

            horizontalGridView?.post {
                val viewHolder = horizontalGridView.findViewHolderForAdapterPosition(itemIndex)
                val itemView = viewHolder?.itemView as? NewVideoCardView
                itemView?.let {
                    Log.d(
                        TAG,
                        "Requesting focus for item: rowIndex=$rowIndex, itemIndex=$itemIndex"
                    )
                    it.requestFocus()
                } ?: Log.e(
                    TAG,
                    "Failed to find item view for rowIndex=$rowIndex, itemIndex=$itemIndex"
                )
            }
        }
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

    companion object {
        private const val TAG = "MainFragment"
    }
}