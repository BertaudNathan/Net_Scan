package com.example.MobileM2.Net_Scan.data.position

import kotlin.math.atan2
import kotlin.math.tan
import org.junit.Assert.assertEquals
import org.junit.Test

class DepthProjectionTest {
    // Valeurs relevées sur le Galaxy S24 FE : image CPU 4:3, texture affichée 16:9 recadrée.
    private val image = PinholeIntrinsics(442.32f, 443.66f, 317.09f, 240.43f, 640, 480)
    private val texture = PinholeIntrinsics(1326.96f, 1330.97f, 952.27f, 542.28f, 1920, 1080)
    private val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    @Test fun aFourThirdsDepthImageUsesTheCpuImageFraming() {
        val k = intrinsicsForDepth(listOf(image, texture), depthWidth = 160, depthHeight = 120)
        assertEquals(110.92f, k.fy, 0.01f)
        assertEquals(60.11f, k.cy, 0.01f)
        assertEquals(160, k.width)
    }

    @Test fun aWideDepthImageUsesTheTextureFraming() {
        // Cas du Galaxy S24 FE : profondeur 160 × 90, cadrée comme la texture affichée.
        val k = intrinsicsForDepth(listOf(image, texture), depthWidth = 160, depthHeight = 90)
        assertEquals(110.91f, k.fy, 0.01f)
        assertEquals(45.19f, k.cy, 0.01f)
        assertEquals(90, k.height)
    }

    @Test fun aPixelBelowTheCentreLooksDownAtItsTrueAngle() {
        val k = intrinsicsForDepth(listOf(texture, image), 160, 120)
        val v = k.cy + k.fy * tan(Math.toRadians(30.0)).toFloat()
        val point = unproject(k.cx, v, depthMeters = 2f, k = k, pose = identity)
        val angle = Math.toDegrees(atan2(-point.y, -point.z).toDouble())
        assertEquals(30.0, angle, 0.1)
        assertEquals(0f, point.x, 1e-4f)
    }

    @Test fun thePoseMovesPointsIntoTheWorld() {
        val k = intrinsicsForDepth(listOf(image), 160, 120)
        // Caméra placée en (1 ; 1,4 ; -2), sans rotation.
        val pose = identity.copyOf().also {
            it[12] = 1f
            it[13] = 1.4f
            it[14] = -2f
        }
        val point = unproject(k.cx, k.cy, depthMeters = 3f, k = k, pose = pose)
        assertEquals(1f, point.x, 1e-4f)
        assertEquals(1.4f, point.y, 1e-4f)
        assertEquals(-5f, point.z, 1e-4f)
    }
}
