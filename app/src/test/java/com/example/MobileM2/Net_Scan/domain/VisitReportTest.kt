package com.example.MobileM2.Net_Scan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitReportTest {
    private val visit = Visit(3, "Entrepôt Nord", "Acme", "1 rue de la Paix", 100)

    private fun scan(id: Long, startedAt: Long, level: SignalLevel, operator: String? = "Orange") = NetScan(
        visitId = 3,
        startedAt = startedAt,
        durationSeconds = 30,
        mode = NetScanMode.Timer(30),
        operatorName = operator,
        summary = NetScanSummary(level, NetworkTechnology.LTE, -90.0, 5.0, 12, 3, 42L),
        id = id,
    )

    @Test fun pointsAreNumberedInChronologicalOrder() {
        val report = buildVisitReport(visit, listOf(scan(2, 200, SignalLevel.HIGH), scan(1, 100, SignalLevel.LOW)), 999)
        assertEquals(listOf("P1", "P2"), report.points.map { it.label })
        assertEquals(listOf(1L, 2L), report.points.map { it.scan.id })
    }

    @Test fun simultaneousScansKeepTheirSavingOrder() {
        val report = buildVisitReport(visit, listOf(scan(5, 100, SignalLevel.HIGH), scan(4, 100, SignalLevel.LOW)), 999)
        assertEquals(listOf(4L, 5L), report.points.map { it.scan.id })
    }

    @Test fun countsEveryLevelIncludingMissingOnes() {
        val report = buildVisitReport(
            visit,
            listOf(scan(1, 1, SignalLevel.HIGH), scan(2, 2, SignalLevel.HIGH), scan(3, 3, SignalLevel.NONE)),
            999,
        )
        assertEquals(
            mapOf(SignalLevel.NONE to 1, SignalLevel.LOW to 0, SignalLevel.MEDIUM to 0, SignalLevel.HIGH to 2),
            report.levelCounts,
        )
    }

    @Test fun weakPointsAreLowAndNone() {
        val report = buildVisitReport(
            visit,
            listOf(scan(1, 1, SignalLevel.LOW), scan(2, 2, SignalLevel.MEDIUM), scan(3, 3, SignalLevel.NONE)),
            999,
        )
        assertEquals(listOf("P1", "P3"), report.weakPoints.map { it.label })
    }

    @Test fun operatorsAreListedOnceWithoutBlanks() {
        val report = buildVisitReport(
            visit,
            listOf(scan(1, 1, SignalLevel.LOW, "SFR"), scan(2, 2, SignalLevel.LOW, null), scan(3, 3, SignalLevel.LOW, " "),
                scan(4, 4, SignalLevel.LOW, "Orange"), scan(5, 5, SignalLevel.LOW, "SFR")),
            999,
        )
        assertEquals(listOf("SFR", "Orange"), report.operators)
    }

    @Test fun aVisitWithoutScanGivesAnEmptyReport() {
        assertTrue(buildVisitReport(visit, emptyList(), 999).isEmpty)
    }

    @Test fun fileNameIsSafeAndReadable() {
        assertEquals("rapport-entrepot-nord-2026-11-19.pdf", reportFileName("Entrepôt  Nord !", "2026-11-19"))
        assertEquals("rapport-2026-11-19.pdf", reportFileName("***", "2026-11-19"))
        assertTrue(reportFileName("a".repeat(100), "2026-11-19").length < 70)
    }
}
