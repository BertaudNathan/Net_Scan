package com.example.MobileM2.Net_Scan.data

import com.example.MobileM2.Net_Scan.domain.ThemeMode
import com.example.MobileM2.Net_Scan.domain.AppPreferences
import com.example.MobileM2.Net_Scan.domain.Handedness
import com.example.MobileM2.Net_Scan.domain.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPreferencesRepository(private val dao: PreferencesDao) : PreferencesRepository {
    override fun observePreferences(): Flow<AppPreferences> = dao.observePreferences().map { preference ->
        if (preference == null) {
            AppPreferences(ThemeMode.SYSTEM, Handedness.RIGHT)
        } else {
            AppPreferences(ThemeMode.valueOf(preference.theme), Handedness.valueOf(preference.handedness))
        }
    }

    override suspend fun setTheme(theme: ThemeMode) {
        dao.saveTheme(theme.name)
    }

    override suspend fun setHandedness(handedness: Handedness) {
        dao.saveHandedness(handedness.name)
    }
}
