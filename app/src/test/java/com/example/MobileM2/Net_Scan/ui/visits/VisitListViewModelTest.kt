package com.example.MobileM2.Net_Scan.ui.visits

import com.example.MobileM2.Net_Scan.domain.MappingMode
import com.example.MobileM2.Net_Scan.domain.Visit
import com.example.MobileM2.Net_Scan.domain.VisitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
class VisitListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun startsEmptyAndDisplaysSavedValues() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = VisitListViewModel(repository)
        advanceUntilIdle()
        assertEquals(VisitListState.Empty, viewModel.state.value)

        viewModel.createVisit("  Boutique  ", " Client ", " Adresse ")
        advanceUntilIdle()

        val saved = (viewModel.state.value as VisitListState.Success).visits.single()
        assertEquals("Boutique", saved.building)
        assertEquals("Client", saved.client)
        assertEquals("Adresse", saved.address)
        assertEquals(CreationState.Saved, viewModel.creationState.value)
    }

    @Test fun theChosenMappingModeIsSaved() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = VisitListViewModel(repository)
        advanceUntilIdle()
        viewModel.createVisit("Entrepôt", "", "", MappingMode.MANUAL)
        advanceUntilIdle()
        assertEquals(MappingMode.MANUAL, (viewModel.state.value as VisitListState.Success).visits.single().mode)
    }

    @Test fun rejectsBlankBuildingWithoutSaving() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = VisitListViewModel(repository)
        viewModel.createVisit("   ", "", "")
        advanceUntilIdle()
        assertEquals(CreationState.InvalidBuilding, viewModel.creationState.value)
        assertTrue(repository.visits.value.isEmpty())
    }

    @Test fun saveFailureIsVisibleAndDoesNotLoseInput() = runTest(dispatcher) {
        val repository = FakeRepository().apply { failSave = true }
        val viewModel = VisitListViewModel(repository)
        viewModel.createVisit("Magasin", "", "")
        advanceUntilIdle()
        assertEquals(CreationState.Error, viewModel.creationState.value)
        assertTrue(repository.visits.value.isEmpty())
    }

    @Test fun loadFailureCanBeRetried() = runTest(dispatcher) {
        val repository = FakeRepository().apply { failLoad = true }
        val viewModel = VisitListViewModel(repository)
        advanceUntilIdle()
        assertEquals(VisitListState.Error, viewModel.state.value)
        repository.failLoad = false
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(VisitListState.Empty, viewModel.state.value)
    }

    private class FakeRepository : VisitRepository {
        val visits = MutableStateFlow<List<Visit>>(emptyList())
        var failLoad = false
        var failSave = false

        override fun observeVisits(): Flow<List<Visit>> =
            if (failLoad) flow { throw IllegalStateException("test") } else visits

        override fun observeVisit(id: Long): Flow<Visit?> = visits.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun createVisit(building: String, client: String, address: String, mode: MappingMode) {
            if (failSave) throw IllegalStateException("test")
            visits.value = listOf(Visit(1, building, client, address, 1, mode))
        }
    }
}
