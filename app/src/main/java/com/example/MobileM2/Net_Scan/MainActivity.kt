package com.example.MobileM2.Net_Scan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.example.MobileM2.Net_Scan.data.RoomVisitRepository
import com.example.MobileM2.Net_Scan.data.signal.TelephonySignalSource
import com.example.MobileM2.Net_Scan.ui.mapscan.MapScanScreen
import com.example.MobileM2.Net_Scan.ui.mapscan.MapScanViewModel
import com.example.MobileM2.Net_Scan.ui.theme.NetScanTheme
import com.example.MobileM2.Net_Scan.ui.visits.VisitListScreen
import com.example.MobileM2.Net_Scan.ui.visits.VisitListViewModel

private const val VISITS_ROUTE = "visits"
private const val VISIT_ID = "visitId"
private const val MAP_SCAN_ROUTE = "visits/{$VISIT_ID}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = AppDatabase.getInstance(applicationContext)
        setContent {
            NetScanTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = VISITS_ROUTE
                ) {
                    composable(VISITS_ROUTE) {
                        val visitListViewModel: VisitListViewModel = viewModel(
                            factory = viewModelFactory {
                                initializer { VisitListViewModel(RoomVisitRepository(database.visitDao())) }
                            },
                        )
                        VisitListScreen(
                            onExit = ::finish,
                            onOpenVisit = { id -> navController.navigate("visits/$id") },
                            viewModel = visitListViewModel,
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
