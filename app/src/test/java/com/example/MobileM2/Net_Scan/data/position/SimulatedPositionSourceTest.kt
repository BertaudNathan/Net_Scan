package com.example.MobileM2.Net_Scan.data.position

import com.example.MobileM2.Net_Scan.domain.FloorGrid
import com.example.MobileM2.Net_Scan.domain.FloorPoint
import com.example.MobileM2.Net_Scan.domain.GridCell
import com.example.MobileM2.Net_Scan.domain.OccupancyMap
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedPositionSourceTest {
    private val source = SimulatedPositionSource(speedMetersPerSecond = 1f, startupMillis = 1_000, clock = { 0L })

    @Test fun followsTheRouteAtTheChosenSpeed() {
        assertNull(source.poseAt(500))
        val pose = source.poseAt(3_000)!!
        assertEquals(0f, pose.point.x, 1e-4f)
        assertEquals(-2f, pose.point.z, 1e-4f)
        assertEquals(0f, pose.headingRadians, 1e-4f)
        assertEquals((PI / 2).toFloat(), source.poseAt(1_000 + 9_000)!!.headingRadians, 1e-4f)
    }

    @Test fun walkingTheRoomRevealsItsWalls() {
        val grid = FloorGrid()
        val map = OccupancyMap(grid)
        (0 until 120).forEach { second -> map.integrate(source.observe(source.poseAt(1_000L + second * 500L)!!)) }
        val occupancy = map.snapshot()
        // Le mur du fond (z = -9,5) et la cloison (z = -5,5) apparaissent ; le couloir reste libre.
        fun wallNear(x: Float, z: Float) = listOf(z - 0.1f, z + 0.1f).any { grid.cellOf(FloorPoint(x, it)) in occupancy.occupied }
        assertTrue(wallNear(0.25f, -9.5f))
        assertTrue(wallNear(2.75f, -5.5f))
        assertTrue(GridCell(0, -8) in occupancy.free)
    }
}
