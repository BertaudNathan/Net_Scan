package com.example.MobileM2.Net_Scan.domain

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackBuilderTest {
    @Test fun keepsAPointOnlyOnceThePhoneHasMoved() {
        val track = TrackBuilder(minStep = 0.25f)
        assertTrue(track.add(FloorPoint(0f, 0f)))
        assertFalse(track.add(FloorPoint(0.1f, 0.1f)))
        assertTrue(track.add(FloorPoint(0.3f, 0f)))
        assertEquals(listOf(listOf(FloorPoint(0f, 0f), FloorPoint(0.3f, 0f))), track.segments())
    }

    @Test fun lostTrackingOpensANewSegment() {
        val track = TrackBuilder()
        track.add(FloorPoint(0f, 0f))
        track.breakSegment()
        assertTrue(track.add(FloorPoint(0.05f, 0f)))
        assertEquals(2, track.segments().size)
        assertEquals(listOf(0, 1), track.drainPending().map { it.segment })
    }

    @Test fun anImpossibleJumpOpensANewSegment() {
        val track = TrackBuilder(minStep = 0.25f, maxJump = 1.5f)
        track.add(FloorPoint(0f, 0f))
        track.add(FloorPoint(1f, 0f))
        assertTrue(track.add(FloorPoint(9f, 0f)))
        track.add(FloorPoint(10f, 0f))
        assertEquals(
            listOf(listOf(FloorPoint(0f, 0f), FloorPoint(1f, 0f)), listOf(FloorPoint(9f, 0f), FloorPoint(10f, 0f))),
            track.segments(),
        )
        assertEquals(listOf(0, 0, 1, 1), track.drainPending().map { it.segment })
    }

    @Test fun aBriskWalkStaysInOneSegment() {
        val track = TrackBuilder(minStep = 0.25f, maxJump = 1.5f)
        (0..10).forEach { track.add(FloorPoint(it * 1.4f, 0f)) }
        assertEquals(1, track.segments().size)
    }

    @Test fun pendingPointsAreHandedOutOnce() {
        val track = TrackBuilder()
        track.add(FloorPoint(0f, 0f))
        track.add(FloorPoint(1f, 0f))
        assertEquals(2, track.drainPending().size)
        assertTrue(track.drainPending().isEmpty())
    }

    @Test fun unsavedPointsGoBackFirstInLine() {
        val track = TrackBuilder()
        track.add(FloorPoint(0f, 0f))
        val failed = track.drainPending()
        track.add(FloorPoint(1f, 0f))
        track.restorePending(failed)
        assertEquals(listOf(FloorPoint(0f, 0f), FloorPoint(1f, 0f)), track.drainPending().map { it.point })
    }

    @Test fun headingFollowsWhereThePhoneLooks() {
        val up = floatArrayOf(0f, 1f, 0f)
        assertEquals(0f, floorPoseOf(1f, 2f, floatArrayOf(0f, 0f, -1f), up).headingRadians, 1e-5f)
        assertEquals((PI / 2).toFloat(), floorPoseOf(0f, 0f, floatArrayOf(1f, 0f, 0f), up).headingRadians, 1e-5f)
        assertEquals(FloorPoint(1f, 2f), floorPoseOf(1f, 2f, floatArrayOf(0f, 0f, -1f), up).point)
    }

    @Test fun filmingTheFloorUsesTheTopOfThePhone() {
        val pose = floorPoseOf(0f, 0f, forward = floatArrayOf(0f, -1f, 0f), up = floatArrayOf(-1f, 0f, 0f))
        assertEquals((-PI / 2).toFloat(), pose.headingRadians, 1e-5f)
    }

    @Test fun medianPointResistsAnOutlier() {
        val points = listOf(FloorPoint(1f, 1f), FloorPoint(1.2f, 0.8f), FloorPoint(9f, 9f))
        assertEquals(FloorPoint(1.2f, 1f), medianPoint(points))
        assertNull(medianPoint(emptyList()))
    }
}
