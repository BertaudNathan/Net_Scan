package com.example.MobileM2.Net_Scan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitReportTest {
    private val visit = Visit(3, "Entrepôt Nord", "Acme", "1 rue de la Paix", 100)
    private val plan = FloorPlan(3, "plan.png", widthPx = 200, heightPx = 100, metersPerPixel = 0.1f)

    private fun scan(
        id: Long,
        startedAt: Long,
        level: SignalLevel,
        operator: String? = "Orange",
        planPosition: PlanPoint? = null,
    ) = NetScan(
        visitId = 3,
        startedAt = startedAt,
        durationSeconds = 30,
        mode = NetScanMode.Timer(30),
        operatorName = operator,
        summary = NetScanSummary(level, NetworkTechnology.LTE, -90.0, 5.0, 12, 3, 42L),
        planPosition = planPosition,
        id = id,
    )

    @Test fun withoutPlanNothingIsPlaced() {
        val report = buildVisitReport(visit, listOf(scan(1, 1, SignalLevel.HIGH, planPosition = PlanPoint(0.5f, 0.5f))), 999)
        assertTrue(report.placedPoints.isEmpty())
        assertNull(report.planMap)
    }

    @Test fun pointsOnThePlanKeepTheirNumberAndFeedTheHeatmap() {
        val report = buildVisitReport(
            visit,
            listOf(
                scan(1, 1, SignalLevel.HIGH, planPosition = PlanPoint(0.25f, 0.5f)),
                scan(2, 2, SignalLevel.LOW),
                scan(3, 3, SignalLevel.NONE, planPosition = PlanPoint(0.75f, 0.5f)),
                scan(4, 4, SignalLevel.LOW, planPosition = PlanPoint(1.5f, 0.5f)),
            ),
            999,
            plan,
        )
        assertEquals(listOf("P1", "P3"), report.placedPoints.map { it.label })
        assertEquals(listOf("P2", "P4"), report.unplacedPoints.map { it.label })
        val map = checkNotNull(report.planMap)
        assertEquals(listOf(SignalLevel.HIGH, SignalLevel.NONE), map.measurements.map { it.level })
        assertTrue(map.heatmap.values.values.any { it.measured && it.level == SignalLevel.HIGH })
        assertTrue(map.heatmap.values.values.any { !it.measured })
    }

    @Test fun theWallsDrawnOnThePlanStopItsHeatmapLikeOnScreen() {
        // Plan de 20 × 10 m ; mur vertical à 10,25 m sur toute la hauteur, scan à 9 m.
        val wall = PlanWall(1, PlanPoint(0.5125f, 0f), PlanPoint(0.5125f, 1f))
        val scans = listOf(scan(1, 1, SignalLevel.HIGH, planPosition = PlanPoint(0.45f, 0.5f)))
        val beyond = GridCell(22, 10)
        assertTrue(checkNotNull(buildVisitReport(visit, scans, 999, plan).planMap).heatmap.valueAt(beyond) != null)

        val walled = buildVisitReport(visit, scans, 999, plan, walls = listOf(wall))
        assertEquals(listOf(wall), walled.walls)
        assertNull(checkNotNull(walled.planMap).heatmap.valueAt(beyond))
    }

    @Test fun scansOfTheLatestRecordingArePlacedOnItsMap() {
        val recording = SavedRecording(9, 0, listOf(listOf(FloorPoint(0f, 0f), FloorPoint(4f, 0f))), emptyList())
        val report = buildVisitReport(
            visit,
            listOf(
                scan(1, 1, SignalLevel.HIGH).copy(recordingId = 9, position = FloorPoint(0f, 0f)),
                scan(2, 2, SignalLevel.LOW).copy(recordingId = 8, position = FloorPoint(1f, 0f)),
                scan(3, 3, SignalLevel.NONE).copy(recordingId = 9),
                scan(4, 4, SignalLevel.LOW).copy(recordingId = 9, position = FloorPoint(4f, 0f)),
            ),
            999,
            recording = recording,
        )
        assertEquals(listOf("P1", "P4"), report.placedPoints.map { it.label })
        assertEquals(listOf("P2", "P3"), report.unplacedPoints.map { it.label })
        val map = checkNotNull(report.recordingMap)
        assertEquals(recording.segments, map.segments)
        assertEquals(listOf(SignalLevel.HIGH, SignalLevel.LOW), map.measurements.map { it.level })
        assertTrue(map.heatmap.values.values.any { !it.measured })
    }

    @Test fun withoutRecordingThereIsNoArCoreMap() {
        assertNull(buildVisitReport(visit, listOf(scan(1, 1, SignalLevel.HIGH)), 999).recordingMap)
    }

    @Test fun aPlanWithoutScaleHasNoHeatmapButKeepsItsPoints() {
        val report = buildVisitReport(
            visit,
            listOf(scan(1, 1, SignalLevel.HIGH, planPosition = PlanPoint(0.5f, 0.5f))),
            999,
            plan.copy(metersPerPixel = null),
        )
        assertEquals(listOf("P1"), report.placedPoints.map { it.label })
        assertNull(report.planMap)
    }

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
