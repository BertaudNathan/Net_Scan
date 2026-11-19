package com.example.MobileM2.Net_Scan.ui.report

import com.example.MobileM2.Net_Scan.domain.NetScan
import com.example.MobileM2.Net_Scan.domain.NetScanMode
import com.example.MobileM2.Net_Scan.domain.NetScanRepository
import com.example.MobileM2.Net_Scan.domain.NetScanSummary
import com.example.MobileM2.Net_Scan.domain.NetworkTechnology
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val visits = FakeVisitRepository()
    private val scans = FakeNetScanRepository()
    private val generator = FakeReportGenerator()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        Dispatchers.resetMain()
        generator.file.delete()
    }

    private fun TestScope.viewModel() =
        ReportViewModel(VISIT_ID, visits, scans, generator, clock = { 5_000L }, io = dispatcher).also { runCurrent() }

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
        override suspend fun createVisit(building: String, client: String, address: String) = Unit
    }

    private class FakeNetScanRepository : NetScanRepository {
        val scans = MutableStateFlow<List<NetScan>>(emptyList())
        override fun observeForVisit(visitId: Long): Flow<List<NetScan>> = scans
        override suspend fun save(scan: NetScan) = Unit
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
        val PDF_BYTES = "%PDF-1.4 test".toByteArray()
    }
}
