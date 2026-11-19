package com.example.MobileM2.Net_Scan.domain

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Déplacement rigide vu de dessus : rotation de [rotation] radians (sens horaire à l'écran, comme
 * le cap) autour de l'origine, puis translation ([tx], [tz]). La hauteur n'est jamais modifiée.
 */
data class FloorTransform(val rotation: Float, val tx: Float, val tz: Float) {
    private val cos = cos(rotation)
    private val sin = sin(rotation)

    fun apply(point: FloorPoint) = FloorPoint(cos * point.x - sin * point.z + tx, sin * point.x + cos * point.z + tz)

    fun apply(point: ScenePoint) = ScenePoint(cos * point.x - sin * point.z + tx, point.y, sin * point.x + cos * point.z + tz)

    fun apply(pose: FloorPose) = FloorPose(apply(pose.point), pose.headingRadians + rotation)

    fun apply(observation: SceneObservation) =
        SceneObservation(apply(observation.camera), observation.points.map(::apply), observation.floorY)

    /** Ce déplacement, puis [next]. */
    fun then(next: FloorTransform) = FloorTransform(
        rotation = rotation + next.rotation,
        tx = next.cos * tx - next.sin * tz + next.tx,
        tz = next.sin * tx + next.cos * tz + next.tz,
    )

    companion object {
        val IDENTITY = FloorTransform(0f, 0f, 0f)

        /** Rotation de [rotation] radians autour de [pivot], puis décalage ([dx], [dz]). */
        fun around(pivot: FloorPoint, rotation: Float, dx: Float, dz: Float): FloorTransform {
            val c = cos(rotation)
            val s = sin(rotation)
            return FloorTransform(rotation, pivot.x - (c * pivot.x - s * pivot.z) + dx, pivot.z - (s * pivot.x + c * pivot.z) + dz)
        }
    }
}

/** Réglages du recalage automatique d'une portion de parcours sur la carte déjà tracée. */
object AlignmentRules {
    /** Dérive cherchée après une perte de suivi : jusqu'à 1,5 m et 12°. */
    const val MAX_SHIFT_STEPS = 6
    const val SHIFT_STEP = 0.25f
    const val MAX_ROTATION_DEGREES = 12
    const val ROTATION_STEP_DEGREES = 2

    /**
     * Chaque mètre ou degré de correction coûte des points : entre deux recalages aussi bons, le
     * plus petit gagne. Dans un couloir uniforme, rien ne situe la portion le long du couloir : elle
     * n'y est donc pas déplacée, seul le décalage latéral est corrigé.
     */
    const val SHIFT_COST = 1f
    const val ROTATION_COST = 0.2f

    /** Un recalage n'est retenu que s'il fait clairement mieux que de ne rien changer. */
    const val MIN_GAIN = 3f
    const val MIN_MATCHES = 6
    const val MIN_WALLS = 6

    /** Vues de la scène attendues avant de chercher (3 s), et au plus avant d'abandonner (10 s). */
    const val MIN_PENDING_OBSERVATIONS = 6
    const val MAX_PENDING_OBSERVATIONS = 20
}

/** Recalage trouvé : [gain] est l'avantage sur « ne rien changer », [matches] le nombre de murs superposés. */
data class Alignment(val transform: FloorTransform, val gain: Float, val matches: Int)

/**
 * Recale une portion de carte ([moving]) sur la carte déjà tracée ([base]) en cherchant le petit
 * déplacement qui superpose le mieux leurs murs, sans faire tomber de mur sur du sol déjà vu libre.
 */
object MapMatcher {
    fun align(base: Occupancy, moving: Occupancy, pivot: FloorPoint, grid: FloorGrid): Alignment? {
        if (moving.occupied.size < AlignmentRules.MIN_WALLS || base.occupied.size < AlignmentRules.MIN_WALLS) return null
        val nearWalls = HashSet<GridCell>()
        for (wall in base.occupied) {
            for (dc in -1..1) for (dr in -1..1) nearWalls += GridCell(wall.col + dc, wall.row + dr)
        }
        val walls = moving.occupied.map(grid::centerOf)
        val floor = moving.free.map(grid::centerOf)

        fun score(transform: FloorTransform): Pair<Float, Int> {
            var score = 0f
            var matches = 0
            for (center in walls) {
                val cell = grid.cellOf(transform.apply(center))
                when {
                    cell in base.occupied -> {
                        score += 1f
                        matches++
                    }
                    cell in nearWalls -> score += 0.5f
                    cell in base.free -> score -= 1f
                }
            }
            // Du sol vu libre ne peut pas tomber sur un mur.
            for (center in floor) {
                if (grid.cellOf(transform.apply(center)) in base.occupied) score -= 0.5f
            }
            return score to matches
        }

        val unchanged = score(FloorTransform.IDENTITY).first
        var best: Alignment? = null
        var bestScore = Float.NEGATIVE_INFINITY
        val steps = AlignmentRules.MAX_SHIFT_STEPS
        for (degrees in -AlignmentRules.MAX_ROTATION_DEGREES..AlignmentRules.MAX_ROTATION_DEGREES step AlignmentRules.ROTATION_STEP_DEGREES) {
            val rotation = Math.toRadians(degrees.toDouble()).toFloat()
            for (i in -steps..steps) {
                for (j in -steps..steps) {
                    val dx = i * AlignmentRules.SHIFT_STEP
                    val dz = j * AlignmentRules.SHIFT_STEP
                    val transform = FloorTransform.around(pivot, rotation, dx, dz)
                    val (raw, matches) = score(transform)
                    val total = raw - AlignmentRules.SHIFT_COST * hypot(dx, dz) - AlignmentRules.ROTATION_COST * abs(degrees)
                    if (total > bestScore) {
                        bestScore = total
                        best = Alignment(transform, total - unchanged, matches)
                    }
                }
            }
        }
        return best?.takeIf { it.gain >= AlignmentRules.MIN_GAIN && it.matches >= AlignmentRules.MIN_MATCHES }
    }
}
