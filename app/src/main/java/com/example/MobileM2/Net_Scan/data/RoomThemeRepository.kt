package com.example.MobileM2.Net_Scan.data

import com.example.MobileM2.Net_Scan.domain.ThemeMode
import com.example.MobileM2.Net_Scan.domain.ThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomThemeRepository(private val dao: PreferencesDao) : ThemeRepository {
    override fun observeTheme(): Flow<ThemeMode> = dao.observePreferences().map { preference ->
        if (preference == null) ThemeMode.SYSTEM else ThemeMode.valueOf(preference.theme)
    }

    override suspend fun setTheme(theme: ThemeMode) {
        dao.save(PreferencesEntity(theme = theme.name))
    }
}
