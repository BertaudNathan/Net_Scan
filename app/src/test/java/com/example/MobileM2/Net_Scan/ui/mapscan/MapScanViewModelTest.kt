package com.example.MobileM2.Net_Scan.ui.mapscan

import com.example.MobileM2.Net_Scan.domain.NetScan
import com.example.MobileM2.Net_Scan.domain.NetScanMode
import com.example.MobileM2.Net_Scan.domain.NetScanRepository
import com.example.MobileM2.Net_Scan.domain.NetworkTechnology
import com.example.MobileM2.Net_Scan.domain.OperatorStatus
import com.example.MobileM2.Net_Scan.domain.SignalAvailability
import com.example.MobileM2.Net_Scan.domain.SignalLevel
import com.example.MobileM2.Net_Scan.domain.SignalSample
import com.example.MobileM2.Net_Scan.domain.SignalSource
import com.example.MobileM2.Net_Scan.domain.Visit
import com.example.MobileM2.Net_Scan.domain.VisitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
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

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun TestScope.viewModel(): MapScanViewModel =
        MapScanViewModel(VISIT_ID, visits, netScans, source, clock = { 1_000L }).also { runCurrent() }

    private fun TestScope.seconds(count: Int) {
        advanceTimeBy(count * 1_000L)
        runCurrent()
    }

    private fun running(viewModel: MapScanViewModel) = viewModel.netScan.value as NetScanState.Running

    @Test fun showsTheVisitAndTheCurrentOperator() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertEquals("Entrepôt Nord", (viewModel.visit.value as VisitState.Loaded).visit.building)
        assertEquals(OperatorStatus.Connected("Orange", NetworkTechnology.LTE), viewModel.operator.value)
        assertTrue(viewModel.isSimulated)
    }

    @Test fun reportsAMissingVisit() = runTest(dispatcher) {
        visits.visit.value = null
        assertEquals(VisitState.Missing, viewModel().visit.value)
    }

    @Test fun historyObservesOnlyThisVisitAndUpdatesAfterSaving() = runTest(dispatcher) {
        source.signal.value = lte(rsrp = -90)
        val viewModel = viewModel()
        assertEquals(ScanHistoryState.Empty, viewModel.history.value)
        viewModel.selectMode(NetScanMode.Timer(20))
        viewModel.startNetScan()
        seconds(20)
        val history = viewModel.history.value as ScanHistoryState.Success
        assertEquals(1, history.scans.size)
        assertEquals(VISIT_ID, history.scans.single().visitId)
        assertEquals(SignalLevel.MEDIUM, history.scans.single().summary.level)
    }

    @Test fun historyFailureCanBeRetried() = runTest(dispatcher) {
        netScans.failRead = true
        val viewModel = viewModel()
        assertEquals(ScanHistoryState.Error, viewModel.history.value)
        netScans.failRead = false
        viewModel.retryHistory()
        runCurrent()
        assertEquals(ScanHistoryState.Empty, viewModel.history.value)
    }

    @Test fun timerCountsDownThenSavesTheMedian() = runTest(dispatcher) {
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

    @Test fun freeModeCanOnlyFinishAfterTheMinimumDuration() = runTest(dispatcher) {
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

    @Test fun cancellingDiscardsTheScan() = runTest(dispatcher) {
        source.signal.value = lte(rsrp = -90)
        val viewModel = viewModel()
        viewModel.startNetScan()
        seconds(5)
        viewModel.cancelNetScan()
        seconds(60)
        assertEquals(NetScanState.Idle, viewModel.netScan.value)
        assertTrue(netScans.saved.isEmpty())
    }

    @Test fun noServiceIsSavedAsAResult() = runTest(dispatcher) {
        source.signal.value = SignalSample(inService = false)
        val viewModel = viewModel()
        viewModel.selectMode(NetScanMode.Timer(20))
        viewModel.startNetScan()
        seconds(20)
        assertEquals(SignalLevel.NONE, netScans.saved.single().summary.level)
    }

    @Test fun systemConditionsAreExplainedInsteadOfMeasuring() = runTest(dispatcher) {
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

    @Test fun deniedPermissionsAreExplained() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onPermissionsDenied()
        assertEquals(NetScanState.Unavailable(NetScanProblem.PERMISSION_DENIED), viewModel.netScan.value)
    }

    @Test fun permissionRevokedDuringTheScanStopsIt() = runTest(dispatcher) {
        source.failure = SecurityException("revoked")
        val viewModel = viewModel()
        viewModel.startNetScan()
        seconds(1)
        assertEquals(NetScanState.Unavailable(NetScanProblem.PERMISSION_DENIED), viewModel.netScan.value)
        seconds(60)
        assertTrue(netScans.saved.isEmpty())
    }

    @Test fun silentModemGivesNoData() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.selectMode(NetScanMode.Timer(20))
        viewModel.startNetScan()
        seconds(20)
        assertEquals(NetScanState.Unavailable(NetScanProblem.NO_DATA), viewModel.netScan.value)
    }

    @Test fun failedSaveCanBeRetried() = runTest(dispatcher) {
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

    @Test fun modeIsLockedWhileScanning() = runTest(dispatcher) {
        source.signal.value = lte(rsrp = -90)
        val viewModel = viewModel()
        viewModel.selectMode(NetScanMode.Timer(60))
        viewModel.startNetScan()
        viewModel.selectMode(NetScanMode.Free)
        assertEquals(NetScanMode.Timer(60), viewModel.mode.value)
        viewModel.cancelNetScan()
    }

    @Test fun recordingRunsIndependentlyOfNetScans() = runTest(dispatcher) {
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

    private fun lte(rsrp: Int, sinr: Int? = null) =
        SignalSample(inService = true, technology = NetworkTechnology.LTE, rsrpDbm = rsrp, sinrDb = sinr, cellId = 42)

    private class FakeVisitRepository : VisitRepository {
        val visit = MutableStateFlow<Visit?>(Visit(VISIT_ID, "Entrepôt Nord", "", "", 0))
        override fun observeVisits(): Flow<List<Visit>> = visit.map { listOfNotNull(it) }
        override fun observeVisit(id: Long): Flow<Visit?> = visit
        override suspend fun createVisit(building: String, client: String, address: String) = Unit
    }

    private class FakeNetScanRepository : NetScanRepository {
        val saved = mutableListOf<NetScan>()
        val updates = MutableStateFlow<List<NetScan>>(emptyList())
        var failSave = false
        var failRead = false
        override fun observeForVisit(visitId: Long): Flow<List<NetScan>> = flow {
            if (failRead) throw IllegalStateException("read failed")
            emitAll(updates.map { scans -> scans.filter { it.visitId == visitId } })
        }
        override suspend fun save(scan: NetScan) {
            if (failSave) throw IllegalStateException("disk full")
            saved += scan
            updates.value = saved.toList()
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

    private companion object { const val VISIT_ID = 7L }
}
