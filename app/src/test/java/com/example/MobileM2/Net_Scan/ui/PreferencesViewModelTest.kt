package com.example.MobileM2.Net_Scan.ui

import com.example.MobileM2.Net_Scan.domain.ThemeMode
import com.example.MobileM2.Net_Scan.domain.ThemeRepository
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
        val repository = FakeThemeRepository(ThemeMode.DARK)
        val viewModel = PreferencesViewModel(repository)
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, (viewModel.state.value as PreferencesState.Ready).theme)
        viewModel.setTheme(ThemeMode.LIGHT)
        advanceUntilIdle()
        assertEquals(ThemeMode.LIGHT, repository.theme.value)
        assertEquals(ThemeMode.LIGHT, (viewModel.state.value as PreferencesState.Ready).theme)
    }

    @Test fun failedSaveKeepsPreviousThemeAndReportsError() = runTest(dispatcher) {
        val repository = FakeThemeRepository(ThemeMode.SYSTEM)
        val viewModel = PreferencesViewModel(repository)
        advanceUntilIdle()
        repository.failWrite = true
        viewModel.setTheme(ThemeMode.DARK)
        advanceUntilIdle()
        assertEquals(ThemeMode.SYSTEM, repository.theme.value)
        assertEquals(PreferencesState.Ready(ThemeMode.SYSTEM, saveError = true), viewModel.state.value)
    }

    private class FakeThemeRepository(initial: ThemeMode) : ThemeRepository {
        val theme = MutableStateFlow(initial)
        var failWrite = false
        override fun observeTheme(): Flow<ThemeMode> = theme
        override suspend fun setTheme(theme: ThemeMode) {
            if (failWrite) throw IllegalStateException("test failure")
            this.theme.value = theme
        }
    }
}
