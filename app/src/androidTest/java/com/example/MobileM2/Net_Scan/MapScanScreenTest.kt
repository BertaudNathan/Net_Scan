package com.example.MobileM2.Net_Scan

import android.Manifest
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.MobileM2.Net_Scan.data.AppDatabase
import com.example.MobileM2.Net_Scan.data.RoomNetScanRepository
import com.example.MobileM2.Net_Scan.data.RoomVisitRepository
import com.example.MobileM2.Net_Scan.data.signal.SimulatedSignalSource
import com.example.MobileM2.Net_Scan.domain.SignalAvailability
import com.example.MobileM2.Net_Scan.ui.mapscan.MapScanScreen
import com.example.MobileM2.Net_Scan.ui.mapscan.MapScanViewModel
import com.example.MobileM2.Net_Scan.ui.theme.NetScanTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class MapScanScreenTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
    )

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private var visitId = 0L
    private var closed = false

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        visitId = runBlocking {
            RoomVisitRepository(db.visitDao()).createVisit("Entrepôt Nord", "", "")
            db.visitDao().observeAll().first().single().id
        }
    }

    @After fun tearDown() { db.close() }

    private fun showScreen(source: SimulatedSignalSource = SimulatedSignalSource()) {
        val viewModel = MapScanViewModel(
            visitId,
            RoomVisitRepository(db.visitDao()),
            RoomNetScanRepository(db.netScanDao()),
            source,
        )
        compose.setContent {
            NetScanTheme {
                MapScanScreen(viewModel = viewModel, onClose = { closed = true }, cameraPreview = { Box(it) })
            }
        }
        waitForText("Entrepôt Nord")
    }

    private fun waitForText(value: String, timeout: Long = 10_000) = compose.waitUntil(timeout) {
        compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun headerShowsBuildingOperatorAndSimulatedData() {
        showScreen()
        waitForText(text(R.string.operator_with_technology, "Simulation", text(R.string.technology_4g)))
        compose.onNodeWithText(text(R.string.simulated_data)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.map_placeholder_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.map_scan_close))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test fun controlsFollowTheAgreedLayout() {
        showScreen()
        val root = compose.onRoot().fetchSemanticsNode().boundsInWindow
        val close = compose.onNodeWithContentDescription(text(R.string.map_scan_close)).fetchSemanticsNode().boundsInWindow
        val title = compose.onNodeWithText("Entrepôt Nord").fetchSemanticsNode().boundsInWindow
        val scan = compose.onNodeWithText(text(R.string.net_scan_start), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInWindow
        val record = compose.onNodeWithContentDescription(text(R.string.record_start)).fetchSemanticsNode().boundsInWindow

        assertTrue("Close is top right", close.right >= root.right - root.width * 0.2f && close.top < root.height * 0.2f)
        assertTrue("Title sits between the preview and the right edge", title.left > root.width * 0.2f)
        assertTrue("Network scan is at the bottom", scan.top > root.height * 0.7f)
        assertTrue("Network scan is centred", abs(scan.center.x - root.center.x) < 24f * context.resources.displayMetrics.density)
        assertTrue("Record is bottom right", record.left > scan.right && record.top > root.height * 0.75f)
        compose.onNodeWithContentDescription(text(R.string.record_start))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test fun timedNetScanShowsTheLevelWordThenSavesTheResult() {
        showScreen()
        compose.onNodeWithText(text(R.string.net_scan_mode_timer, 30)).performClick()
        compose.onNodeWithText(text(R.string.net_scan_mode_timer_item, 20)).performClick()
        compose.onNodeWithText(text(R.string.net_scan_start)).performClick()
        waitForText(text(R.string.net_scan_running))
        val medium = text(R.string.level_description, text(R.string.level_medium))
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(medium).fetchSemanticsNodes().isNotEmpty()
        }
        waitForText(text(R.string.net_scan_saved), timeout = 30_000)
        compose.onNodeWithText(text(R.string.net_scan_result)).assertIsDisplayed()

        val saved = runBlocking { db.netScanDao().observeForVisit(visitId).first() }.single()
        assertEquals(20, saved.durationSeconds)
        assertEquals("MEDIUM", saved.level)

        compose.onNodeWithText(text(R.string.close)).performClick()
        compose.onNodeWithText(text(R.string.net_scan_start)).assertIsDisplayed()
    }

    @Test fun cancellingANetScanKeepsNothing() {
        showScreen()
        compose.onNodeWithText(text(R.string.net_scan_start)).performClick()
        waitForText(text(R.string.net_scan_running))
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        compose.onNodeWithText(text(R.string.net_scan_start)).assertIsDisplayed()
        assertTrue(runBlocking { db.netScanDao().observeForVisit(visitId).first() }.isEmpty())
    }

    @Test fun missingSimIsExplained() {
        showScreen(SimulatedSignalSource(availability = SignalAvailability.NO_SIM))
        compose.onNodeWithText(text(R.string.net_scan_start)).performClick()
        compose.onNodeWithText(text(R.string.problem_no_sim)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.close)).performClick()
        compose.onNodeWithText(text(R.string.net_scan_start)).assertIsDisplayed()
    }

    @Test fun recordButtonStartsAndStopsRecording() {
        showScreen()
        compose.onNodeWithContentDescription(text(R.string.record_start)).performClick()
        compose.onNodeWithContentDescription(text(R.string.record_stop)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.map_placeholder_recording)).assertIsDisplayed()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(text(R.string.recording_indicator_description, "0:01"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(text(R.string.record_stop)).performClick()
        compose.onNodeWithContentDescription(text(R.string.record_start)).assertIsDisplayed()
    }

    @Test fun leavingWhileRecordingAsksForConfirmation() {
        showScreen()
        compose.onNodeWithContentDescription(text(R.string.record_start)).performClick()
        compose.onNodeWithContentDescription(text(R.string.map_scan_close)).performClick()
        compose.onNodeWithText(text(R.string.leave_visit_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.stay)).performClick()
        assertTrue(!closed)

        compose.onNodeWithContentDescription(text(R.string.record_stop)).performClick()
        compose.onNodeWithContentDescription(text(R.string.map_scan_close)).performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
}
