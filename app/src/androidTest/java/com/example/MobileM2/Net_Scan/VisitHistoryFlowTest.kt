package com.example.MobileM2.Net_Scan

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.MobileM2.Net_Scan.data.AppDatabase
import com.example.MobileM2.Net_Scan.data.RoomNetScanRepository
import com.example.MobileM2.Net_Scan.domain.NetScan
import com.example.MobileM2.Net_Scan.domain.NetScanMode
import com.example.MobileM2.Net_Scan.domain.NetScanSummary
import com.example.MobileM2.Net_Scan.domain.NetworkTechnology
import com.example.MobileM2.Net_Scan.domain.SignalLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisitHistoryFlowTest {
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
    )
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    @Test fun rapidRepeatedNavigationKeepsTheVisitListOnTheBackStack() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(text(R.string.open_preferences)).fetchSemanticsNodes().isNotEmpty()
        }
        repeat(15) {
            compose.onNodeWithContentDescription(text(R.string.open_preferences)).performClick()
            compose.onNodeWithContentDescription(text(R.string.back_to_scans))
                .performSemanticsAction(SemanticsActions.OnClick) { click -> click(); click() }
            compose.onNodeWithText(text(R.string.new_scan)).assertIsDisplayed()
            compose.onNodeWithContentDescription(text(R.string.open_preferences))
                .performSemanticsAction(SemanticsActions.OnClick) { click -> click(); click() }
            compose.onNodeWithContentDescription(text(R.string.back_to_scans)).performClick()
            compose.onNodeWithText(text(R.string.new_scan)).assertIsDisplayed()
        }
    }

    @Test fun systemBackReturnsToTheListAndStillConfirmsExit() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(text(R.string.open_preferences)).fetchSemanticsNodes().isNotEmpty()
        }
        repeat(5) {
            compose.onNodeWithContentDescription(text(R.string.open_preferences)).performClick()
            compose.runOnIdle {
                compose.activity.onBackPressedDispatcher.onBackPressed()
                compose.activity.onBackPressedDispatcher.onBackPressed()
            }
            compose.onNodeWithText(text(R.string.new_scan)).assertIsDisplayed()
        }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(text(R.string.exit_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.stay)).performClick()
        compose.onNodeWithText(text(R.string.new_scan)).assertIsDisplayed()
    }

    @Test fun selectedHandednessAndSavedHistoryFollowTheRealNavigation() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(text(R.string.open_preferences)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(text(R.string.open_preferences)).performClick()
        compose.onNodeWithText(text(R.string.handedness_left)).performScrollTo().performClick()
        compose.waitUntil(10_000) {
            runBlocking {
                AppDatabase.getInstance(context).preferencesDao().observePreferences().first()?.handedness == "LEFT"
            }
        }
        compose.onNodeWithContentDescription(text(R.string.back_to_scans)).performClick()
        val building = "Test ${System.currentTimeMillis()}"
        compose.onNodeWithText(text(R.string.new_scan)).performClick()
        compose.onNodeWithText(text(R.string.building_name)).performTextInput(building)
        compose.onNodeWithText(text(R.string.create_scan)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(building).fetchSemanticsNodes().isNotEmpty() }
        val db = AppDatabase.getInstance(context)
        runBlocking {
            val id = db.visitDao().observeAll().first().first { it.building == building }.id
            RoomNetScanRepository(db.netScanDao()).save(
                NetScan(
                    id, 1_000, 20, NetScanMode.Timer(20), "Opérateur test",
                    NetScanSummary(SignalLevel.MEDIUM, NetworkTechnology.LTE, -95.0, 6.0, 20, 20, 1234),
                ),
            )
        }
        compose.onNodeWithText(building).performClick()
        assertRecordButtonOnLeft()
        compose.onNodeWithText(text(R.string.scan_history_open)).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text(R.string.scan_history_operator, "Opérateur test")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text(R.string.scan_history_operator, "Opérateur test")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.close)).performClick()
        compose.activityRule.scenario.recreate()
        assertRecordButtonOnLeft()
    }

    private fun assertRecordButtonOnLeft() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(text(R.string.record_start)).fetchSemanticsNodes().isNotEmpty()
        }
        val root = compose.onRoot().fetchSemanticsNode().boundsInWindow
        val record = compose.onNodeWithContentDescription(text(R.string.record_start)).fetchSemanticsNode().boundsInWindow
        val close = compose.onNodeWithContentDescription(text(R.string.map_scan_close)).fetchSemanticsNode().boundsInWindow
        assertTrue("The saved preference places the control on the left", record.center.x < root.center.x)
        assertTrue("The saved preference places the close button on the left", close.center.x < root.center.x)
    }
}
