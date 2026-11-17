package com.example.MobileM2.Net_Scan.domain

import kotlinx.coroutines.flow.Flow

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class Handedness { RIGHT, LEFT }

data class AppPreferences(val theme: ThemeMode, val handedness: Handedness)

interface PreferencesRepository {
    fun observePreferences(): Flow<AppPreferences>
    suspend fun setTheme(theme: ThemeMode)
    suspend fun setHandedness(handedness: Handedness)
}
