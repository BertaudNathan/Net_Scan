package com.example.MobileM2.Net_Scan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OccupancyMapTest {
    private companion object {
        val WALL_HEIGHTS = listOf(0.6f, 1.0f, 1.6f)
    }

    private val grid = FloorGrid(cellSize = 0.5f)
    private val camera = ScenePoint(0.25f, 1.4f, 0.25f)

    /** Un mur droit devant (vers -z), à [distance] mètres, vu sur toute sa hauteur, avec le sol jusqu'à lui. */
    private fun wallAhead(distance: Float, floorY: Float? = 0f) = SceneObservation(
        camera = camera,
        points = (-2..2).flatMap { dx ->
            val x = 0.25f + dx * 0.5f
            WALL_HEIGHTS.map { ScenePoint(x, it, 0.25f - distance) } + ScenePoint(x, 0f, 0.25f - distance + 0.5f)
        },
        floorY = floorY,
    )

    @Test fun aWallSeenTwiceBecomesAnObstacleAndTheWayToItIsFree() {
        val map = OccupancyMap(grid)
        map.integrate(wallAhead(3f))
        map.integrate(wallAhead(3f))
        val occupancy = map.snapshot()
        assertTrue(GridCell(0, -6) in occupancy.occupied)
        assertTrue(GridCell(0, -3) in occupancy.free)
        assertTrue(GridCell(0, 0) in occupancy.free)
        assertFalse(GridCell(0, -6) in occupancy.free)
    }

    @Test fun aSingleGlimpseIsNotEnough() {
        val map = OccupancyMap(grid)
        map.integrate(wallAhead(3f))
        assertTrue(map.snapshot().occupied.isEmpty())
    }

    @Test fun ceilingAndLowClutterAreIgnored() {
        val map = OccupancyMap(grid)
        val points = listOf(ScenePoint(0.25f, 2.6f, -2f), ScenePoint(0.25f, 0.35f, -2f))
        repeat(3) { map.integrate(SceneObservation(camera, points, floorY = 0f)) }
        val occupancy = map.snapshot()
        assertTrue(occupancy.occupied.isEmpty())
        assertFalse(GridCell(0, -3) in occupancy.free)
    }

    @Test fun pointsBeyondTheReliableRangeAreIgnored() {
        val map = OccupancyMap(grid)
        repeat(3) { map.integrate(SceneObservation(camera, listOf(ScenePoint(0.25f, 1f, -9f)), floorY = 0f)) }
        assertTrue(map.snapshot().occupied.isEmpty())
    }

    @Test fun seeingThroughAFormerObstacleClearsIt() {
        val map = OccupancyMap(grid)
        repeat(2) { map.integrate(wallAhead(2f)) }
        assertTrue(GridCell(0, -4) in map.snapshot().occupied)
        // La porte s'est ouverte : on voit désormais le sol au-delà.
        repeat(6) { map.integrate(wallAhead(3.5f)) }
        assertFalse(GridCell(0, -4) in map.snapshot().occupied)
    }

    @Test fun withoutADetectedPlaneTheFloorIsEstimatedFromLowPoints() {
        val map = OccupancyMap(grid)
        // Le sol est ici à -1,4 m de la caméra posée à 0 : sans plan, il se déduit des points bas.
        val low = ScenePoint(0.25f, 0f, 0.25f)
        val floor = (1..40).map { ScenePoint(0.25f, -1.4f, 0.25f - it * 0.05f) }
        val wall = WALL_HEIGHTS.map { ScenePoint(0.25f, it - 1.4f, -2.75f) }
        repeat(2) { map.integrate(SceneObservation(low, floor + wall, floorY = null)) }
        assertTrue(GridCell(0, -6) in map.snapshot().occupied)
    }

    @Test fun nothingIsMappedBeforeTheFloorIsKnown() {
        val map = OccupancyMap(grid)
        val wallOnly = SceneObservation(camera, WALL_HEIGHTS.map { ScenePoint(0.25f, it, -2.75f) }, floorY = null)
        repeat(3) { assertFalse(map.integrate(wallOnly)) }
        assertEquals(Occupancy.EMPTY, map.snapshot())
    }

    @Test fun aFlatSurfaceIsNotAWall() {
        // Un plateau de table, ou un sol mal estimé : tout à la même hauteur, sans hauteur de mur.
        val map = OccupancyMap(grid)
        val tableTop = (-2..2).map { ScenePoint(0.25f + it * 0.5f, 0.75f, -1.75f) }
        repeat(3) { map.integrate(SceneObservation(camera, tableTop, floorY = 0f)) }
        assertTrue(map.snapshot().occupied.isEmpty())
    }

    @Test fun distantFloorNoiseStaysFloor() {
        // À 3,5 m, la profondeur peut se tromper de 30 cm en hauteur : c'est encore le sol.
        val map = OccupancyMap(grid)
        val noisyFloor = listOf(ScenePoint(0.25f, 0.3f, -3.25f), ScenePoint(0.25f, -0.3f, -3.25f))
        repeat(3) { map.integrate(SceneObservation(camera, noisyFloor, floorY = 0f)) }
        val occupancy = map.snapshot()
        assertTrue(occupancy.occupied.isEmpty())
        assertTrue(GridCell(0, -7) in occupancy.free)
    }

    @Test fun changedCellsAreHandedOutForSavingAndRestoredOnFailure() {
        val map = OccupancyMap(grid)
        map.integrate(wallAhead(3f))
        val changed = map.drainChanges()
        assertTrue(changed.isNotEmpty())
        assertTrue(map.drainChanges().isEmpty())
        map.restoreChanges(changed.keys)
        assertEquals(changed.keys, map.drainChanges().keys)
    }

    @Test fun aSavedMapComesBackIdentical() {
        val map = OccupancyMap(grid)
        repeat(2) { map.integrate(wallAhead(3f)) }
        val restored = OccupancyMap(grid, map.drainChanges())
        assertEquals(map.snapshot(), restored.snapshot())
    }

    @Test fun observedFloorWidensTheAreaAndWallsCutIt() {
        val occupancy = Occupancy(
            free = setOf(GridCell(0, -3), GridCell(0, -4), GridCell(1, -3)),
            occupied = setOf(GridCell(1, 0), GridCell(0, 0)),
        )
        val walk = listOf(listOf(grid.centerOf(GridCell(0, 0)), grid.centerOf(GridCell(0, -1))))
        val area = mapArea(walk, occupancy, grid)
        assertTrue(GridCell(0, -4) in area)
        assertFalse(GridCell(1, 0) in area)
        // On est passé sur cette case : ce ne peut pas être un mur.
        assertTrue(GridCell(0, 0) in area)
    }

    @Test fun estimatesDoNotCrossWalls() {
        // Deux pièces séparées par un mur, reliées seulement loin au nord.
        val free = buildSet {
            (0..8).forEach { row -> add(GridCell(0, -row)); add(GridCell(2, -row)) }
            (0..2).forEach { add(GridCell(it, -8)) }
        }
        val walls = (0..7).map { GridCell(1, -it) }.toSet()
        val map = FloorMap.build(
            segments = emptyList(),
            measurements = listOf(MapMeasurement(grid.centerOf(GridCell(0, 0)), SignalLevel.HIGH)),
            occupancy = Occupancy(free, walls),
            grid = grid,
        )
        assertEquals(walls, map.obstacles)
        assertNull(map.heatmap.valueAt(GridCell(2, 0)))
        assertEquals(SignalLevel.HIGH, map.heatmap.valueAt(GridCell(0, -3))!!.level)
    }
}
