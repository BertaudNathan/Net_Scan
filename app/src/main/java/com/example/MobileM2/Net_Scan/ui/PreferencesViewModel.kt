package com.example.MobileM2.Net_Scan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.MobileM2.Net_Scan.domain.ThemeMode
import com.example.MobileM2.Net_Scan.domain.Handedness
import com.example.MobileM2.Net_Scan.domain.PreferencesRepository
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PreferencesState {
    data object Loading : PreferencesState
    data object Error : PreferencesState
    data class Ready(
        val theme: ThemeMode,
        val saving: Boolean = false,
        val saveError: Boolean = false,
        val handedness: Handedness = Handedness.RIGHT,
    ) : PreferencesState
}

class PreferencesViewModel(private val repository: PreferencesRepository) : ViewModel() {
    private val logger = Logger.getLogger(PreferencesViewModel::class.java.name)
    private val mutableState = MutableStateFlow<PreferencesState>(PreferencesState.Loading)
    val state = mutableState.asStateFlow()
    private var observation: Job? = null

    init { retry() }

    fun retry() {
        observation?.cancel()
        mutableState.value = PreferencesState.Loading
        observation = viewModelScope.launch {
            try {
                repository.observePreferences().collect { preferences ->
                    val previous = mutableState.value as? PreferencesState.Ready
                    mutableState.value = PreferencesState.Ready(
                        theme = preferences.theme,
                        saving = previous?.saving ?: false,
                        saveError = previous?.saveError ?: false,
                        handedness = preferences.handedness,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.log(Level.WARNING, "Unable to load preferences", error)
                mutableState.value = PreferencesState.Error
            }
        }
    }

    fun setTheme(theme: ThemeMode) {
        val previous = mutableState.value as? PreferencesState.Ready ?: return
        if (previous.saving || previous.theme == theme) return
        save({ repository.setTheme(theme) }) { it.copy(theme = theme) }
    }

    fun setHandedness(handedness: Handedness) {
        val previous = mutableState.value as? PreferencesState.Ready ?: return
        if (previous.saving || previous.handedness == handedness) return
        save({ repository.setHandedness(handedness) }) { it.copy(handedness = handedness) }
    }

    private fun save(write: suspend () -> Unit, update: (PreferencesState.Ready) -> PreferencesState.Ready) {
        val previous = mutableState.value as? PreferencesState.Ready ?: return
        mutableState.value = previous.copy(saving = true, saveError = false)
        viewModelScope.launch {
            try {
                write()
                val current = mutableState.value
                if (current is PreferencesState.Ready) {
                    mutableState.value = update(current).copy(saving = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.log(Level.WARNING, "Unable to save preference", error)
                val current = mutableState.value
                if (current is PreferencesState.Ready) {
                    mutableState.value = current.copy(saving = false, saveError = true)
                }
            }
        }
    }
}
