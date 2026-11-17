package com.example.MobileM2.Net_Scan

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MobileM2.Net_Scan.data.RoomThemeRepository
import com.example.MobileM2.Net_Scan.data.ScanDatabase
import com.example.MobileM2.Net_Scan.domain.ThemeMode
import com.example.MobileM2.Net_Scan.ui.PreferencesScreen
import com.example.MobileM2.Net_Scan.ui.PreferencesState
import com.example.MobileM2.Net_Scan.ui.PreferencesViewModel
import com.example.MobileM2.Net_Scan.ui.theme.DarkColors
import com.example.MobileM2.Net_Scan.ui.theme.LightColors
import com.example.MobileM2.Net_Scan.ui.theme.NetScanTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PreferencesScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var db: ScanDatabase

    @Before fun setup() {
        databaseName = "preferences-test-${UUID.randomUUID()}.db"
        db = openDatabase()
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase(): ScanDatabase = Room.databaseBuilder(context, ScanDatabase::class.java, databaseName)
        .addMigrations(ScanDatabase.MIGRATION_1_2)
        .build()

    private fun text(id: Int) = context.getString(id)

    @Test fun choicesUpdateThemeAndPersistWhenDatabaseReopens() {
        val viewModel = PreferencesViewModel(RoomThemeRepository(db.preferencesDao()))
        var currentPrimary = LightColors.primary
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                if (state is PreferencesState.Ready) {
                    val ready = state as PreferencesState.Ready
                    NetScanTheme(theme = ready.theme) {
                        currentPrimary = MaterialTheme.colorScheme.primary
                        PreferencesScreen(state = ready, onThemeSelected = viewModel::setTheme, onBack = {})
                    }
                } else {
                    Text(text(R.string.loading_preferences))
                }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text(R.string.theme_system)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text(R.string.theme_system)).assertIsSelected()
        compose.onNodeWithText(text(R.string.theme_dark))
            .assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.waitUntil(10_000) { (viewModel.state.value as? PreferencesState.Ready)?.theme == ThemeMode.DARK }
        compose.onNodeWithText(text(R.string.theme_dark)).assertIsSelected()
        compose.runOnIdle { assertEquals(DarkColors.primary, currentPrimary) }
        compose.onNodeWithText(text(R.string.theme_light)).performClick()
        compose.waitUntil(10_000) { (viewModel.state.value as? PreferencesState.Ready)?.theme == ThemeMode.LIGHT }
        compose.runOnIdle { assertEquals(LightColors.primary, currentPrimary) }
        compose.onNodeWithText(text(R.string.theme_system)).performClick()
        compose.waitUntil(10_000) { (viewModel.state.value as? PreferencesState.Ready)?.theme == ThemeMode.SYSTEM }
        compose.onNodeWithText(text(R.string.theme_system)).assertIsSelected()
        compose.onNodeWithText(text(R.string.theme_dark)).performClick()
        compose.waitUntil(10_000) { (viewModel.state.value as? PreferencesState.Ready)?.theme == ThemeMode.DARK }
        db.close()
        db = openDatabase()
        assertEquals(ThemeMode.DARK, runBlocking { RoomThemeRepository(db.preferencesDao()).observeTheme().first() })
    }

    @Test fun migrationPreservesScansAndDefaultsThemeToSystem() = runBlocking {
        db.close()
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { sqlite ->
            sqlite.execSQL(
                "CREATE TABLE IF NOT EXISTS scans (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, building TEXT NOT NULL, client TEXT NOT NULL, address TEXT NOT NULL, createdAt INTEGER NOT NULL)",
            )
            sqlite.execSQL(
                "INSERT INTO scans (building, client, address, createdAt) VALUES ('Magasin', 'Client', 'Adresse', 42)",
            )
            sqlite.version = 1
        }
        db = openDatabase()
        assertEquals("Magasin", db.scanDao().observeAll().first().single().building)
        assertEquals(ThemeMode.SYSTEM, RoomThemeRepository(db.preferencesDao()).observeTheme().first())
    }

    @Test fun systemThemeRespondsToSystemChanges() {
        val systemDark = mutableStateOf(false)
        val selected = mutableStateOf(ThemeMode.SYSTEM)
        var currentPrimary = LightColors.primary
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (systemDark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                NetScanTheme(theme = selected.value) { currentPrimary = MaterialTheme.colorScheme.primary }
            }
        }
        compose.runOnIdle { assertEquals(LightColors.primary, currentPrimary); systemDark.value = true }
        compose.runOnIdle { assertEquals(DarkColors.primary, currentPrimary); selected.value = ThemeMode.LIGHT }
        compose.runOnIdle { assertEquals(LightColors.primary, currentPrimary); selected.value = ThemeMode.DARK }
        compose.runOnIdle { assertEquals(DarkColors.primary, currentPrimary); systemDark.value = false }
        compose.runOnIdle { assertEquals(DarkColors.primary, currentPrimary) }
    }
}
