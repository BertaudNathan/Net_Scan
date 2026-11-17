package com.example.MobileM2.Net_Scan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.MobileM2.Net_Scan.data.RoomVisitRepository
import com.example.MobileM2.Net_Scan.data.AppDatabase
import com.example.MobileM2.Net_Scan.ui.visits.VisitListViewModel
import com.example.MobileM2.Net_Scan.ui.visits.VisitListScreen
import com.example.MobileM2.Net_Scan.ui.theme.NetScanTheme

private const val VISITS_ROUTE = "visits"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                                initializer {
                                    VisitListViewModel(
                                        RoomVisitRepository(AppDatabase.getInstance(applicationContext).visitDao()),
                                    )
                                }
                            },
                        )
                        VisitListScreen(onExit = ::finish, viewModel = visitListViewModel)
                    }
                }
            }
        }
    }
}
