package com.example.MobileM2.Net_Scan.data.position

import com.example.MobileM2.Net_Scan.domain.FloorPoint
import com.example.MobileM2.Net_Scan.domain.FloorPose
import com.example.MobileM2.Net_Scan.domain.OccupancyRules
import com.example.MobileM2.Net_Scan.domain.PositionSource
import com.example.MobileM2.Net_Scan.domain.PositionState
import com.example.MobileM2.Net_Scan.domain.SceneObservation
import com.example.MobileM2.Net_Scan.domain.ScenePoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Marche simulée en boucle le long de [route], dans une pièce dont les murs sont [walls], pour les
 * tests et les démonstrations sans téléphone compatible ARCore. La position dépend seulement du
 * temps écoulé : position et vues de la scène restent cohérentes. À signaler comme donnée simulée.
 */
class SimulatedPositionSource(
    private val route: List<FloorPoint> = DEFAULT_ROUTE,
    private val walls: List<Wall> = DEFAULT_WALLS,
    private val speedMetersPerSecond: Float = 0.8f,
    private val intervalMillis: Long = 200,
    private val startupMillis: Long = 1_000,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : PositionSource {
    /** Mur vu de dessus, de [from] à [to]. */
    data class Wall(val from: FloorPoint, val to: FloorPoint)

    private val startedAt = clock()
    private val legs = route.indices.map { route[it] to route[(it + 1) % route.size] }
    private val loopLength = legs.sumOf { (from, to) -> from.distanceTo(to).toDouble() }.toFloat()

    init {
        require(route.size >= 2) { "A simulated walk needs at least two points" }
    }

    override val isSimulated = true

    override val position: Flow<PositionState> = flow {
        while (true) {
            emit(poseAt(clock() - startedAt)?.let { PositionState.Tracking(it) } ?: PositionState.Starting)
            delay(intervalMillis)
        }
    }

    override val observations: Flow<SceneObservation> = flow {
        while (true) {
            poseAt(clock() - startedAt)?.let { emit(observe(it)) }
            delay(OBSERVATION_INTERVAL_MILLIS)
        }
    }

    /** Position après [elapsedMillis] millisecondes, ou null pendant le démarrage du suivi. */
    fun poseAt(elapsedMillis: Long): FloorPose? {
        if (elapsedMillis < startupMillis) return null
        var distance = ((elapsedMillis - startupMillis) * speedMetersPerSecond / 1_000f) % loopLength
        for ((from, to) in legs) {
            val length = from.distanceTo(to)
            if (distance <= length) {
                val ratio = if (length == 0f) 0f else distance / length
                val point = FloorPoint(from.x + (to.x - from.x) * ratio, from.z + (to.z - from.z) * ratio)
                return FloorPose(point, atan2(to.x - from.x, -(to.z - from.z)))
            }
            distance -= length
        }
        return FloorPose(route.first(), 0f)
    }

    /**
     * Ce que verrait la caméra tenue droite : des rayons en éventail devant le téléphone, le sol
     * jusqu'au premier mur, puis ce mur à deux hauteurs.
     */
    fun observe(pose: FloorPose): SceneObservation {
        val origin = pose.point
        val points = ArrayList<ScenePoint>()
        for (i in 0 until RAYS) {
            val angle = pose.headingRadians + FIELD_OF_VIEW * (i / (RAYS - 1f) - 0.5f)
            val dx = sin(angle)
            val dz = -cos(angle)
            val hit = walls.mapNotNull { distanceToWall(origin, dx, dz, it) }.minOrNull()
            val reach = minOf(hit ?: OccupancyRules.MAX_RANGE, OccupancyRules.MAX_RANGE)
            var d = FLOOR_STEP
            while (d < reach - FLOOR_STEP / 2) {
                points += ScenePoint(origin.x + dx * d, FLOOR_Y, origin.z + dz * d)
                d += FLOOR_STEP
            }
            if (hit != null && hit <= OccupancyRules.MAX_RANGE) {
                WALL_HEIGHTS.forEach { points += ScenePoint(origin.x + dx * hit, it, origin.z + dz * hit) }
            }
        }
        return SceneObservation(ScenePoint(origin.x, CAMERA_HEIGHT, origin.z), points, floorY = FLOOR_Y)
    }

    private fun distanceToWall(origin: FloorPoint, dx: Float, dz: Float, wall: Wall): Float? {
        val ex = wall.to.x - wall.from.x
        val ez = wall.to.z - wall.from.z
        val denominator = dx * ez - dz * ex
        if (denominator == 0f) return null
        val fx = wall.from.x - origin.x
        val fz = wall.from.z - origin.z
        val t = (fx * ez - fz * ex) / denominator
        val u = (fx * dz - fz * dx) / denominator
        return if (t > 0f && u in 0f..1f) t else null
    }

    companion object {
        /** Tour d'un plateau de bureaux : un couloir, le fond de la salle, le long de la cloison, retour. */
        val DEFAULT_ROUTE = listOf(
            FloorPoint(0f, 0f),
            FloorPoint(0f, -8f),
            FloorPoint(6f, -8f),
            FloorPoint(6f, -4f),
            FloorPoint(2f, -4f),
        )

        /** Une pièce de 9 × 11 m et une cloison au milieu. */
        val DEFAULT_WALLS = listOf(
            Wall(FloorPoint(-1.5f, 1.5f), FloorPoint(7.5f, 1.5f)),
            Wall(FloorPoint(7.5f, 1.5f), FloorPoint(7.5f, -9.5f)),
            Wall(FloorPoint(7.5f, -9.5f), FloorPoint(-1.5f, -9.5f)),
            Wall(FloorPoint(-1.5f, -9.5f), FloorPoint(-1.5f, 1.5f)),
            Wall(FloorPoint(1.5f, -5.5f), FloorPoint(4.5f, -5.5f)),
        )

        private const val OBSERVATION_INTERVAL_MILLIS = 500L
        private const val RAYS = 25
        private const val FIELD_OF_VIEW = 1.1f
        private const val FLOOR_STEP = 0.25f
        private const val FLOOR_Y = 0f
        private const val CAMERA_HEIGHT = 1.4f
        private val WALL_HEIGHTS = listOf(0.8f, 1.4f)
    }
}
