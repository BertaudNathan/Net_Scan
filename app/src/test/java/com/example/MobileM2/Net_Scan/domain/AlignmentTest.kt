package com.example.MobileM2.Net_Scan.domain

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlignmentTest {
    private val grid = FloorGrid(cellSize = 0.5f)

    @Test fun aTransformRotatesClockwiseOnScreenThenShifts() {
        val quarter = FloorTransform((PI / 2).toFloat(), tx = 1f, tz = 0f)
        val moved = quarter.apply(FloorPoint(0f, -1f))
        assertEquals(2f, moved.x, 1e-5f)
        assertEquals(0f, moved.z, 1e-5f)
        assertEquals((PI / 2).toFloat(), quarter.apply(FloorPose(FloorPoint(0f, 0f), 0f)).headingRadians, 1e-5f)
    }

    @Test fun aRotationAroundAPivotKeepsThePivotInPlace() {
        val pivot = FloorPoint(3f, -4f)
        val turn = FloorTransform.around(pivot, rotation = 0.3f, dx = 0.5f, dz = 0f)
        val moved = turn.apply(pivot)
        assertEquals(3.5f, moved.x, 1e-5f)
        assertEquals(-4f, moved.z, 1e-5f)
    }

    @Test fun transformsChainInOrder() {
        val first = FloorTransform(0.2f, 1f, -2f)
        val second = FloorTransform(-0.5f, 0.3f, 0.7f)
        val point = FloorPoint(1.5f, -0.5f)
        val chained = first.then(second).apply(point)
        val stepByStep = second.apply(first.apply(point))
        assertEquals(stepByStep.x, chained.x, 1e-5f)
        assertEquals(stepByStep.z, chained.z, 1e-5f)
    }

    @Test fun aShiftedAndTurnedRoomIsPutBackOnItsWalls() {
        val base = room(cols = 0..12, rows = -16..0)
        // Après une perte de suivi : la même pièce vue décalée de 0,75 m et tournée de 6°.
        val drift = FloorTransform.around(FloorPoint(3f, -4f), rotation = Math.toRadians(6.0).toFloat(), dx = 0.75f, dz = -0.5f)
        val seen = Occupancy(base.free.moved(drift), base.occupied.moved(drift))
        val alignment = MapMatcher.align(base, seen, pivot = drift.apply(FloorPoint(3f, -4f)), grid = grid)!!
        // Les coins de la pièce reviennent à leur place, à la précision de la grille près.
        for (corner in listOf(FloorPoint(0.25f, 0.25f), FloorPoint(6.25f, -7.75f), FloorPoint(0.25f, -7.75f))) {
            val back = alignment.transform.apply(drift.apply(corner))
            assertEquals(corner.x, back.x, 0.35f)
            assertEquals(corner.z, back.z, 0.35f)
        }
    }

    @Test fun inACorridorOnlyTheSidewaysOffsetIsCorrected() {
        val base = corridor(rows = -30..0)
        // Revue après une perte : décalée de 0,5 m sur le côté, rien ne situe la portion le long du couloir.
        val seen = corridor(rows = -20..-10, colShift = 1)
        val alignment = MapMatcher.align(base, seen, pivot = FloorPoint(1.75f, -7.5f), grid = grid)!!
        val moved = alignment.transform.apply(FloorPoint(1.75f, -7.5f))
        assertEquals(1.25f, moved.x, 0.15f)
        assertEquals(-7.5f, moved.z, 0.15f)
    }

    @Test fun noCorrectionWithoutCommonWalls() {
        val base = room(cols = 0..12, rows = -16..0)
        val elsewhere = room(cols = 60..70, rows = -10..0)
        assertNull(MapMatcher.align(base, elsewhere, pivot = FloorPoint(32f, -2f), grid = grid))
    }

    @Test fun aSectionAlreadyInPlaceNeedsNoCorrection() {
        val base = room(cols = 0..12, rows = -16..0)
        assertNull(MapMatcher.align(base, base, pivot = FloorPoint(3f, -4f), grid = grid))
    }

    @Test fun theFirstWalkIsMappedAsItComes() {
        val mapping = LiveMapping(grid)
        walk(mapping, steps = 0..12)
        assertFalse(mapping.isRealigning)
        assertTrue(GridCell(-1, -6) in mapping.occupancy().occupied)
        assertEquals(1, mapping.segments().size)
    }

    @Test fun afterALossTheNewSectionIsPutBackOnTheMap() {
        val mapping = LiveMapping(grid)
        walk(mapping, steps = 0..12)
        mapping.onTrackingLost()
        // ARCore repart décalé de 0,5 m vers la droite : sans recalage, le couloir serait dédoublé.
        walk(mapping, steps = 13..26, drift = 0.5f)
        assertFalse(mapping.isRealigning)
        val pose = mapping.onPose(FloorPose(FloorPoint(1.5f + 0.5f, -13.5f), 0f))
        assertEquals(1.5f, pose.point.x, 0.3f)
        val occupied = mapping.occupancy().occupied
        assertTrue("doubled walls: ${occupied.filter { it.col == 0 || it.col == 7 }}", occupied.none { it.col == 0 || it.col == 7 })
        assertEquals(2, mapping.segments().size)
        assertTrue(mapping.segments()[1].all { it.x in 1.2f..1.8f })
    }

    @Test fun whileRealigningTheNewSectionIsHeldBack() {
        val mapping = LiveMapping(grid)
        walk(mapping, steps = 0..12)
        val before = mapping.occupancy()
        mapping.onTrackingLost()
        walk(mapping, steps = 13..14, drift = 0.5f)
        assertTrue(mapping.isRealigning)
        assertEquals(before, mapping.occupancy())
        assertEquals(1, mapping.segments().size)
    }

    @Test fun anUnmappedAreaIsAddedAsItIsAfterAWhile() {
        val mapping = LiveMapping(grid)
        walk(mapping, steps = 0..12)
        mapping.onTrackingLost()
        walk(mapping, steps = 0..AlignmentRules.MAX_PENDING_OBSERVATIONS, along = 40f)
        assertFalse(mapping.isRealigning)
        assertTrue(mapping.occupancy().occupied.any { it.col >= 79 })
    }

    @Test fun finishingAddsWhatWasHeldBack() {
        val mapping = LiveMapping(grid)
        walk(mapping, steps = 0..12)
        mapping.onTrackingLost()
        walk(mapping, steps = 13..16, drift = 0.5f)
        mapping.finish()
        assertFalse(mapping.isRealigning)
        assertEquals(2, mapping.segments().size)
    }

    @Test fun scanPositionsTakenWhileRealigningAreCorrectedAfterwards() {
        val mapping = LiveMapping(grid)
        walk(mapping, steps = 0..12)
        mapping.onTrackingLost()
        walk(mapping, steps = 13..14, drift = 0.5f)
        val sample = mapping.sample(FloorPoint(2f, -7f))
        walk(mapping, steps = 15..26, drift = 0.5f)
        assertEquals(1.5f, mapping.correct(sample).x, 0.3f)
    }

    /** Pièce rectangulaire : murs sur le pourtour, sol libre dedans. */
    private fun room(cols: IntRange, rows: IntRange): Occupancy {
        val walls = HashSet<GridCell>()
        val free = HashSet<GridCell>()
        for (col in cols) for (row in rows) {
            val border = col == cols.first || col == cols.last || row == rows.first || row == rows.last
            (if (border) walls else free) += GridCell(col, row)
        }
        return Occupancy(free, walls)
    }

    /** Couloir de 3 m le long de z, murs en colonnes -1 et 6 (+ [colShift]). */
    private fun corridor(rows: IntRange, colShift: Int = 0): Occupancy {
        val walls = rows.flatMap { listOf(GridCell(-1 + colShift, it), GridCell(6 + colShift, it)) }.toSet()
        val free = rows.flatMap { row -> (0..5).map { GridCell(it + colShift, row) } }.toSet()
        return Occupancy(free, walls)
    }

    private fun Set<GridCell>.moved(transform: FloorTransform): Set<GridCell> =
        mapTo(HashSet()) { grid.cellOf(transform.apply(grid.centerOf(it))) }

    /**
     * Marche vers le nord (z décroissant) au milieu d'un couloir de 3 m, une vue par pas de 0,5 m.
     * [drift] décale tout ce que rapporte ARCore ; [along] déplace la marche ailleurs en x.
     */
    private fun walk(mapping: LiveMapping, steps: IntRange, drift: Float = 0f, along: Float = 0f) {
        for (i in steps) {
            val truth = FloorPoint(1.5f + along, -i * 0.5f)
            mapping.onPose(FloorPose(FloorPoint(truth.x + drift, truth.z), 0f))
            mapping.onObservation(corridorView(truth, drift, along))
        }
    }

    private fun corridorView(camera: FloorPoint, drift: Float, along: Float): SceneObservation {
        val points = ArrayList<ScenePoint>()
        var z = camera.z + 0.5f
        while (z >= camera.z - 3.5f) {
            for (wallX in listOf(-0.25f + along, 3.25f + along)) {
                listOf(0.6f, 1f, 1.6f).forEach { points += ScenePoint(wallX + drift, it, z) }
            }
            var x = 0.25f + along
            while (x < 3f + along) {
                points += ScenePoint(x + drift, 0f, z)
                x += 0.5f
            }
            z -= 0.25f
        }
        return SceneObservation(ScenePoint(camera.x + drift, 1.4f, camera.z), points, floorY = 0f)
    }
}
