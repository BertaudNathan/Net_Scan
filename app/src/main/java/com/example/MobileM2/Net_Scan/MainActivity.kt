package com.example.MobileM2.Net_Scan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.MobileM2.Net_Scan.data.AppDatabase
import com.example.MobileM2.Net_Scan.data.RoomNetScanRepository
import com.example.MobileM2.Net_Scan.data.RoomThemeRepository
import com.example.MobileM2.Net_Scan.data.RoomVisitRepository
import com.example.MobileM2.Net_Scan.data.signal.TelephonySignalSource
import com.example.MobileM2.Net_Scan.domain.ThemeMode
import com.example.MobileM2.Net_Scan.ui.PreferencesScreen
import com.example.MobileM2.Net_Scan.ui.PreferencesState
import com.example.MobileM2.Net_Scan.ui.PreferencesViewModel
import com.example.MobileM2.Net_Scan.ui.mapscan.MapScanScreen
import com.example.MobileM2.Net_Scan.ui.mapscan.MapScanViewModel
import com.example.MobileM2.Net_Scan.ui.theme.Design
import com.example.MobileM2.Net_Scan.ui.theme.NetScanTheme
import com.example.MobileM2.Net_Scan.ui.theme.TertiaryButton
import com.example.MobileM2.Net_Scan.ui.visits.VisitListScreen
import com.example.MobileM2.Net_Scan.ui.visits.VisitListViewModel

private const val VISITS_ROUTE = "visits"
private const val PREFERENCES_ROUTE = "preferences"
private const val VISIT_ID = "visitId"
private const val MAP_SCAN_ROUTE = "visits/{$VISIT_ID}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = AppDatabase.getInstance(applicationContext)
        setContent {
            val preferences: PreferencesViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { PreferencesViewModel(RoomThemeRepository(database.preferencesDao())) }
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
                        NavHost(navController = navController, startDestination = VISITS_ROUTE) {
                            composable(VISITS_ROUTE) {
                                val visitListViewModel: VisitListViewModel = viewModel(
                                    factory = viewModelFactory {
                                        initializer { VisitListViewModel(RoomVisitRepository(database.visitDao())) }
                                    },
                                )
                                VisitListScreen(
                                    onExit = ::finish,
                                    onPreferences = { navController.navigate(PREFERENCES_ROUTE) },
                                    onOpenVisit = { id -> navController.navigate("visits/$id") },
                                    viewModel = visitListViewModel,
                                )
                            }
                            composable(PREFERENCES_ROUTE) {
                                PreferencesScreen(
                                    state = current,
                                    onThemeSelected = preferences::setTheme,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable(
                                route = MAP_SCAN_ROUTE,
                                arguments = listOf(navArgument(VISIT_ID) { type = NavType.LongType }),
                            ) { entry ->
                                val visitId = entry.arguments?.getLong(VISIT_ID) ?: 0L
                                val mapScanViewModel: MapScanViewModel = viewModel(
                                    factory = viewModelFactory {
                                        initializer {
                                            MapScanViewModel(
                                                visitId = visitId,
                                                visitRepository = RoomVisitRepository(database.visitDao()),
                                                netScanRepository = RoomNetScanRepository(database.netScanDao()),
                                                signalSource = TelephonySignalSource(applicationContext),
                                            )
                                        }
                                    },
                                )
                                MapScanScreen(
                                    viewModel = mapScanViewModel,
                                    onClose = { navController.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
