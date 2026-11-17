package com.example.MobileM2.Net_Scan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.MobileM2.Net_Scan.domain.ThemeMode
import com.example.MobileM2.Net_Scan.domain.ThemeRepository
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
    ) : PreferencesState
}

class PreferencesViewModel(private val repository: ThemeRepository) : ViewModel() {
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
                repository.observeTheme().collect { theme ->
                    val previous = mutableState.value as? PreferencesState.Ready
                    mutableState.value = PreferencesState.Ready(
                        theme = theme,
                        saving = previous?.saving ?: false,
                        saveError = previous?.saveError ?: false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.log(Level.WARNING, "Unable to load theme preference", error)
                mutableState.value = PreferencesState.Error
            }
        }
    }

    fun setTheme(theme: ThemeMode) {
        val previous = mutableState.value as? PreferencesState.Ready ?: return
        if (previous.saving || previous.theme == theme) return
        mutableState.value = previous.copy(saving = true, saveError = false)
        viewModelScope.launch {
            try {
                repository.setTheme(theme)
                val current = mutableState.value
                if (current is PreferencesState.Ready) {
                    mutableState.value = current.copy(theme = theme, saving = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.log(Level.WARNING, "Unable to save theme preference", error)
                val current = mutableState.value
                if (current is PreferencesState.Ready) {
                    mutableState.value = current.copy(saving = false, saveError = true)
                }
            }
        }
    }
}
