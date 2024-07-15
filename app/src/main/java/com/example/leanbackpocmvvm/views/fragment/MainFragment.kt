package com.example.leanbackpocmvvm.views.fragment

import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ListRow
import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.R
import com.example.leanbackpocmvvm.utils.ExoPlayerManager
import com.example.leanbackpocmvvm.utils.ExoPlayerManager.Companion.TAG
import com.example.leanbackpocmvvm.utils.NetworkChangeReceiver
import com.example.leanbackpocmvvm.utils.isConnected
import com.example.leanbackpocmvvm.views.customview.NewVideoCardView
import com.example.leanbackpocmvvm.views.viewmodel.MainViewModel
import com.example.leanbackpocmvvm.views.viewmodel.VideoPlaybackState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MainFragment : BrowseSupportFragment(), isConnected {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var mRowsAdapter: ArrayObjectAdapter
    private val networkChangeReceiver = NetworkChangeReceiver(this)

    @Inject
    lateinit var exoPlayerManager: ExoPlayerManager

    @Inject
    lateinit var coroutineScope: CoroutineScope

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
        //viewModel.loadData(viewLifecycleOwner)
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
            val cardView = view?.findViewWithTag<NewVideoCardView>(command.videoUrl)
            cardView?.let { command.playAction(it) }
        }
    }

    private fun updateSelectedItemVideoState(state: VideoPlaybackState) {
        val adapter = adapter as? ArrayObjectAdapter
        adapter?.let { arrayAdapter ->
            for (i in 0 until arrayAdapter.size()) {
                val row = arrayAdapter.get(i) as? ListRow
                row?.let {
                    val rowAdapter = it.adapter as? ArrayObjectAdapter
                    rowAdapter?.let { itemAdapter ->
                        for (j in 0 until itemAdapter.size()) {
                            val item = itemAdapter.get(j) as? MainViewModel.CustomRowItemX
                            item?.let { customItem ->
                                updateCardViewState(customItem, state)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateCardViewState(item: MainViewModel.CustomRowItemX, state: VideoPlaybackState) {
        val cardView = view?.findViewWithTag<NewVideoCardView>(item.rowItemX.tid)
        cardView?.updateVideoPlaybackState(state)
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
            when {
                item is MainViewModel.CustomRowItemX && itemViewHolder?.view is NewVideoCardView -> {
                    val cardView = itemViewHolder.view as NewVideoCardView
                    Log.d(TAG, "MainFragment: Item selected, calling onItemSelected")
                    cardView.setExoPlayerManager(exoPlayerManager)
                    viewModel.onItemSelected(item)
                }
                itemViewHolder?.view is NewVideoCardView -> {
                    (itemViewHolder.view as NewVideoCardView).shrinkCard()
                }
                else -> {
                    Log.d("MainFragment", "Unhandled item type: ${item?.javaClass?.simpleName}")
                }
            }
            // Stop and release player when moving to any new tile
            viewModel.stopVideoPlayback()
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
            val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.wifi_connected_white_filled)
            wifiLogo.setImageDrawable(drawable)
        } else {
            offlineText.visibility = View.VISIBLE
            val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.wifi_disconnected_white)
            wifiLogo.setImageDrawable(drawable)
        }
    }

    private fun showProgressBar() {
        requireActivity().findViewById<ProgressBar>(R.id.progress_bar).visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        requireActivity().findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
    }
}