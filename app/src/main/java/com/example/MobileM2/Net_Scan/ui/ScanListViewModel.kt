package com.example.MobileM2.Net_Scan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.MobileM2.Net_Scan.domain.Scan
import com.example.MobileM2.Net_Scan.domain.ScanRepository
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

sealed interface ScanListState {
    data object Loading : ScanListState
    data object Empty : ScanListState
    data object Error : ScanListState
    data class Success(val scans: List<Scan>) : ScanListState
}

sealed interface CreationState {
    data object Idle : CreationState
    data object InvalidBuilding : CreationState
    data object Saving : CreationState
    data object Saved : CreationState
    data object Error : CreationState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScanListViewModel(private val repository: ScanRepository) : ViewModel() {
    private val logger = Logger.getLogger(ScanListViewModel::class.java.name)
    private val attempts = MutableStateFlow(0)
    private val mutableCreationState = MutableStateFlow<CreationState>(CreationState.Idle)
    val creationState = mutableCreationState.asStateFlow()

    val state = attempts.flatMapLatest {
        flow { emitAll(repository.observeScans()) }
            .map<List<Scan>, ScanListState> { scans ->
                if (scans.isEmpty()) ScanListState.Empty else ScanListState.Success(scans)
            }
            .onStart { emit(ScanListState.Loading) }
            .catch { error ->
                logger.log(Level.WARNING, "Unable to load scans", error)
                emit(ScanListState.Error)
            }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ScanListState.Loading)

    fun retry() { attempts.value++ }

    fun resetCreation() { mutableCreationState.value = CreationState.Idle }

    fun createScan(building: String, client: String, address: String) {
        if (mutableCreationState.value == CreationState.Saving) return
        val name = building.trim()
        if (name.isEmpty()) {
            mutableCreationState.value = CreationState.InvalidBuilding
            return
        }
        mutableCreationState.value = CreationState.Saving
        viewModelScope.launch {
            try {
                repository.createScan(name, client.trim(), address.trim())
                mutableCreationState.value = CreationState.Saved
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.log(Level.WARNING, "Unable to save scan", error)
                mutableCreationState.value = CreationState.Error
            }
        }
    }
}
