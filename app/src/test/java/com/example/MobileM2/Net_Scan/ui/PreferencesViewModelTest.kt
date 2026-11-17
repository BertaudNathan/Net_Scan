package com.example.MobileM2.Net_Scan.ui

import com.example.MobileM2.Net_Scan.domain.ThemeMode
import com.example.MobileM2.Net_Scan.domain.AppPreferences
import com.example.MobileM2.Net_Scan.domain.Handedness
import com.example.MobileM2.Net_Scan.domain.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun savedPreferenceIsLoadedAndChangesArePersisted() = runTest(dispatcher) {
        val repository = FakePreferencesRepository(ThemeMode.DARK)
        val viewModel = PreferencesViewModel(repository)
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, (viewModel.state.value as PreferencesState.Ready).theme)
        viewModel.setTheme(ThemeMode.LIGHT)
        advanceUntilIdle()
        assertEquals(ThemeMode.LIGHT, repository.preferences.value.theme)
        assertEquals(ThemeMode.LIGHT, (viewModel.state.value as PreferencesState.Ready).theme)
    }

    @Test fun failedSaveKeepsPreviousThemeAndReportsError() = runTest(dispatcher) {
        val repository = FakePreferencesRepository(ThemeMode.SYSTEM)
        val viewModel = PreferencesViewModel(repository)
        advanceUntilIdle()
        repository.failWrite = true
        viewModel.setTheme(ThemeMode.DARK)
        advanceUntilIdle()
        assertEquals(ThemeMode.SYSTEM, repository.preferences.value.theme)
        assertEquals(PreferencesState.Ready(ThemeMode.SYSTEM, saveError = true), viewModel.state.value)
    }

    @Test fun handednessIsLoadedAndSavedWithoutChangingTheme() = runTest(dispatcher) {
        val repository = FakePreferencesRepository(ThemeMode.DARK)
        val viewModel = PreferencesViewModel(repository)
        advanceUntilIdle()
        assertEquals(Handedness.RIGHT, (viewModel.state.value as PreferencesState.Ready).handedness)
        viewModel.setHandedness(Handedness.LEFT)
        advanceUntilIdle()
        assertEquals(AppPreferences(ThemeMode.DARK, Handedness.LEFT), repository.preferences.value)
        assertEquals(Handedness.LEFT, (viewModel.state.value as PreferencesState.Ready).handedness)
        viewModel.setTheme(ThemeMode.LIGHT)
        advanceUntilIdle()
        assertEquals(AppPreferences(ThemeMode.LIGHT, Handedness.LEFT), repository.preferences.value)
    }

    @Test fun failedHandednessSaveKeepsPreviousPreference() = runTest(dispatcher) {
        val repository = FakePreferencesRepository(ThemeMode.DARK)
        val viewModel = PreferencesViewModel(repository)
        advanceUntilIdle()
        repository.failWrite = true
        viewModel.setHandedness(Handedness.LEFT)
        advanceUntilIdle()
        assertEquals(AppPreferences(ThemeMode.DARK, Handedness.RIGHT), repository.preferences.value)
        assertEquals(PreferencesState.Ready(ThemeMode.DARK, saveError = true), viewModel.state.value)
    }

    private class FakePreferencesRepository(initial: ThemeMode) : PreferencesRepository {
        val preferences = MutableStateFlow(AppPreferences(initial, Handedness.RIGHT))
        var failWrite = false
        override fun observePreferences(): Flow<AppPreferences> = preferences
        override suspend fun setTheme(theme: ThemeMode) {
            if (failWrite) throw IllegalStateException("test failure")
            preferences.value = preferences.value.copy(theme = theme)
        }
        override suspend fun setHandedness(handedness: Handedness) {
            if (failWrite) throw IllegalStateException("test failure")
            preferences.value = preferences.value.copy(handedness = handedness)
        }
    }
}
