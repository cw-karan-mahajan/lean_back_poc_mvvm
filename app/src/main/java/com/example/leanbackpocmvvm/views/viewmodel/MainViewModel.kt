package com.example.leanbackpocmvvm.views.viewmodel

import android.util.Log
import androidx.lifecycle.*
import androidx.leanback.widget.*
import androidx.media3.common.util.UnstableApi
import com.example.leanbackpocmvvm.core.Resource
import com.example.leanbackpocmvvm.models.MyData2
import com.example.leanbackpocmvvm.models.RowItemX
import com.example.leanbackpocmvvm.repository.MainRepository
import com.example.leanbackpocmvvm.repository.MainRepository1
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MainRepository,
    private val apiRepository1: MainRepository1
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _networkStatus = MutableLiveData<Boolean>()
    val networkStatus: LiveData<Boolean> = _networkStatus

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            //_isLoading.postValue(true)
            try {
                val myData2 = repository.getMyData()
                _uiState.value = UiState.Success(myData2)
            } catch (e: Exception) {
                _toastMessage.postValue("Error loading data: ${e.message}")
            } finally {
                //_isLoading.postValue(false)
            }
        }
    }

    fun fetchData() {
        viewModelScope.launch {
            apiRepository1.fetchList().distinctUntilChanged().collect { response ->
                when (response) {
                    is Resource.Success -> {
                        _uiState.value = UiState.Success(response.data)
                        //createRowsAdapter(response.data, lifecycleOwner)
                    }
                    is Resource.Error -> {
                        _uiState.value = UiState.Error(response.message)
                        _toastMessage.postValue("Error loading data: ${response.message}")
                    }
                }
            }
        }
    }

    fun setNetworkStatus(isConnected: Boolean) {
        if (isConnected) {
            _toastMessage.value = "Network connected"
        } else {
            _toastMessage.value = "Network disconnected"
        }
        _networkStatus.value = isConnected
    }

    fun onItemClicked(item: CustomRowItemX) {
        // Handle item click
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
    }

    companion object {
        const val TAG = "MainViewModel"
    }
}

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

//sealed class VideoPlaybackState {
//    data class Playing(val itemId: String, val videoUrl: String) : VideoPlaybackState()
//    object Stopped : VideoPlaybackState()
//}

sealed class UiState {
    object Loading : UiState()
    data class Success(val data: MyData2) : UiState()
    data class Error(val message: String) : UiState()
}

data class ContentData(
    val imageUrl: String,
    val width: Int,
    val height: Int,
    val isLandscape: Boolean,
    val isPortrait: Boolean
)

//@OptIn(UnstableApi::class)
//data class PlayVideoCommand(
//    val videoUrl: String,
//    val tileId: String,
//    val playAction: (NewVideoCardView, String) -> Unit
//)

//data class AutoScrollCommand(val rowIndex: Int, val itemIndex: Int)