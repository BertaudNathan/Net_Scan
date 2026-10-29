package com.example.MobileM2.Net_Scan.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.example.MobileM2.Net_Scan.R

data class ScanExample(
    @param:StringRes val name: Int,
    @param:StringRes val description: Int,
    @param:StringRes val date: Int,
    @param:StringRes val status: Int,
)

sealed interface ScanListState {
    data object Loading : ScanListState
    data object Empty : ScanListState
    data object Error : ScanListState
    data class Success(val scans: List<ScanExample>) : ScanListState
}

class ScanListViewModel : ViewModel() {
    val state: ScanListState = ScanListState.Success(
        listOf(
            ScanExample(
                R.string.example_building_one, R.string.example_client_one,
                R.string.example_date_one, R.string.example_status_done,
            ),
            ScanExample(
                R.string.example_building_two, R.string.example_client_two,
                R.string.example_date_two, R.string.example_status_progress,
            ),
        ),
    )
}
