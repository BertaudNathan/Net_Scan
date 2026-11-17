package com.example.MobileM2.Net_Scan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MobileM2.Net_Scan.data.RoomScanRepository
import com.example.MobileM2.Net_Scan.data.RoomThemeRepository
import com.example.MobileM2.Net_Scan.data.ScanDatabase
import com.example.MobileM2.Net_Scan.domain.ThemeMode
import com.example.MobileM2.Net_Scan.ui.PreferencesScreen
import com.example.MobileM2.Net_Scan.ui.PreferencesState
import com.example.MobileM2.Net_Scan.ui.PreferencesViewModel
import com.example.MobileM2.Net_Scan.ui.ScanListViewModel
import com.example.MobileM2.Net_Scan.ui.ScanScreen
import com.example.MobileM2.Net_Scan.ui.theme.NetScanTheme
import com.example.MobileM2.Net_Scan.ui.theme.Design
import com.example.MobileM2.Net_Scan.ui.theme.TertiaryButton

private const val SCANS_ROUTE = "scans"
private const val PREFERENCES_ROUTE = "preferences"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences: PreferencesViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        PreferencesViewModel(RoomThemeRepository(ScanDatabase.getInstance(applicationContext).preferencesDao()))
                    }
                },
            )
            val state by preferences.state.collectAsStateWithLifecycle()
            val theme = (state as? PreferencesState.Ready)?.theme ?: ThemeMode.SYSTEM
            val dark = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                val style = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            NetScanTheme(theme = theme) {
                when (val current = state) {
                    PreferencesState.Loading -> Column(
                        modifier = Modifier.fillMaxSize().padding(Design.spaceLarge),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.loading_preferences))
                    }
                    PreferencesState.Error -> Column(
                        modifier = Modifier.fillMaxSize().padding(Design.spaceLarge),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.theme_load_error), color = MaterialTheme.colorScheme.error)
                        TertiaryButton(onClick = preferences::retry) { Text(stringResource(R.string.retry)) }
                    }
                    is PreferencesState.Ready -> {
                        val navController = rememberNavController()
                        NavHost(navController = navController, startDestination = SCANS_ROUTE) {
                            composable(SCANS_ROUTE) {
                                val scanViewModel: ScanListViewModel = viewModel(
                                    factory = viewModelFactory {
                                        initializer {
                                            ScanListViewModel(
                                                RoomScanRepository(ScanDatabase.getInstance(applicationContext).scanDao()),
                                            )
                                        }
                                    },
                                )
                                ScanScreen(
                                    onExit = ::finish,
                                    onPreferences = { navController.navigate(PREFERENCES_ROUTE) },
                                    viewModel = scanViewModel,
                                )
                            }
                            composable(PREFERENCES_ROUTE) {
                                PreferencesScreen(
                                    state = current,
                                    onThemeSelected = preferences::setTheme,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
