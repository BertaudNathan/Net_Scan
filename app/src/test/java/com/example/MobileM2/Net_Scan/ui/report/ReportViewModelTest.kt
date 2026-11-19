package com.example.MobileM2.Net_Scan.ui.report

import com.example.MobileM2.Net_Scan.domain.FloorPlan
import com.example.MobileM2.Net_Scan.domain.FloorPlanRepository
import com.example.MobileM2.Net_Scan.domain.FloorPoint
import com.example.MobileM2.Net_Scan.domain.GridCell
import com.example.MobileM2.Net_Scan.domain.RecordingRepository
import com.example.MobileM2.Net_Scan.domain.SavedRecording
import com.example.MobileM2.Net_Scan.domain.TrackPoint
import com.example.MobileM2.Net_Scan.domain.MappingMode
import com.example.MobileM2.Net_Scan.domain.NetScan
import com.example.MobileM2.Net_Scan.domain.NetScanMode
import com.example.MobileM2.Net_Scan.domain.NetScanRepository
import com.example.MobileM2.Net_Scan.domain.NetScanSummary
import com.example.MobileM2.Net_Scan.domain.NetworkTechnology
import com.example.MobileM2.Net_Scan.domain.PlanMeasurement
import com.example.MobileM2.Net_Scan.domain.PlanPoint
import com.example.MobileM2.Net_Scan.domain.PlanWall
import com.example.MobileM2.Net_Scan.domain.ReportGenerator
import com.example.MobileM2.Net_Scan.domain.SignalLevel
import com.example.MobileM2.Net_Scan.domain.Visit
import com.example.MobileM2.Net_Scan.domain.VisitReport
import com.example.MobileM2.Net_Scan.domain.VisitRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val visits = FakeVisitRepository()
    private val scans = FakeNetScanRepository()
    private val plans = FakeFloorPlanRepository()
    private val recordings = FakeRecordingRepository()
    private val generator = FakeReportGenerator()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        Dispatchers.resetMain()
        generator.file.delete()
    }

    private fun TestScope.viewModel() =
        ReportViewModel(VISIT_ID, visits, scans, plans, recordings, generator, clock = { 5_000L }, io = dispatcher)
            .also { runCurrent() }

    @Test fun aManualVisitIncludesItsImportedPlan() = runTest(dispatcher) {
        visits.visit.value = visits.visit.value?.copy(mode = MappingMode.MANUAL)
        val plan = FloorPlan(VISIT_ID, "plan.png", 200, 100, 0.1f)
        plans.plan.value = plan
        recordings.latest.value = RECORDING
        val state = viewModel().state.value as ReportState.Ready
        assertEquals(plan, state.report.plan)
        assertNull(state.report.recording)
    }

    @Test fun aManualVisitIncludesTheWallsDrawnOnItsPlan() = runTest(dispatcher) {
        visits.visit.value = visits.visit.value?.copy(mode = MappingMode.MANUAL)
        plans.plan.value = FloorPlan(VISIT_ID, "plan.png", 200, 100, 0.1f)
        val wall = PlanWall(1, PlanPoint(0.5f, 0f), PlanPoint(0.5f, 1f))
        plans.walls.value = listOf(wall)
        val state = viewModel().state.value as ReportState.Ready
        assertEquals(listOf(wall), state.report.walls)
        assertEquals(listOf(wall), generator.generated.single().walls)
    }

    @Test fun anArCoreVisitIncludesItsLatestRecording() = runTest(dispatcher) {
        plans.plan.value = FloorPlan(VISIT_ID, "plan.png", 200, 100, 0.1f)
        recordings.latest.value = RECORDING
        val state = viewModel().state.value as ReportState.Ready
        assertEquals(RECORDING, state.report.recording)
        assertNull(state.report.plan)
    }

    @Test fun buildsTheReportFromTheVisitAndItsScans() = runTest(dispatcher) {
        scans.scans.value = listOf(scan(2, 200), scan(1, 100))
        val state = viewModel().state.value as ReportState.Ready
        assertEquals("Entrepôt Nord", state.report.visit.building)
        assertEquals(listOf(1L, 2L), state.report.points.map { it.scan.id })
        assertEquals(5_000L, state.report.generatedAt)
        assertEquals(generator.file, state.file)
        assertEquals(state.report, generator.generated.single())
    }

    @Test fun aVisitWithoutScanStillGivesAReport() = runTest(dispatcher) {
        val state = viewModel().state.value as ReportState.Ready
        assertEquals(true, state.report.isEmpty)
    }

    @Test fun reportsAMissingVisit() = runTest(dispatcher) {
        visits.visit.value = null
        assertEquals(ReportState.Missing, viewModel().state.value)
    }

    @Test fun generationErrorCanBeRetried() = runTest(dispatcher) {
        generator.fail = true
        val viewModel = viewModel()
        assertEquals(ReportState.Error, viewModel.state.value)
        generator.fail = false
        viewModel.retry()
        runCurrent()
        assertEquals(ReportState.Ready::class, viewModel.state.value::class)
    }

    @Test fun exportCopiesThePdfAndConfirms() = runTest(dispatcher) {
        val viewModel = viewModel()
        val output = ByteArrayOutputStream()
        viewModel.exportTo { output }
        runCurrent()
        assertArrayEquals(PDF_BYTES, output.toByteArray())
        assertEquals(ReportMessage.SAVED, viewModel.message.value)
        assertFalse(viewModel.exporting.value)
        viewModel.messageShown()
        assertEquals(null, viewModel.message.value)
    }

    @Test fun exportFailureIsReported() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.exportTo { throw IOException("full") }
        runCurrent()
        assertEquals(ReportMessage.SAVE_FAILED, viewModel.message.value)
        viewModel.exportTo { null }
        runCurrent()
        assertEquals(ReportMessage.SAVE_FAILED, viewModel.message.value)
    }

    private fun scan(id: Long, startedAt: Long) = NetScan(
        visitId = VISIT_ID,
        startedAt = startedAt,
        durationSeconds = 30,
        mode = NetScanMode.Timer(30),
        operatorName = "Orange",
        summary = NetScanSummary(SignalLevel.MEDIUM, NetworkTechnology.LTE, -90.0, 5.0, 10, 3, 1L),
        id = id,
    )

    private class FakeVisitRepository : VisitRepository {
        val visit = MutableStateFlow<Visit?>(Visit(VISIT_ID, "Entrepôt Nord", "Acme", "", 0))
        override fun observeVisits(): Flow<List<Visit>> = visit.map { listOfNotNull(it) }
        override fun observeVisit(id: Long): Flow<Visit?> = visit
        override suspend fun createVisit(building: String, client: String, address: String, mode: MappingMode) = Unit
    }

    private class FakeNetScanRepository : NetScanRepository {
        val scans = MutableStateFlow<List<NetScan>>(emptyList())
        override fun observeForVisit(visitId: Long): Flow<List<NetScan>> = scans
        override suspend fun save(scan: NetScan) = Unit
    }

    private class FakeFloorPlanRepository : FloorPlanRepository {
        val plan = MutableStateFlow<FloorPlan?>(null)
        override fun observePlan(visitId: Long): Flow<FloorPlan?> = plan
        override suspend fun savePlan(plan: FloorPlan) = Unit
        override suspend fun setScale(visitId: Long, metersPerPixel: Float) = Unit
        override fun observePlanMeasurements(visitId: Long): Flow<List<PlanMeasurement>> = MutableStateFlow(emptyList())
        val walls = MutableStateFlow<List<PlanWall>>(emptyList())
        override fun observeWalls(visitId: Long): Flow<List<PlanWall>> = walls
        override suspend fun addWall(visitId: Long, start: PlanPoint, end: PlanPoint) = 0L
        override suspend fun deleteWall(id: Long) = Unit
    }

    private class FakeRecordingRepository : RecordingRepository {
        val latest = MutableStateFlow<SavedRecording?>(null)
        override suspend fun start(visitId: Long, startedAt: Long) = 1L
        override suspend fun addPoints(recordingId: Long, points: List<TrackPoint>) = Unit
        override suspend fun finish(recordingId: Long, endedAt: Long) = Unit
        override suspend fun saveCells(recordingId: Long, cells: Map<GridCell, Float>) = Unit
        override fun observeLatest(visitId: Long): Flow<SavedRecording?> = latest
    }

    private class FakeReportGenerator : ReportGenerator {
        val file: File = File.createTempFile("rapport", ".pdf").apply { writeBytes(PDF_BYTES) }
        val generated = mutableListOf<VisitReport>()
        var fail = false
        override suspend fun generate(report: VisitReport): File {
            if (fail) throw IOException("disk full")
            generated += report
            return file
        }
    }

    private companion object {
        const val VISIT_ID = 7L
        val RECORDING = SavedRecording(3, 0, listOf(listOf(FloorPoint(0f, 0f), FloorPoint(2f, 0f))), emptyList())
        val PDF_BYTES = "%PDF-1.4 test".toByteArray()
    }
}
