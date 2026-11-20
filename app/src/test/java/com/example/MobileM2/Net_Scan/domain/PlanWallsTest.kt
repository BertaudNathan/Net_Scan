package com.example.MobileM2.Net_Scan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanWallsTest {
    /** Plan de 2000 × 1000 pixels, 1 cm par pixel : 20 × 10 m. */
    private val plan = FloorPlan(visitId = 1, imagePath = "plan.png", widthPx = 2000, heightPx = 1000, metersPerPixel = 0.01f)
    private val grid = FloorGrid(0.5f)
    private val existing = listOf(PlanWall(1, PlanPoint(0.1f, 0.2f), PlanPoint(0.5f, 0.2f)))

    @Test fun aWallEndSnapsToANearbyExistingEnd() {
        // 14 pixels de l'extrémité (1000 ; 200) : le mur se raccorde.
        val snapped = snapWallPoint(plan, PlanPoint(0.505f, 0.21f), from = null, walls = existing, snapPixels = 20f)
        assertEquals(PlanPoint(0.5f, 0.2f), snapped)
    }

    @Test fun anAlmostHorizontalWallIsStraightened() {
        // 10 pixels de dénivelé sur 600 : moins d'un degré.
        val snapped = snapWallPoint(plan, PlanPoint(0.4f, 0.51f), from = PlanPoint(0.1f, 0.5f), walls = emptyList(), snapPixels = 20f)
        assertEquals(0.4f, snapped.u, 1e-6f)
        assertEquals(0.5f, snapped.v, 1e-6f)
    }

    @Test fun anAlmostVerticalWallIsStraightened() {
        val snapped = snapWallPoint(plan, PlanPoint(0.205f, 0.9f), from = PlanPoint(0.2f, 0.1f), walls = emptyList(), snapPixels = 20f)
        assertEquals(0.2f, snapped.u, 1e-6f)
        assertEquals(0.9f, snapped.v, 1e-6f)
    }

    @Test fun aDiagonalWallStaysDiagonal() {
        val raw = PlanPoint(0.3f, 0.5f)
        assertEquals(raw, snapWallPoint(plan, raw, from = PlanPoint(0.1f, 0.1f), walls = emptyList(), snapPixels = 20f))
    }

    @Test fun joiningAnExistingEndWinsOverStraightening() {
        val snapped = snapWallPoint(plan, PlanPoint(0.498f, 0.205f), from = PlanPoint(0.5f, 0.8f), walls = existing, snapPixels = 20f)
        assertEquals(PlanPoint(0.5f, 0.2f), snapped)
    }

    @Test fun aWallEndMeetsAnExistingWallAlongItsLength() {
        // Cloison en biais qui s'arrête 10 pixels avant le mur du haut : elle le rejoint, sans trou.
        val snapped = snapWallPoint(plan, PlanPoint(0.3f, 0.21f), from = PlanPoint(0.2f, 0.6f), walls = existing, snapPixels = 20f)
        assertEquals(0.3f, snapped.u, 1e-6f)
        assertEquals(0.2f, snapped.v, 1e-6f)
    }

    @Test fun aStraightenedWallStillMeetsTheWallItRunsInto() {
        val snapped = snapWallPoint(plan, PlanPoint(0.302f, 0.215f), from = PlanPoint(0.3f, 0.8f), walls = existing, snapPixels = 20f)
        assertEquals(0.3f, snapped.u, 1e-6f)
        assertEquals(0.2f, snapped.v, 1e-6f)
    }

    @Test fun tappingNearAWallSelectsIt() {
        val walls = existing + PlanWall(2, PlanPoint(0.1f, 0.6f), PlanPoint(0.9f, 0.6f))
        assertEquals(1L, wallNear(plan, PlanPoint(0.3f, 0.21f), walls, tolerancePixels = 20f)?.id)
        assertEquals(2L, wallNear(plan, PlanPoint(0.5f, 0.59f), walls, tolerancePixels = 20f)?.id)
        assertNull(wallNear(plan, PlanPoint(0.3f, 0.4f), walls, tolerancePixels = 20f))
        // Dans le prolongement, mais 40 pixels après l'extrémité : ce n'est plus le mur.
        assertNull(wallNear(plan, PlanPoint(0.52f, 0.2f), walls, tolerancePixels = 20f))
    }

    @Test fun tappingNearASavedMeasurementSelectsTheClosest() {
        val measurements = listOf(
            PlanMeasurement(PlanPoint(0.3f, 0.3f), SignalLevel.HIGH, scanId = 1),
            PlanMeasurement(PlanPoint(0.31f, 0.3f), SignalLevel.LOW, scanId = 2),
            // Pas encore en base : on ne peut pas la choisir.
            PlanMeasurement(PlanPoint(0.6f, 0.6f), SignalLevel.LOW),
        )
        assertEquals(2L, measurementNear(plan, PlanPoint(0.308f, 0.3f), measurements, tolerancePixels = 20f)?.scanId)
        assertNull(measurementNear(plan, PlanPoint(0.6f, 0.6f), measurements, tolerancePixels = 20f))
        assertNull(measurementNear(plan, PlanPoint(0.3f, 0.4f), measurements, tolerancePixels = 20f))
    }

    @Test fun wallsBecomeGridCellsOnceScaled() {
        // Mur horizontal de 2 m, de (2,1 m ; 1,1 m) à (4,1 m ; 1,1 m).
        val wall = PlanWall(1, PlanPoint(0.105f, 0.11f), PlanPoint(0.205f, 0.11f))
        val cells = wallCells(plan, listOf(wall), grid)
        assertEquals((4..8).map { GridCell(it, 2) }.toSet(), cells)
        assertTrue(wallCells(plan.copy(metersPerPixel = null), listOf(wall), grid).isEmpty())
    }

    @Test fun anEstimateDoesNotCrossADrawnWall() {
        // Mur vertical sur toute la hauteur, à 10,25 m : la mesure à gauche n'éclaire pas la droite.
        val wall = PlanWall(1, PlanPoint(0.5125f, 0f), PlanPoint(0.5125f, 1f))
        val measurement = PlanMeasurement(PlanPoint(9f / 20f, 0.5f), SignalLevel.HIGH)
        val withWall = FloorMap.onPlan(plan, listOf(measurement), grid, walls = listOf(wall))!!
        val without = FloorMap.onPlan(plan, listOf(measurement), grid)!!
        val beyond = grid.cellOf(FloorPoint(11f, 5f))
        assertNotNull(without.heatmap.valueAt(beyond))
        assertNull(withWall.heatmap.valueAt(beyond))
        assertEquals(wallCells(plan, listOf(wall), grid), withWall.obstacles)
    }

    @Test fun aDoorwayLetsTheEstimateThrough() {
        // Le mur s'arrête à 4,5 m du haut : on passe en dessous, comme par une porte.
        val wall = PlanWall(1, PlanPoint(0.5125f, 0f), PlanPoint(0.5125f, 0.45f))
        val measurement = PlanMeasurement(PlanPoint(9f / 20f, 0.7f), SignalLevel.HIGH)
        val map = FloorMap.onPlan(plan, listOf(measurement), grid, walls = listOf(wall))!!
        assertEquals(SignalLevel.HIGH, map.heatmap.valueAt(grid.cellOf(FloorPoint(11f, 7f)))!!.level)
        assertNull(map.heatmap.valueAt(grid.cellOf(FloorPoint(10.25f, 2f))))
    }
}
