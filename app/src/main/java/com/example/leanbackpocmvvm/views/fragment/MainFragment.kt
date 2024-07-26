package com.example.leanbackpocmvvm.views.fragment

import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.R
import com.example.leanbackpocmvvm.models.MyData2
import com.example.leanbackpocmvvm.utils.NetworkChangeReceiver
import com.example.leanbackpocmvvm.utils.isConnected
import com.example.leanbackpocmvvm.views.presenter.ContentItemPresenter
import com.example.leanbackpocmvvm.views.viewmodel.CustomRowItemX
import com.example.leanbackpocmvvm.views.viewmodel.MainViewModel
import com.example.leanbackpocmvvm.views.viewmodel.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MainFragment : BrowseSupportFragment(), isConnected {

    private val viewModel: MainViewModel by viewModels()
    private val networkChangeReceiver = NetworkChangeReceiver(this)
    private lateinit var rowsAdapter: ArrayObjectAdapter

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
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            // Show loading indicator
                            showProgressBar()
                        }
                        is UiState.Success -> {
                            hideProgressBar()
                            populateRows(state.data)
                        }
                        is UiState.Error -> {
                            hideProgressBar()
                            // Show error message
                        }
                        else -> {}
                    }
                }
            }
        }

        viewModel.networkStatus.observe(viewLifecycleOwner) { isConnected ->
            updateNetworkUI(isConnected)
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        requireActivity().unregisterReceiver(networkChangeReceiver)
    }

    private fun setUI() {
        title = "CloudTV"
        headersState = HEADERS_DISABLED
    }

    private fun setupEventListeners() {
        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is CustomRowItemX -> {
                    viewModel.onItemClicked(item)
                }
            }
        }
    }

    private fun populateRows(data: MyData2) {
        var lrp = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE, false)
        lrp.shadowEnabled = false
        lrp.selectEffectEnabled = false
        rowsAdapter = ArrayObjectAdapter(lrp)

        data.rows.forEachIndexed { index, row ->
            val headerItem = HeaderItem(index.toLong(), row.rowHeader)
            val listRowAdapter = ArrayObjectAdapter(ContentItemPresenter())

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

//    private fun findItemIndex(listRow: ListRow?, item: CustomRowItemX): Int {
//        if (listRow == null) return -1
//        val adapter = listRow.adapter as? ArrayObjectAdapter ?: return -1
//        for (i in 0 until adapter.size()) {
//            if (adapter.get(i) == item) {
//                return i
//            }
//        }
//        return -1
//    }

//    private fun scrollToItem(rowIndex: Int, itemIndex: Int) {
//        view?.post {
//            Log.d(TAG, "Scrolling to item: rowIndex=$rowIndex, itemIndex=$itemIndex")
//            val verticalGridView = view?.findViewById<VerticalGridView>(androidx.leanback.R.id.container_list)
//            verticalGridView?.smoothScrollToPosition(rowIndex)
//
//            verticalGridView?.postDelayed({
//                val rowView = findRowView(verticalGridView, rowIndex)
//                val horizontalGridView = rowView?.findViewById<HorizontalGridView>(androidx.leanback.R.id.row_content)
//                horizontalGridView?.smoothScrollToPosition(itemIndex)
//
//                horizontalGridView?.postDelayed({
//                    focusOnItem(horizontalGridView, itemIndex)
//                }, 200) // Delay to ensure horizontal scroll has completed
//            }, 200) // Delay to ensure vertical scroll has completed
//        }
//    }

//    private fun findRowView(verticalGridView: VerticalGridView?, rowIndex: Int): ViewGroup? {
//        if (verticalGridView == null) return null
//        for (i in 0 until verticalGridView.childCount) {
//            val child = verticalGridView.getChildAt(i)
//            if (verticalGridView.getChildAdapterPosition(child) == rowIndex) {
//                return child as? ViewGroup
//            }
//        }
//        return null
//    }



    override fun connected() {
        viewModel.setNetworkStatus(true)
        viewModel.fetchData()
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
        viewModel.networkStatus.removeObservers(viewLifecycleOwner)
        viewModel.toastMessage.removeObservers(viewLifecycleOwner)
    }

    companion object {
        private const val TAG = "MainFragment"
    }
}