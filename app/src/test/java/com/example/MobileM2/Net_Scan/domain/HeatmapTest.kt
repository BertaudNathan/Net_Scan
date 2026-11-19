package com.example.MobileM2.Net_Scan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatmapTest {
    private val grid = FloorGrid(cellSize = 0.5f)

    @Test fun cellsAreCutByFloorDivisionAroundTheOrigin() {
        assertEquals(GridCell(0, -1), grid.cellOf(FloorPoint(0.1f, -0.1f)))
        assertEquals(GridCell(-2, 2), grid.cellOf(FloorPoint(-0.6f, 1.2f)))
        assertEquals(FloorPoint(0.25f, -0.25f), grid.centerOf(GridCell(0, -1)))
    }

    @Test fun standingStillOpensADiscOfOneMetre() {
        val area = accessibleArea(listOf(listOf(at(0, 0))), grid, radius = 1f)
        assertEquals(13, area.size)
        assertTrue(GridCell(2, 0) in area)
        assertTrue(GridCell(1, 1) in area)
        assertFalse(GridCell(2, 1) in area)
    }

    @Test fun walkingOpensACorridor() {
        val area = accessibleArea(listOf(walk(0, 10)), grid, radius = 1f)
        (0..10).forEach { assertTrue("col $it", GridCell(it, 0) in area) }
        assertTrue(GridCell(5, 2) in area)
        assertFalse(GridCell(5, 3) in area)
    }

    @Test fun trackingJumpsAreNotBridged() {
        val area = accessibleArea(listOf(listOf(at(0, 0), at(20, 0))), grid, radius = 1f, maxJoin = 3f)
        assertTrue(GridCell(20, 0) in area)
        assertFalse(GridCell(10, 0) in area)
    }

    @Test fun separateSegmentsAreNotBridged() {
        val start = FloorPoint(0f, 0.25f)
        val end = FloorPoint(3f, 0.25f)
        val joined = accessibleArea(listOf(listOf(start, end)), grid, radius = 1f)
        val split = accessibleArea(listOf(listOf(start), listOf(end)), grid, radius = 1f)
        assertTrue(GridCell(2, 0) in joined)
        assertFalse(GridCell(2, 0) in split)
    }

    @Test fun measuredCellsKeepTheirLevelAndOthersAreInterpolated() {
        val area = accessibleArea(listOf(walk(0, 12)), grid, radius = 1f)
        val heatmap = buildHeatmap(
            area,
            listOf(MapMeasurement(at(0, 0), SignalLevel.HIGH), MapMeasurement(at(8, 0), SignalLevel.LOW)),
            grid,
        )
        assertEquals(HeatCell(SignalLevel.HIGH, measured = true, confidence = 1f), heatmap.valueAt(GridCell(0, 0)))
        val halfway = heatmap.valueAt(GridCell(4, 0))!!
        assertEquals(SignalLevel.MEDIUM, halfway.level)
        assertFalse(halfway.measured)
        assertEquals(SignalLevel.HIGH, heatmap.valueAt(GridCell(1, 0))!!.level)
        assertTrue(heatmap.valueAt(GridCell(1, 0))!!.confidence > halfway.confidence)
    }

    @Test fun cellsFarFromEveryMeasurementStayUnknown() {
        val area = accessibleArea(listOf(walk(0, 20)), grid, radius = 1f)
        val heatmap = buildHeatmap(area, listOf(MapMeasurement(at(0, 0), SignalLevel.HIGH)), grid, maxRadius = 6f)
        assertNotNull(heatmap.valueAt(GridCell(11, 0)))
        assertTrue(GridCell(13, 0) in heatmap.area)
        assertNull(heatmap.valueAt(GridCell(13, 0)))
    }

    @Test fun distanceIsMeasuredAlongTheWalkNotThroughWalls() {
        // Un U : deux couloirs parallèles à 2 m l'un de l'autre, reliés tout en bas.
        val area = buildSet {
            (0..12).forEach { add(GridCell(0, it)); add(GridCell(4, it)) }
            (0..4).forEach { add(GridCell(it, 12)) }
        }
        val heatmap = buildHeatmap(area, listOf(MapMeasurement(at(0, 0), SignalLevel.LOW)), grid, maxRadius = 6f)
        assertNotNull(heatmap.valueAt(GridCell(0, 4)))
        assertNull(heatmap.valueAt(GridCell(4, 0)))
    }

    @Test fun nothingIsInventedOutsideTheWalkedArea() {
        val area = accessibleArea(listOf(listOf(at(0, 0))), grid, radius = 1f)
        val heatmap = buildHeatmap(area, listOf(MapMeasurement(at(0, 0), SignalLevel.HIGH)), grid)
        assertFalse(GridCell(3, 3) in heatmap.area)
        assertNull(heatmap.valueAt(GridCell(3, 3)))
        assertEquals(area, heatmap.values.keys)
    }

    @Test fun aMeasurementOutsideTheWalkedAreaStillCounts() {
        val heatmap = buildHeatmap(emptySet(), listOf(MapMeasurement(at(7, 7), SignalLevel.MEDIUM)), grid)
        assertEquals(SignalLevel.MEDIUM, heatmap.valueAt(GridCell(7, 7))!!.level)
    }

    @Test fun repeatedMeasurementsAreAveragedPessimistically() {
        val heatmap = buildHeatmap(
            emptySet(),
            listOf(MapMeasurement(at(0, 0), SignalLevel.MEDIUM), MapMeasurement(at(0, 0), SignalLevel.LOW)),
            grid,
        )
        assertEquals(HeatCell(SignalLevel.LOW, measured = true, confidence = 1f), heatmap.valueAt(GridCell(0, 0)))
    }

    @Test fun noServiceWeighsInTheEstimate() {
        val area = accessibleArea(listOf(walk(0, 8)), grid, radius = 1f)
        val heatmap = buildHeatmap(
            area,
            listOf(MapMeasurement(at(0, 0), SignalLevel.NONE), MapMeasurement(at(8, 0), SignalLevel.HIGH)),
            grid,
        )
        assertEquals(SignalLevel.LOW, heatmap.valueAt(GridCell(4, 0))!!.level)
    }

    @Test fun floorMapBundlesTheWalkTheMeasurementsAndTheHeatmap() {
        val segments = listOf(listOf(at(0, 0), at(4, 0)))
        val measurements = listOf(MapMeasurement(at(4, 0), SignalLevel.HIGH))
        val map = FloorMap.build(segments, measurements, grid)
        assertEquals(segments, map.segments)
        assertEquals(measurements, map.measurements)
        assertEquals(SignalLevel.HIGH, map.heatmap.valueAt(GridCell(0, 0))!!.level)
    }

    /** Marche le long de la rangée 0, un point par case. */
    private fun walk(fromCol: Int, toCol: Int) = (fromCol..toCol).map { at(it, 0) }

    /** Centre de la case (col, row). */
    private fun at(col: Int, row: Int) = grid.centerOf(GridCell(col, row))
}
