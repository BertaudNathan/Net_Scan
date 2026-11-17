package com.example.MobileM2.Net_Scan.ui

import com.example.MobileM2.Net_Scan.domain.Scan
import com.example.MobileM2.Net_Scan.domain.ScanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun startsEmptyAndDisplaysSavedValues() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = ScanListViewModel(repository)
        advanceUntilIdle()
        assertEquals(ScanListState.Empty, viewModel.state.value)

        viewModel.createScan("  Boutique  ", " Client ", " Adresse ")
        advanceUntilIdle()

        val saved = (viewModel.state.value as ScanListState.Success).scans.single()
        assertEquals("Boutique", saved.building)
        assertEquals("Client", saved.client)
        assertEquals("Adresse", saved.address)
        assertEquals(CreationState.Saved, viewModel.creationState.value)
    }

    @Test fun rejectsBlankBuildingWithoutSaving() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = ScanListViewModel(repository)
        viewModel.createScan("   ", "", "")
        advanceUntilIdle()
        assertEquals(CreationState.InvalidBuilding, viewModel.creationState.value)
        assertTrue(repository.scans.value.isEmpty())
    }

    @Test fun saveFailureIsVisibleAndDoesNotLoseInput() = runTest(dispatcher) {
        val repository = FakeRepository().apply { failSave = true }
        val viewModel = ScanListViewModel(repository)
        viewModel.createScan("Magasin", "", "")
        advanceUntilIdle()
        assertEquals(CreationState.Error, viewModel.creationState.value)
        assertTrue(repository.scans.value.isEmpty())
    }

    @Test fun loadFailureCanBeRetried() = runTest(dispatcher) {
        val repository = FakeRepository().apply { failLoad = true }
        val viewModel = ScanListViewModel(repository)
        advanceUntilIdle()
        assertEquals(ScanListState.Error, viewModel.state.value)
        repository.failLoad = false
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(ScanListState.Empty, viewModel.state.value)
    }

    private class FakeRepository : ScanRepository {
        val scans = MutableStateFlow<List<Scan>>(emptyList())
        var failLoad = false
        var failSave = false

        override fun observeScans(): Flow<List<Scan>> =
            if (failLoad) flow { throw IllegalStateException("test") } else scans

        override suspend fun createScan(building: String, client: String, address: String) {
            if (failSave) throw IllegalStateException("test")
            scans.value = listOf(Scan(1, building, client, address, 1))
        }
    }
}
