package com.example.MobileM2.Net_Scan.ui.visits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.MobileM2.Net_Scan.domain.Visit
import com.example.MobileM2.Net_Scan.domain.VisitRepository
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VisitListState {
    data object Loading : VisitListState
    data object Empty : VisitListState
    data object Error : VisitListState
    data class Success(val visits: List<Visit>) : VisitListState
}

sealed interface CreationState {
    data object Idle : CreationState
    data object InvalidBuilding : CreationState
    data object Saving : CreationState
    data object Saved : CreationState
    data object Error : CreationState
}

@OptIn(ExperimentalCoroutinesApi::class)
class VisitListViewModel(private val repository: VisitRepository) : ViewModel() {
    private val logger = Logger.getLogger(VisitListViewModel::class.java.name)
    private val attempts = MutableStateFlow(0)
    private val mutableCreationState = MutableStateFlow<CreationState>(CreationState.Idle)
    val creationState = mutableCreationState.asStateFlow()

    val state = attempts.flatMapLatest {
        flow { emitAll(repository.observeVisits()) }
            .map<List<Visit>, VisitListState> { visits ->
                if (visits.isEmpty()) VisitListState.Empty else VisitListState.Success(visits)
            }
            .onStart { emit(VisitListState.Loading) }
            .catch { error ->
                logger.log(Level.WARNING, "Unable to load visits", error)
                emit(VisitListState.Error)
            }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VisitListState.Loading)

    fun retry() { attempts.value++ }

    fun resetCreation() { mutableCreationState.value = CreationState.Idle }

    fun createVisit(building: String, client: String, address: String) {
        if (mutableCreationState.value == CreationState.Saving) return
        val name = building.trim()
        if (name.isEmpty()) {
            mutableCreationState.value = CreationState.InvalidBuilding
            return
        }
        mutableCreationState.value = CreationState.Saving
        viewModelScope.launch {
            try {
                repository.createVisit(name, client.trim(), address.trim())
                mutableCreationState.value = CreationState.Saved
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.log(Level.WARNING, "Unable to save visit", error)
                mutableCreationState.value = CreationState.Error
            }
        }
    }
}
