package com.example.MobileM2.Net_Scan

import android.Manifest
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.MobileM2.Net_Scan.data.AppDatabase
import com.example.MobileM2.Net_Scan.data.RoomNetScanRepository
import com.example.MobileM2.Net_Scan.data.RoomRecordingRepository
import com.example.MobileM2.Net_Scan.data.RoomVisitRepository
import com.example.MobileM2.Net_Scan.data.position.SimulatedPositionSource
import com.example.MobileM2.Net_Scan.data.signal.SimulatedSignalSource
import com.example.MobileM2.Net_Scan.domain.PositionSource
import com.example.MobileM2.Net_Scan.domain.PositionState
import com.example.MobileM2.Net_Scan.domain.PositioningUnavailable
import com.example.MobileM2.Net_Scan.domain.SignalAvailability
import com.example.MobileM2.Net_Scan.domain.Handedness
import com.example.MobileM2.Net_Scan.domain.NetScan
import com.example.MobileM2.Net_Scan.domain.NetScanMode
import com.example.MobileM2.Net_Scan.domain.NetScanSummary
import com.example.MobileM2.Net_Scan.domain.NetworkTechnology
import com.example.MobileM2.Net_Scan.domain.SignalLevel
import com.example.MobileM2.Net_Scan.ui.mapscan.MapScanScreen
import com.example.MobileM2.Net_Scan.ui.mapscan.MapScanViewModel
import com.example.MobileM2.Net_Scan.ui.theme.NetScanTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    private fun showScreen(
        source: SimulatedSignalSource = SimulatedSignalSource(),
        positions: PositionSource = SimulatedPositionSource(startupMillis = 300),
        handedness: Handedness = Handedness.RIGHT,
        direction: LayoutDirection = LayoutDirection.Ltr,
        fontScale: Float = 1f,
    ) {
        val viewModel = MapScanViewModel(
            visitId = visitId,
            visitRepository = RoomVisitRepository(db.visitDao()),
            netScanRepository = RoomNetScanRepository(db.netScanDao()),
            recordingRepository = RoomRecordingRepository(db.recordingDao(), db.netScanDao()),
            signalSource = source,
            positionSource = positions,
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalLayoutDirection provides direction,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                NetScanTheme {
                    MapScanScreen(viewModel = viewModel, onClose = { closed = true }, handedness = handedness, cameraPreview = { Box(it.testTag("camera-preview")) })
                }
            }
        }
        waitForText("Entrepôt Nord")
    }

    private fun waitForText(value: String, timeout: Long = 10_000) = compose.waitUntil(timeout) {
        compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
    }

    /** Début du texte, avant le premier argument, d'une ressource à paramètres. */
    private fun prefix(id: Int) = text(id, "").substringBefore(",").trim()

    private fun waitForDescription(start: String, timeout: Long = 10_000) {
        val matcher = SemanticsMatcher("description starting with $start") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith(start) }
        }
        compose.waitUntil(timeout) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun headerShowsBuildingOperatorAndSimulatedData() {
        showScreen()
        waitForText(text(R.string.operator_with_technology, "Simulation", text(R.string.technology_4g)))
        compose.onNodeWithText(text(R.string.simulated_data)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.map_empty_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.map_scan_close))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test fun controlsFollowTheAgreedLayout() {
        showScreen()
        assertHeaderPosition(closeOnLeft = false)
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
        // Le scan a démarré l'enregistrement : il est placé sur la carte de ce parcours.
        assertNotNull(saved.recordingId)
        assertNotNull(saved.mapX)
        val placed = text(
            R.string.map_measurements_detail,
            context.resources.getQuantityString(R.plurals.map_measurements, 1, 1),
            text(R.string.map_level_count, text(R.string.level_medium), 1),
        )
        waitForDescription(text(R.string.map_description, placed))

        compose.onNodeWithText(text(R.string.close)).performClick()
        compose.onNodeWithText(text(R.string.net_scan_start)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.scan_history_open)).performClick()
        compose.onNodeWithText(text(R.string.scan_history_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.scan_history_operator, "Simulation")).assertIsDisplayed()
    }

    @Test fun leftHandedButtonStaysOnPhysicalLeftInRtl() {
        showScreen(handedness = Handedness.LEFT, direction = LayoutDirection.Rtl)
        assertHeaderPosition(closeOnLeft = true)
        val root = compose.onRoot().fetchSemanticsNode().boundsInWindow
        val record = compose.onNodeWithContentDescription(text(R.string.record_start)).fetchSemanticsNode().boundsInWindow
        assertTrue("Left-handed control is physically left", record.center.x < root.center.x)
    }

    @Test fun leftHandedButtonStaysOnPhysicalLeftInLtr() {
        showScreen(handedness = Handedness.LEFT)
        assertHeaderPosition(closeOnLeft = true)
        val root = compose.onRoot().fetchSemanticsNode().boundsInWindow
        val record = compose.onNodeWithContentDescription(text(R.string.record_start)).fetchSemanticsNode().boundsInWindow
        assertTrue("Left-handed control is physically left", record.center.x < root.center.x)
    }

    @Test fun rightHandedButtonStaysOnPhysicalRightInRtl() {
        showScreen(handedness = Handedness.RIGHT, direction = LayoutDirection.Rtl)
        assertHeaderPosition(closeOnLeft = false)
        val root = compose.onRoot().fetchSemanticsNode().boundsInWindow
        val record = compose.onNodeWithContentDescription(text(R.string.record_start)).fetchSemanticsNode().boundsInWindow
        assertTrue("Right-handed control is physically right", record.center.x > root.center.x)
    }

    private fun assertHeaderPosition(closeOnLeft: Boolean) {
        val root = compose.onRoot().fetchSemanticsNode().boundsInWindow
        val close = compose.onNodeWithContentDescription(text(R.string.map_scan_close))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInWindow
        val camera = compose.onNodeWithTag("camera-preview").fetchSemanticsNode().boundsInWindow
        if (closeOnLeft) {
            assertTrue("Close is on the physical left", close.center.x < root.center.x)
            assertTrue("Camera is on the physical right", camera.center.x > root.center.x)
        } else {
            assertTrue("Close is on the physical right", close.center.x > root.center.x)
            assertTrue("Camera is on the physical left", camera.center.x < root.center.x)
        }
    }

    @Test fun historyShowsOnlyThisVisitsSavedScansNewestFirstWithDetails() = runBlocking {
        RoomVisitRepository(db.visitDao()).createVisit("Autre", "", "")
        val otherId = db.visitDao().observeAll().first().first { it.id != visitId }.id
        val repository = RoomNetScanRepository(db.netScanDao())
        fun scan(visit: Long, date: Long, operator: String) = NetScan(
            visit, date, 20, NetScanMode.Timer(20), operator,
            NetScanSummary(SignalLevel.MEDIUM, NetworkTechnology.LTE, -95.0, 6.0, 20, 20, 1234),
        )
        repository.save(scan(visitId, 1_000, "Ancien"))
        repository.save(scan(visitId, 2_000, "Récent"))
        repository.save(scan(otherId, 3_000, "Autre opérateur"))
        val rows = repository.observeForVisit(visitId).first()
        assertEquals(listOf("Récent", "Ancien"), rows.map { it.operatorName })
        showScreen()
        compose.onNodeWithText(text(R.string.scan_history_open)).performClick()
        waitForText(text(R.string.scan_history_operator, "Récent"))
        compose.onNodeWithText(text(R.string.scan_history_operator, "Autre opérateur")).assertDoesNotExist()
        compose.onAllNodesWithText(text(R.string.details_show))[0].performClick()
        compose.onNodeWithText(text(R.string.detail_rsrp)).performScrollTo().assertIsDisplayed()
        Unit
    }

    @Test fun historyExplainsWhenNoMeasurementsHaveBeenSaved() {
        showScreen()
        compose.onNodeWithText(text(R.string.scan_history_open)).performClick()
        waitForText(text(R.string.scan_history_empty))
        compose.onNodeWithText(text(R.string.scan_history_empty)).assertIsDisplayed()
    }

    @Test fun historyRemainsAccessibleWithLargeTextAndRtl() {
        runBlocking {
            RoomNetScanRepository(db.netScanDao()).save(
                NetScan(
                    visitId, 1_000, 20, NetScanMode.Timer(20), "Orange",
                    NetScanSummary(SignalLevel.MEDIUM, NetworkTechnology.LTE, -95.0, 6.0, 20, 20, 1234),
                ),
            )
        }
        showScreen(handedness = Handedness.LEFT, direction = LayoutDirection.Rtl, fontScale = 2f)
        compose.onNodeWithText(text(R.string.scan_history_open))
            .assertHeightIsAtLeast(48.dp).performClick()
        waitForText(text(R.string.scan_history_operator, "Orange"))
        compose.onNodeWithText(text(R.string.scan_history_operator, "Orange")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.close)).assertIsDisplayed().performClick()
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
        waitForDescription(prefix(R.string.map_description))
        // N'importe quelle durée au-delà de 0:00 : sous charge, la seconde exacte peut être manquée.
        val prefix = text(R.string.recording_indicator_description, "")
        val counting = SemanticsMatcher("recording time above zero") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                .any { it.startsWith(prefix) && !it.endsWith("0:00") }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(counting).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(text(R.string.record_stop)).performClick()
        compose.onNodeWithContentDescription(text(R.string.record_start)).assertIsDisplayed()
        // Le parcours sauvegardé reste affiché.
        val lastRecording = text(R.string.map_last_recording, "").trim()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(lastRecording, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun unavailablePositioningIsExplainedAndScansStayPossible() {
        val unsupported = object : PositionSource {
            override val isSimulated = false
            override val position = flowOf<PositionState>(PositionState.Unavailable(PositioningUnavailable.UNSUPPORTED_DEVICE))
        }
        showScreen(positions = unsupported)
        waitForText(text(R.string.mapping_unavailable_title))
        compose.onNodeWithText(text(R.string.net_scan_start)).assertIsDisplayed()
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
