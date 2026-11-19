package com.example.MobileM2.Net_Scan.ui.mapscan

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.MobileM2.Net_Scan.domain.AlignmentRules
import com.example.MobileM2.Net_Scan.domain.FloorPlan
import com.example.MobileM2.Net_Scan.domain.FloorPlanRepository
import com.example.MobileM2.Net_Scan.domain.FloorPoint
import com.example.MobileM2.Net_Scan.domain.GridCell
import com.example.MobileM2.Net_Scan.domain.FloorPose
import com.example.MobileM2.Net_Scan.domain.ImportedPlan
import com.example.MobileM2.Net_Scan.domain.MapMeasurement
import com.example.MobileM2.Net_Scan.domain.MappingMode
import com.example.MobileM2.Net_Scan.domain.NetScan
import com.example.MobileM2.Net_Scan.domain.NetScanMode
import com.example.MobileM2.Net_Scan.domain.NetScanRepository
import com.example.MobileM2.Net_Scan.domain.NetworkTechnology
import com.example.MobileM2.Net_Scan.domain.OperatorStatus
import com.example.MobileM2.Net_Scan.domain.PlanImporter
import com.example.MobileM2.Net_Scan.domain.PlanMeasurement
import com.example.MobileM2.Net_Scan.domain.PlanPoint
import com.example.MobileM2.Net_Scan.domain.PositionSource
import com.example.MobileM2.Net_Scan.domain.PositionState
import com.example.MobileM2.Net_Scan.domain.PositioningUnavailable
import com.example.MobileM2.Net_Scan.domain.RecordingRepository
import com.example.MobileM2.Net_Scan.domain.SavedRecording
import com.example.MobileM2.Net_Scan.domain.SceneObservation
import com.example.MobileM2.Net_Scan.domain.ScenePoint
import com.example.MobileM2.Net_Scan.domain.SignalAvailability
import com.example.MobileM2.Net_Scan.domain.SignalLevel
import com.example.MobileM2.Net_Scan.domain.SignalSample
import com.example.MobileM2.Net_Scan.domain.SignalSource
import com.example.MobileM2.Net_Scan.domain.TrackPoint
import com.example.MobileM2.Net_Scan.domain.TrackingProblem
import com.example.MobileM2.Net_Scan.domain.Visit
import com.example.MobileM2.Net_Scan.domain.VisitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapScanViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val visits = FakeVisitRepository()
    private val netScans = FakeNetScanRepository()
    private val source = FakeSignalSource()
    private val positions = FakePositionSource()
    private val recordings = FakeRecordingRepository()
    private val plans = FakeFloorPlanRepository()
    private var importer = PlanImporter { _, _ -> ImportedPlan("plans/visit.png", 2000, 1000) }
    private val store = ViewModelStore()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    /**
     * Le ViewModel est effacé à la fin de chaque test, comme en quittant l'écran : sinon l'enregistrement
     * (démarré aussi par un scan réseau) tourne sans fin et runTest ne se termine jamais.
     */
    private fun vmTest(body: TestScope.() -> Unit): TestResult = runTest(dispatcher) {
        try {
            body()
        } finally {
            store.clear()
        }
    }

    private fun TestScope.viewModel(): MapScanViewModel {
        val factory = viewModelFactory {
            initializer {
                MapScanViewModel(
                    visitId = VISIT_ID,
                    visitRepository = visits,
                    netScanRepository = netScans,
                    recordingRepository = recordings,
                    planRepository = plans,
                    planImporter = { visitId, uri -> importer.import(visitId, uri) },
                    signalSource = source,
                    positionSource = positions,
                    clock = { 1_000L },
                    mapDispatcher = dispatcher,
                )
            }
        }
        return ViewModelProvider.create(store, factory)[MapScanViewModel::class].also { runCurrent() }
    }

    private fun TestScope.seconds(count: Int) {
        advanceTimeBy(count * 1_000L)
        runCurrent()
    }

    private fun running(viewModel: MapScanViewModel) = viewModel.netScan.value as NetScanState.Running

    @Test fun showsTheVisitAndTheCurrentOperator() = vmTest {
        val viewModel = viewModel()
        assertEquals("Entrepôt Nord", (viewModel.visit.value as VisitState.Loaded).visit.building)
        assertEquals(OperatorStatus.Connected("Orange", NetworkTechnology.LTE), viewModel.operator.value)
        assertTrue(viewModel.isSimulated)
    }

    @Test fun reportsAMissingVisit() = vmTest {
        visits.visit.value = null
        assertEquals(VisitState.Missing, viewModel().visit.value)
    }

    @Test fun timerCountsDownThenSavesTheMedian() = vmTest {
        source.signal.value = lte(rsrp = -90, sinr = 5)
        val viewModel = viewModel()
        viewModel.selectMode(NetScanMode.Timer(20))
        viewModel.startNetScan()
        seconds(10)
        val halfway = running(viewModel)
        assertEquals(10, halfway.remainingSeconds)
        assertEquals(SignalLevel.MEDIUM, halfway.summary!!.level)

        source.signal.value = lte(rsrp = -70, sinr = 20)
        seconds(10)
        val completed = viewModel.netScan.value as NetScanState.Completed
        assertEquals(SaveState.SAVED, completed.saveState)
        val saved = netScans.saved.single()
        assertEquals(VISIT_ID, saved.visitId)
        assertEquals(20, saved.durationSeconds)
        assertEquals("Orange", saved.operatorName)
        assertEquals(20, saved.summary.sampleCount)
        assertEquals(-80.0, saved.summary.rsrpDbm!!, 0.0)
        assertEquals(1_000L, saved.startedAt)
    }

    @Test fun freeModeCanOnlyFinishAfterTheMinimumDuration() = vmTest {
        source.signal.value = lte(rsrp = -110)
        val viewModel = viewModel()
        viewModel.selectMode(NetScanMode.Free)
        viewModel.startNetScan()
        seconds(5)
        assertNull(running(viewModel).remainingSeconds)
        assertFalse(running(viewModel).canFinish)
        viewModel.finishNetScan()
        assertTrue(viewModel.netScan.value is NetScanState.Running)

        seconds(NetScanMode.MIN_DURATION_SECONDS - 5)
        assertTrue(running(viewModel).canFinish)
        seconds(4)
        viewModel.finishNetScan()
        runCurrent()
        val completed = viewModel.netScan.value as NetScanState.Completed
        assertEquals(SignalLevel.LOW, completed.result.summary.level)
        assertEquals(24, netScans.saved.single().durationSeconds)
    }

    @Test fun cancellingDiscardsTheScan() = vmTest {
        source.signal.value = lte(rsrp = -90)
        val viewModel = viewModel()
        viewModel.startNetScan()
        seconds(5)
        viewModel.cancelNetScan()
        seconds(60)
        assertEquals(NetScanState.Idle, viewModel.netScan.value)
        assertTrue(netScans.saved.isEmpty())
    }

    @Test fun noServiceIsSavedAsAResult() = vmTest {
        source.signal.value = SignalSample(inService = false)
        val viewModel = viewModel()
        viewModel.selectMode(NetScanMode.Timer(20))
        viewModel.startNetScan()
        seconds(20)
        assertEquals(SignalLevel.NONE, netScans.saved.single().summary.level)
    }

    @Test fun systemConditionsAreExplainedInsteadOfMeasuring() = vmTest {
        val expected = mapOf(
            SignalAvailability.NO_SIM to NetScanProblem.NO_SIM,
            SignalAvailability.AIRPLANE_MODE to NetScanProblem.AIRPLANE_MODE,
            SignalAvailability.LOCATION_DISABLED to NetScanProblem.LOCATION_DISABLED,
            SignalAvailability.NO_TELEPHONY to NetScanProblem.NO_TELEPHONY,
        )
        val viewModel = viewModel()
        expected.forEach { (availability, problem) ->
            source.availability = availability
            viewModel.startNetScan()
            assertEquals(NetScanState.Unavailable(problem), viewModel.netScan.value)
            viewModel.dismissNetScan()
        }
    }

    @Test fun deniedPermissionsAreExplained() = vmTest {
        val viewModel = viewModel()
        viewModel.onPermissionsDenied()
        assertEquals(NetScanState.Unavailable(NetScanProblem.PERMISSION_DENIED), viewModel.netScan.value)
    }

    @Test fun permissionRevokedDuringTheScanStopsIt() = vmTest {
        source.failure = SecurityException("revoked")
        val viewModel = viewModel()
        viewModel.startNetScan()
        seconds(1)
        assertEquals(NetScanState.Unavailable(NetScanProblem.PERMISSION_DENIED), viewModel.netScan.value)
        seconds(60)
        assertTrue(netScans.saved.isEmpty())
    }

    @Test fun silentModemGivesNoData() = vmTest {
        val viewModel = viewModel()
        viewModel.selectMode(NetScanMode.Timer(20))
        viewModel.startNetScan()
        seconds(20)
        assertEquals(NetScanState.Unavailable(NetScanProblem.NO_DATA), viewModel.netScan.value)
    }

    @Test fun failedSaveCanBeRetried() = vmTest {
        source.signal.value = lte(rsrp = -90)
        netScans.failSave = true
        val viewModel = viewModel()
        viewModel.selectMode(NetScanMode.Timer(20))
        viewModel.startNetScan()
        seconds(20)
        assertEquals(SaveState.FAILED, (viewModel.netScan.value as NetScanState.Completed).saveState)

        netScans.failSave = false
        viewModel.retrySave()
        runCurrent()
        assertEquals(SaveState.SAVED, (viewModel.netScan.value as NetScanState.Completed).saveState)
        assertEquals(1, netScans.saved.size)
        viewModel.dismissNetScan()
        assertEquals(NetScanState.Idle, viewModel.netScan.value)
    }

    @Test fun modeIsLockedWhileScanning() = vmTest {
        source.signal.value = lte(rsrp = -90)
        val viewModel = viewModel()
        viewModel.selectMode(NetScanMode.Timer(60))
        viewModel.startNetScan()
        viewModel.selectMode(NetScanMode.Free)
        assertEquals(NetScanMode.Timer(60), viewModel.mode.value)
        viewModel.cancelNetScan()
    }

    @Test fun recordingRunsIndependentlyOfNetScans() = vmTest {
        source.signal.value = lte(rsrp = -90)
        val viewModel = viewModel()
        assertFalse(viewModel.hasActivityInProgress())
        viewModel.toggleRecording()
        seconds(3)
        assertEquals(RecordingState.Recording(3), viewModel.recording.value)
        assertTrue(viewModel.hasActivityInProgress())

        viewModel.startNetScan()
        seconds(2)
        viewModel.cancelNetScan()
        assertEquals(RecordingState.Recording(5), viewModel.recording.value)

        viewModel.toggleRecording()
        seconds(10)
        assertEquals(RecordingState.Idle, viewModel.recording.value)
        assertFalse(viewModel.hasActivityInProgress())
    }

    @Test fun recordingFollowsTheWalkAndSavesItEverySecond() = vmTest {
        val viewModel = viewModel()
        viewModel.toggleRecording()
        runCurrent()
        positions.state.value = tracking(1f, 0f)
        runCurrent()
        positions.state.value = tracking(2f, 0f)
        seconds(1)

        val ready = viewModel.map.value as MapState.Ready
        assertTrue(ready.live)
        assertEquals(listOf(listOf(point(0f, 0f), point(1f, 0f), point(2f, 0f))), ready.map.segments)
        assertEquals(FloorPose(point(2f, 0f), 0f), ready.pose)
        assertEquals(listOf(point(0f, 0f), point(1f, 0f), point(2f, 0f)), recordings.points.map { it.point })

        viewModel.toggleRecording()
        runCurrent()
        assertEquals(setOf(RECORDING_ID), recordings.finished)
    }

    @Test fun lostTrackingSplitsTheWalk() = vmTest {
        val viewModel = viewModel()
        viewModel.toggleRecording()
        runCurrent()
        positions.state.value = PositionState.Lost(TrackingProblem.EXCESSIVE_MOTION)
        runCurrent()
        assertNull((viewModel.map.value as MapState.Ready).pose)
        positions.state.value = tracking(0.1f, 0f)
        seconds(1)

        assertEquals(2, (viewModel.map.value as MapState.Ready).map.segments.size)
        assertEquals(listOf(0, 1), recordings.points.map { it.segment })
    }

    @Test fun aNetScanStartsRecordingAndLandsOnTheMap() = vmTest {
        source.signal.value = lte(rsrp = -70, sinr = 20)
        positions.state.value = tracking(2f, 1f)
        val viewModel = viewModel()
        viewModel.selectMode(NetScanMode.Timer(20))
        viewModel.startNetScan()
        assertTrue(viewModel.recording.value is RecordingState.Recording)
        seconds(20)

        val saved = netScans.saved.single()
        assertEquals(RECORDING_ID, saved.recordingId)
        assertEquals(point(2f, 1f), saved.position)
        seconds(1)
        val ready = viewModel.map.value as MapState.Ready
        assertEquals(listOf(MapMeasurement(point(2f, 1f), SignalLevel.HIGH)), ready.map.measurements)
    }

    @Test fun withoutPositioningTheScanIsSavedOffTheMap() = vmTest {
        positions.state.value = PositionState.Unavailable(PositioningUnavailable.UNSUPPORTED_DEVICE)
        source.signal.value = lte(rsrp = -90)
        val viewModel = viewModel()
        viewModel.selectMode(NetScanMode.Timer(20))
        viewModel.startNetScan()
        assertEquals(RecordingState.Idle, viewModel.recording.value)
        seconds(20)

        val saved = netScans.saved.single()
        assertNull(saved.position)
        assertNull(saved.recordingId)
    }

    @Test fun idleScreenShowsTheLastSavedWalk() = vmTest {
        recordings.latest.value = SavedRecording(
            id = 5,
            startedAt = 42,
            segments = listOf(listOf(point(0f, 0f), point(1f, 0f))),
            measurements = listOf(MapMeasurement(point(1f, 0f), SignalLevel.LOW)),
        )
        val viewModel = viewModel()
        val saved = viewModel.map.value as MapState.Ready
        assertFalse(saved.live)
        assertEquals(42L, saved.recordedAt)
        assertEquals(SignalLevel.LOW, saved.map.measurements.single().level)

        viewModel.toggleRecording()
        runCurrent()
        val live = viewModel.map.value as MapState.Ready
        assertTrue(live.live)
        assertTrue(live.map.measurements.isEmpty())
    }

    @Test fun visitWithoutRecordingHasAnEmptyMap() = vmTest {
        assertEquals(MapState.Empty, viewModel().map.value)
    }

    @Test fun leavingSavesTheEndOfTheWalk() = vmTest {
        val viewModel = viewModel()
        viewModel.toggleRecording()
        runCurrent()
        positions.state.value = tracking(3f, 0f)
        runCurrent()
        viewModel.leave()
        runCurrent()

        assertEquals(RecordingState.Idle, viewModel.recording.value)
        assertEquals(listOf(point(0f, 0f), point(3f, 0f)), recordings.points.map { it.point })
        assertEquals(setOf(RECORDING_ID), recordings.finished)
    }

    @Test fun aFailedRecordingSaveIsFlaggedAndRetried() = vmTest {
        recordings.failPoints = true
        val viewModel = viewModel()
        viewModel.toggleRecording()
        runCurrent()
        assertEquals(RecordingState.Recording(0, saveFailed = true), viewModel.recording.value)

        recordings.failPoints = false
        seconds(1)
        assertEquals(RecordingState.Recording(1, saveFailed = false), viewModel.recording.value)
        assertEquals(1, recordings.points.size)
    }

    @Test fun movingDuringTheScanMarksItsPositionAsApproximate() = vmTest {
        source.signal.value = lte(rsrp = -90)
        val viewModel = viewModel()
        viewModel.selectMode(NetScanMode.Timer(20))
        viewModel.startNetScan()
        seconds(10)
        positions.state.value = tracking(5f, 0f)
        seconds(10)
        assertTrue((viewModel.netScan.value as NetScanState.Completed).movedDuringScan)
    }

    @Test fun standingStillKeepsAnExactPosition() = vmTest {
        source.signal.value = lte(rsrp = -90)
        val viewModel = viewModel()
        viewModel.selectMode(NetScanMode.Timer(20))
        viewModel.startNetScan()
        seconds(20)
        assertFalse((viewModel.netScan.value as NetScanState.Completed).movedDuringScan)
    }

    @Test fun whatTheCameraSeesDrawsTheRoomAndIsSaved() = vmTest {
        val viewModel = viewModel()
        viewModel.toggleRecording()
        runCurrent()
        repeat(2) {
            positions.scene.tryEmit(wallAhead())
            runCurrent()
        }
        seconds(1)

        val map = (viewModel.map.value as MapState.Ready).map
        assertTrue(WALL in map.obstacles)
        assertTrue(GridCell(0, -3) in map.heatmap.area)
        assertTrue(recordings.cells.getValue(WALL) > 0f)
    }

    @Test fun theSavedWalkKeepsItsWalls() = vmTest {
        recordings.latest.value = SavedRecording(
            id = 5,
            startedAt = 42,
            segments = listOf(listOf(point(0f, 0f))),
            measurements = emptyList(),
            cells = mapOf(WALL to 4f, GridCell(0, -3) to -4f),
        )
        val map = (viewModel().map.value as MapState.Ready).map
        assertEquals(setOf(WALL), map.obstacles)
        assertTrue(GridCell(0, -3) in map.heatmap.area)
    }

    @Test fun afterALossTheNewSectionIsRealignedBeforeExtendingTheMap() = vmTest {
        val viewModel = viewModel()
        viewModel.toggleRecording()
        runCurrent()
        repeat(2) {
            positions.scene.tryEmit(wallLine())
            runCurrent()
        }
        positions.state.value = PositionState.Lost(TrackingProblem.INSUFFICIENT_FEATURES)
        runCurrent()
        positions.state.value = tracking(0.1f, 0f)
        runCurrent()
        assertTrue((viewModel.recording.value as RecordingState.Recording).realigning)

        repeat(AlignmentRules.MAX_PENDING_OBSERVATIONS) {
            positions.scene.tryEmit(wallLine())
            runCurrent()
        }
        assertFalse((viewModel.recording.value as RecordingState.Recording).realigning)
    }

    @Test fun onAPlanTheScanWaitsForThePersonToPlaceThemself() = vmTest {
        manualVisit()
        val viewModel = viewModel()
        viewModel.startNetScan()
        assertEquals(NetScanState.Unavailable(NetScanProblem.POSITION_REQUIRED), viewModel.netScan.value)
        assertEquals(RecordingState.Idle, viewModel.recording.value)
    }

    @Test fun onAPlanTheScanIsSavedWhereThePersonStood() = vmTest {
        manualVisit()
        source.signal.value = lte(rsrp = -90)
        val viewModel = viewModel()
        viewModel.placeHere(PlanPoint(0.4f, 0.6f))
        assertEquals(PlanPoint(0.4f, 0.6f), viewModel.manualPosition.value)
        viewModel.selectMode(NetScanMode.Timer(20))
        viewModel.startNetScan()
        seconds(20)

        val saved = netScans.saved.single()
        assertEquals(PlanPoint(0.4f, 0.6f), saved.planPosition)
        assertNull(saved.recordingId)
        assertNull(saved.position)
        // Pas d'ARCore en mode manuel, et la prochaine mesure demandera une nouvelle position.
        assertEquals(RecordingState.Idle, viewModel.recording.value)
        assertNull(viewModel.manualPosition.value)
    }

    @Test fun onAPlanRecordingDoesNotStart() = vmTest {
        manualVisit()
        val viewModel = viewModel()
        viewModel.toggleRecording()
        assertEquals(RecordingState.Idle, viewModel.recording.value)
    }

    @Test fun aPositionOutsideThePlanOrBeforeTheScaleIsIgnored() = vmTest {
        manualVisit(metersPerPixel = null)
        val viewModel = viewModel()
        assertTrue(viewModel.plan.value is PlanState.NeedsScale)
        viewModel.placeHere(PlanPoint(0.5f, 0.5f))
        assertNull(viewModel.manualPosition.value)

        plans.plan.value = plans.plan.value!!.copy(metersPerPixel = 0.025f)
        runCurrent()
        viewModel.placeHere(PlanPoint(1.2f, 0.5f))
        assertNull(viewModel.manualPosition.value)
    }

    @Test fun theScansOnThePlanMakeItsHeatmap() = vmTest {
        manualVisit()
        plans.measurements.value = listOf(PlanMeasurement(PlanPoint(0.1f, 0.1f), SignalLevel.HIGH))
        val ready = viewModel().plan.value as PlanState.Ready
        assertEquals(SignalLevel.HIGH, ready.map.measurements.single().level)
        assertTrue(ready.map.heatmap.values.isNotEmpty())
    }

    @Test fun anImportedPlanStillNeedsItsScale() = vmTest {
        visits.visit.value = visits.visit.value!!.copy(mode = MappingMode.MANUAL)
        val viewModel = viewModel()
        assertEquals(PlanState.Missing, viewModel.plan.value)
        viewModel.importPlan("content://plan")
        runCurrent()
        assertEquals(ImportStatus.IDLE, viewModel.planImport.value)
        assertEquals(FloorPlan(VISIT_ID, "plans/visit.png", 2000, 1000, null), plans.plan.value)
        assertTrue(viewModel.plan.value is PlanState.NeedsScale)
    }

    @Test fun aFailedImportIsReported() = vmTest {
        visits.visit.value = visits.visit.value!!.copy(mode = MappingMode.MANUAL)
        importer = PlanImporter { _, _ -> throw java.io.IOException("not an image") }
        val viewModel = viewModel()
        viewModel.importPlan("content://broken")
        runCurrent()
        assertEquals(ImportStatus.FAILED, viewModel.planImport.value)
        assertNull(plans.plan.value)
    }

    @Test fun theScaleComesFromTwoTappedPointsAndALength() = vmTest {
        manualVisit(metersPerPixel = null)
        val viewModel = viewModel()
        assertFalse(viewModel.setScale(PlanPoint(0.5f, 0.5f), PlanPoint(0.5f, 0.5f), 4f))
        assertTrue(viewModel.setScale(PlanPoint(0.1f, 0.5f), PlanPoint(0.3f, 0.5f), 10f))
        runCurrent()
        assertEquals(0.025f, plans.plan.value!!.metersPerPixel!!, 1e-6f)
        assertTrue(viewModel.plan.value is PlanState.Ready)
    }

    /** Visite en mode manuel avec un plan de 2000 × 1000 pixels. */
    private fun manualVisit(metersPerPixel: Float? = 0.025f) {
        visits.visit.value = visits.visit.value!!.copy(mode = MappingMode.MANUAL)
        plans.plan.value = FloorPlan(VISIT_ID, "plans/visit.png", 2000, 1000, metersPerPixel)
    }

    /** Un mur de 4 m de large, à 3 m devant la caméra, et le sol jusqu'à lui. */
    private fun wallLine() = SceneObservation(
        camera = ScenePoint(0.25f, 1.4f, 0.25f),
        points = (-4..3).flatMap { col ->
            val x = col * 0.5f + 0.25f
            listOf(0.6f, 1f, 1.6f).map { ScenePoint(x, it, -2.75f) } + ScenePoint(x, 0f, -2.25f)
        },
        floorY = 0f,
    )

    /** Un mur à 3 m devant la caméra posée en (0,25 ; 0,25), et le sol jusqu'à lui. */
    private fun wallAhead() = SceneObservation(
        camera = ScenePoint(0.25f, 1.4f, 0.25f),
        points = listOf(0.6f, 1f, 1.6f).map { ScenePoint(0.25f, it, -2.75f) } + ScenePoint(0.25f, 0f, -2.25f),
        floorY = 0f,
    )

    private fun point(x: Float, z: Float) = FloorPoint(x, z)

    private fun tracking(x: Float, z: Float) = PositionState.Tracking(FloorPose(FloorPoint(x, z), 0f))

    private fun lte(rsrp: Int, sinr: Int? = null) =
        SignalSample(inService = true, technology = NetworkTechnology.LTE, rsrpDbm = rsrp, sinrDb = sinr, cellId = 42)

    private class FakeVisitRepository : VisitRepository {
        val visit = MutableStateFlow<Visit?>(Visit(VISIT_ID, "Entrepôt Nord", "", "", 0))
        override fun observeVisits(): Flow<List<Visit>> = visit.map { listOfNotNull(it) }
        override fun observeVisit(id: Long): Flow<Visit?> = visit
        override suspend fun createVisit(building: String, client: String, address: String, mode: MappingMode) = Unit
    }

    private class FakeNetScanRepository : NetScanRepository {
        val saved = mutableListOf<NetScan>()
        var failSave = false
        override suspend fun save(scan: NetScan) {
            if (failSave) throw IllegalStateException("disk full")
            saved += scan
        }
    }

    private class FakeSignalSource : SignalSource {
        val operator = MutableStateFlow<OperatorStatus>(OperatorStatus.Connected("Orange", NetworkTechnology.LTE))
        val signal = MutableStateFlow<SignalSample?>(null)
        var availability = SignalAvailability.AVAILABLE
        var failure: Throwable? = null
        override val isSimulated = true
        override fun observeOperator(): Flow<OperatorStatus> = operator
        override fun availability() = availability
        override fun observeSignal(): Flow<SignalSample> = flow {
            failure?.let { throw it }
            emitAll(signal.filterNotNull())
        }
    }

    private class FakePositionSource : PositionSource {
        val state = MutableStateFlow<PositionState>(PositionState.Tracking(FloorPose(FloorPoint(0f, 0f), 0f)))
        val scene = MutableSharedFlow<SceneObservation>(extraBufferCapacity = 8)
        override val isSimulated = false
        override val position: Flow<PositionState> = state
        override val observations: Flow<SceneObservation> = scene
    }

    private class FakeFloorPlanRepository : FloorPlanRepository {
        val plan = MutableStateFlow<FloorPlan?>(null)
        val measurements = MutableStateFlow<List<PlanMeasurement>>(emptyList())
        override fun observePlan(visitId: Long): Flow<FloorPlan?> = plan
        override suspend fun savePlan(plan: FloorPlan) {
            this.plan.value = plan
        }
        override suspend fun setScale(visitId: Long, metersPerPixel: Float) {
            plan.value = plan.value?.copy(metersPerPixel = metersPerPixel)
        }
        override fun observePlanMeasurements(visitId: Long): Flow<List<PlanMeasurement>> = measurements
    }

    private class FakeRecordingRepository : RecordingRepository {
        val points = mutableListOf<TrackPoint>()
        val finished = mutableSetOf<Long>()
        val cells = mutableMapOf<GridCell, Float>()
        val latest = MutableStateFlow<SavedRecording?>(null)
        var failPoints = false
        private var nextId = RECORDING_ID
        override suspend fun start(visitId: Long, startedAt: Long) = nextId++
        override suspend fun addPoints(recordingId: Long, points: List<TrackPoint>) {
            if (failPoints) throw IllegalStateException("disk full")
            this.points += points
        }
        override suspend fun finish(recordingId: Long, endedAt: Long) {
            finished += recordingId
        }
        override suspend fun saveCells(recordingId: Long, cells: Map<GridCell, Float>) {
            this.cells += cells
        }
        override fun observeLatest(visitId: Long): Flow<SavedRecording?> = latest
    }

    private companion object {
        const val VISIT_ID = 7L
        const val RECORDING_ID = 100L
        val WALL = GridCell(0, -6)
    }
}
