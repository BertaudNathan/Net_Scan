package com.example.MobileM2.Net_Scan.data.position

import com.example.MobileM2.Net_Scan.domain.ScenePoint
import kotlin.math.abs

/** Paramètres d'une caméra sténopé, en pixels d'une image de [width] × [height]. */
data class PinholeIntrinsics(
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
    val width: Int,
    val height: Int,
) {
    val aspect: Float get() = width.toFloat() / height

    /** Mêmes paramètres pour la même image redimensionnée en [newWidth] × [newHeight]. */
    fun scaledTo(newWidth: Int, newHeight: Int) = PinholeIntrinsics(
        fx = fx * newWidth / width,
        fy = fy * newHeight / height,
        cx = cx * newWidth / width,
        cy = cy * newHeight / height,
        width = newWidth,
        height = newHeight,
    )
}

/**
 * Paramètres à utiliser pour l'image de profondeur. Selon le téléphone, elle a le cadrage de l'image
 * CPU d'ARCore (souvent 4:3) ou celui de la texture affichée (16:9, recadrée ; 160 × 90 sur le
 * Galaxy S24 FE). Avec les paramètres de l'autre image, les angles verticaux seraient faussés d'un
 * quart. On garde les paramètres dont le cadrage correspond à celui de la profondeur ; à égalité, le premier.
 */
fun intrinsicsForDepth(candidates: List<PinholeIntrinsics>, depthWidth: Int, depthHeight: Int): PinholeIntrinsics {
    val aspect = depthWidth.toFloat() / depthHeight
    return candidates.minBy { abs(it.aspect - aspect) }.scaledTo(depthWidth, depthHeight)
}

/**
 * Pixel ([u], [v]) de profondeur [depthMeters] (distance le long de l'axe optique) vers le repère du
 * monde. Repère caméra OpenGL : x à droite, y en haut, la caméra regarde vers -z. [pose] est la
 * matrice 4 × 4, en colonnes, de la caméra physique (orientation du capteur).
 */
fun unproject(u: Float, v: Float, depthMeters: Float, k: PinholeIntrinsics, pose: FloatArray): ScenePoint {
    val x = depthMeters * (u - k.cx) / k.fx
    val y = depthMeters * (k.cy - v) / k.fy
    val z = -depthMeters
    return ScenePoint(
        pose[0] * x + pose[4] * y + pose[8] * z + pose[12],
        pose[1] * x + pose[5] * y + pose[9] * z + pose[13],
        pose[2] * x + pose[6] * y + pose[10] * z + pose[14],
    )
}
