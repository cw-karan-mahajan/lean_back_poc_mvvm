package com.example.leanbackpocmvvm.views.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.example.leanbackpocmvvm.core.Resource
import com.example.leanbackpocmvvm.models.MyData2
import com.example.leanbackpocmvvm.repository.*
import tv.cloudwalker.adtech.vastdata.parser.VastAdSequenceManager
import tv.cloudwalker.adtech.vastdata.tracking.VastTrackingManager
import tv.cloudwalker.adtech.player.ExoPlayerManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<MainRepository>(relaxed = true)
    private val apiRepository1 = mockk<MainRepository1>(relaxed = true)
    private val adRepository = mockk<AdRepository>(relaxed = true)
    private val exoPlayerManager = mockk<ExoPlayerManager>(relaxed = true)
    private val vastRepository = mockk<VastRepository>(relaxed = true)
    private val vastAdSequenceManager = mockk<VastAdSequenceManager>(relaxed = true)
    private val vastTrackingManager = mockk<VastTrackingManager>(relaxed = true)
    private val toastObserver = mockk<Observer<String>>(relaxed = true)

    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        viewModel = MainViewModel(
            repository = repository,
            apiRepository1 = apiRepository1,
            adRepository = adRepository,
            exoPlayerManager = exoPlayerManager,
            vastRepository = vastRepository,
            vastAdSequenceManager = vastAdSequenceManager,
            vastTrackingManager = vastTrackingManager
        )
        viewModel.toastMessage.observeForever(toastObserver)
    }

    @After
    fun tearDown() {
        viewModel.toastMessage.removeObserver(toastObserver)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `fetchData with success response emits Success state`() = runTest {
        // Given
        val mockData = MyData2(rowCount = 1, rows = emptyList())
        coEvery { apiRepository1.fetchList() } returns flowOf(Resource.Success(mockData))

        // When
        viewModel.fetchData()
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assert(state is UiState.Success)
        assertEquals(mockData, (state as UiState.Success).data)
    }

    @Test
    fun `fetchData with error response emits Error state`() = runTest {
        // Given
        val errorMessage = "Network error"
        coEvery { apiRepository1.fetchList() } returns flowOf(Resource.Error(errorMessage))

        // When
        viewModel.fetchData()
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assert(state is UiState.Error)
        assertEquals(errorMessage, (state as UiState.Error).message)
    }

    @Test
    fun `loadData success emits Success state`() = runTest {
        // Given
        val mockData = MyData2(rowCount = 1, rows = emptyList())
        coEvery { repository.getMyData() } returns mockData

        // When
        viewModel.loadData()
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assert(state is UiState.Success)
        assertEquals(mockData, (state as UiState.Success).data)
    }

    @Test
    fun `loadData with network error shows toast message`() = runTest {
        // Given
        coEvery { repository.getMyData() } throws Exception("Network error")
        // When
        viewModel.loadData()
        advanceUntilIdle()

        // Verify UI state is Error
        val state = viewModel.uiState.value
        assert(state is UiState.Error)

        // Verify toast message was set
        verify { toastObserver.onChanged(match { it.contains("Error loading data") }) }
    }
}

class MainDispatcherRule : TestRule {
    private val testDispatcher = StandardTestDispatcher()

    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            Dispatchers.setMain(testDispatcher)
            try {
                base.evaluate()
            } finally {
                Dispatchers.resetMain()
            }
        }
    }
}