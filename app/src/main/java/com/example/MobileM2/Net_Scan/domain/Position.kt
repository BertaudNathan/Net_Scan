package com.example.MobileM2.Net_Scan.domain

import kotlin.math.atan2
import kotlin.math.hypot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Point au sol, en mètres, dans le repère d'un enregistrement : origine au démarrage d'ARCore,
 * x vers la droite et z vers l'arrière de la direction de départ. Vue de dessus, la direction
 * de départ pointe donc vers le haut de l'écran.
 */
data class FloorPoint(val x: Float, val z: Float) {
    fun distanceTo(other: FloorPoint): Float = hypot(x - other.x, z - other.z)
}

/** Position du téléphone vue de dessus. Le cap vaut 0 vers la direction de départ et croît dans le sens horaire. */
data class FloorPose(val point: FloorPoint, val headingRadians: Float)

/** Raison d'une perte de suivi ARCore (TrackingState.PAUSED). */
enum class TrackingProblem { EXCESSIVE_MOTION, INSUFFICIENT_LIGHT, INSUFFICIENT_FEATURES, CAMERA_UNAVAILABLE, UNKNOWN }

/** Raison pour laquelle aucune position ne peut être donnée. */
enum class PositioningUnavailable { CAMERA_PERMISSION, UNSUPPORTED_DEVICE, ARCORE_NOT_INSTALLED, CAMERA_ERROR, ERROR }

sealed interface PositionState {
    /** Le suivi démarre : il faut déplacer un peu le téléphone. */
    data object Starting : PositionState

    data class Tracking(val pose: FloorPose) : PositionState

    /** Suivi perdu ; il reprend souvent seul quand les conditions s'améliorent. */
    data class Lost(val problem: TrackingProblem) : PositionState

    /** Aucun positionnement possible : les scans réseau restent possibles, sans place sur la carte. */
    data class Unavailable(val reason: PositioningUnavailable) : PositionState
}

/**
 * Position du téléphone dans le bâtiment : ARCore en marche libre, ou une position simulée pour
 * les tests et les démonstrations, à signaler à l'écran via [isSimulated].
 */
interface PositionSource {
    val isSimulated: Boolean
    val position: Flow<PositionState>

    /** Ce que la caméra voit, environ deux fois par seconde, pour cartographier la pièce et ses murs. */
    val observations: Flow<SceneObservation> get() = emptyFlow()
}

/**
 * Pose vue de dessus à partir des axes du téléphone dans le repère du monde ([forward] : direction
 * filmée ; [up] : haut de l'écran). Si l'on filme le sol, c'est le haut du téléphone qui donne le cap.
 */
fun floorPoseOf(x: Float, z: Float, forward: FloatArray, up: FloatArray): FloorPose {
    val axis = if (hypot(forward[0], forward[2]) >= MIN_HORIZONTAL_COMPONENT) forward else up
    return FloorPose(FloorPoint(x, z), atan2(axis[0], -axis[2]))
}

private const val MIN_HORIZONTAL_COMPONENT = 0.3f

/** Point de trajectoire à sauvegarder, avec le numéro de son tronçon dans l'enregistrement. */
data class TrackPoint(val segment: Int, val point: FloorPoint)

/**
 * Trajectoire d'un enregistrement. Un point n'est retenu qu'une fois le téléphone déplacé de
 * [minStep] mètres, ce qui ignore le bruit du suivi à l'arrêt. Une perte de suivi coupe la
 * trajectoire en tronçons : la zone parcourue ne relie jamais deux positions de part et d'autre
 * d'une perte, car ARCore peut se recaler entre-temps. Un déplacement de plus de [maxJump] mètres
 * entre deux positions reçues est un recalage, pas une marche : il ouvre aussi un nouveau tronçon.
 */
class TrackBuilder(
    private val minStep: Float = DEFAULT_MIN_STEP,
    private val maxJump: Float = MapRules.MAX_POSE_JUMP,
) {
    private val segments = mutableListOf<MutableList<FloorPoint>>()
    private val pending = ArrayDeque<TrackPoint>()
    private var open = false

    /** Retourne true si le point a été retenu. */
    fun add(point: FloorPoint): Boolean {
        val current = segments.lastOrNull()
        if (open && current != null) {
            val distance = current.last().distanceTo(point)
            if (distance < minStep) return false
            if (distance > maxJump) segments += mutableListOf(point) else current += point
        } else {
            segments += mutableListOf(point)
            open = true
        }
        pending.addLast(TrackPoint(segments.lastIndex, point))
        return true
    }

    /** Le prochain point ouvrira un nouveau tronçon. */
    fun breakSegment() {
        open = false
    }

    fun segments(): List<List<FloorPoint>> = segments.map { it.toList() }

    /** Points retenus depuis le dernier appel, à sauvegarder. */
    fun drainPending(): List<TrackPoint> = pending.toList().also { pending.clear() }

    /** Remet en tête de file des points dont la sauvegarde a échoué. */
    fun restorePending(points: List<TrackPoint>) {
        points.asReversed().forEach(pending::addFirst)
    }

    companion object {
        const val DEFAULT_MIN_STEP = 0.25f
    }
}

/** Point médian (médianes séparées de x et de z) : robuste aux sauts du suivi pendant un scan. */
fun medianPoint(points: List<FloorPoint>): FloorPoint? {
    if (points.isEmpty()) return null
    return FloorPoint(median(points.map { it.x }), median(points.map { it.z }))
}

private fun median(values: List<Float>): Float {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2f
}
