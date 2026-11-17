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
import com.example.MobileM2.Net_Scan.data.RoomScanRepository
import com.example.MobileM2.Net_Scan.data.ScanDatabase
import com.example.MobileM2.Net_Scan.ui.ScanListViewModel
import com.example.MobileM2.Net_Scan.ui.ScanScreen
import com.example.MobileM2.Net_Scan.ui.theme.NetScanTheme

private const val SCANS_ROUTE = "scans"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetScanTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = SCANS_ROUTE
                ) {
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
                        ScanScreen(onExit = ::finish, viewModel = scanViewModel)
                    }
                }
            }
        }
    }
}
