package com.example.MobileM2.Net_Scan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorPlanTest {
    /** Plan de 2000 × 1000 pixels. */
    private val unscaled = FloorPlan(visitId = 1, imagePath = "plan.png", widthPx = 2000, heightPx = 1000, metersPerPixel = null)

    @Test fun theScaleComesFromTwoPointsAndTheirRealDistance() {
        // 400 pixels de long pour un mur de 10 m : 2,5 cm par pixel, plan de 50 × 25 m.
        val scale = scaleFrom(unscaled, PlanPoint(0.1f, 0.5f), PlanPoint(0.3f, 0.5f), meters = 10f)!!
        assertEquals(0.025f, scale, 1e-6f)
        val plan = unscaled.copy(metersPerPixel = scale)
        assertEquals(50f, plan.widthMeters!!, 1e-4f)
        assertEquals(25f, plan.heightMeters!!, 1e-4f)
    }

    @Test fun pointsTooCloseOrADistanceOfZeroGiveNoScale() {
        assertNull(scaleFrom(unscaled, PlanPoint(0.5f, 0.5f), PlanPoint(0.5f, 0.501f), meters = 3f))
        assertNull(scaleFrom(unscaled, PlanPoint(0.1f, 0.1f), PlanPoint(0.9f, 0.9f), meters = 0f))
    }

    @Test fun planPointsBecomeMetresOnceScaled() {
        assertNull(unscaled.toMeters(PlanPoint(0.5f, 0.5f)))
        val plan = unscaled.copy(metersPerPixel = 0.025f)
        assertEquals(FloorPoint(25f, 12.5f), plan.toMeters(PlanPoint(0.5f, 0.5f)))
        assertFalse(plan.contains(PlanPoint(1.2f, 0.5f)))
        assertTrue(plan.contains(PlanPoint(0f, 1f)))
    }

    @Test fun theHeatmapCoversOnlyThePlanNearTheScans() {
        val plan = unscaled.copy(metersPerPixel = 0.025f)
        val map = FloorMap.onPlan(
            plan,
            listOf(PlanMeasurement(PlanPoint(0.02f, 0.04f), SignalLevel.HIGH)),
            FloorGrid(0.5f),
        )!!
        val grid = map.heatmap.grid
        // Mesure à (1 m ; 1 m) : rien au-delà de 6 m, rien hors du plan.
        assertEquals(SignalLevel.HIGH, map.heatmap.valueAt(grid.cellOf(FloorPoint(1f, 1f)))!!.level)
        assertTrue(map.heatmap.valueAt(grid.cellOf(FloorPoint(5f, 1f))) != null)
        assertNull(map.heatmap.valueAt(grid.cellOf(FloorPoint(9f, 1f))))
        assertTrue(map.heatmap.area.none { it.col < 0 || it.row < 0 })
        assertTrue(map.heatmap.area.all { it in map.heatmap.values })
        val measurement = map.measurements.single()
        assertEquals(1f, measurement.point.x, 1e-4f)
        assertEquals(1f, measurement.point.z, 1e-4f)
    }

    @Test fun anUnscaledPlanHasNoHeatmap() {
        assertNull(FloorMap.onPlan(unscaled, listOf(PlanMeasurement(PlanPoint(0.5f, 0.5f), SignalLevel.LOW)), FloorGrid()))
    }

    @Test fun twoScansAreBlendedAsTheCrowFlies() {
        val plan = unscaled.copy(metersPerPixel = 0.025f)
        // Deux mesures à 4 m l'une de l'autre, sans mur entre elles.
        val map = FloorMap.onPlan(
            plan,
            listOf(
                PlanMeasurement(PlanPoint(0.25f / 50f, 0.25f / 25f), SignalLevel.HIGH),
                PlanMeasurement(PlanPoint(4.25f / 50f, 0.25f / 25f), SignalLevel.LOW),
            ),
            FloorGrid(0.5f),
        )!!
        assertEquals(SignalLevel.MEDIUM, map.heatmap.valueAt(GridCell(4, 0))!!.level)
    }
}
