package com.example.MobileM2.Net_Scan.domain

import java.util.PriorityQueue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/** Case de la grille vue de dessus. */
data class GridCell(val col: Int, val row: Int)

/** Découpage du sol en cases carrées de [cellSize] mètres. */
data class FloorGrid(val cellSize: Float = DEFAULT_CELL_SIZE) {
    fun cellOf(point: FloorPoint) = GridCell(floor(point.x / cellSize).toInt(), floor(point.z / cellSize).toInt())

    fun centerOf(cell: GridCell) = FloorPoint((cell.col + 0.5f) * cellSize, (cell.row + 0.5f) * cellSize)

    companion object {
        const val DEFAULT_CELL_SIZE = 0.5f
    }
}

/** Réglages de la carte. */
object MapRules {
    /**
     * Le téléphone est passé là : la trajectoire élargie de ce rayon est toujours accessible. Le
     * reste de la zone vient de ce que la caméra a vu (voir [OccupancyMap]).
     */
    const val ACCESS_RADIUS = 0.5f

    /** Au-delà, deux points consécutifs ne sont pas reliés : c'est un saut du suivi, pas une marche. */
    const val MAX_JOIN = 3f

    /** Au-delà de cette distance de parcours de toute mesure, une case reste inconnue. */
    const val ESTIMATE_RADIUS = 6f

    /**
     * Au-delà, deux positions reçues à la suite (dix par seconde) supposeraient plus de 15 m/s :
     * c'est un recalage du suivi, la trajectoire est coupée.
     */
    const val MAX_POSE_JUMP = 1.5f

    /** Au-delà de cet écart à la position médiane, le téléphone a bougé pendant le scan réseau. */
    const val STILL_RADIUS = 2f
}

/** Zone accessible : chaque case dont le centre est à moins de [radius] mètres de la trajectoire. */
fun accessibleArea(
    segments: List<List<FloorPoint>>,
    grid: FloorGrid,
    radius: Float = MapRules.ACCESS_RADIUS,
    maxJoin: Float = MapRules.MAX_JOIN,
): Set<GridCell> {
    val area = HashSet<GridCell>()
    val step = grid.cellSize / 2
    for (segment in segments) {
        segment.forEachIndexed { index, point ->
            area.addDisc(point, radius, grid)
            val previous = segment.getOrNull(index - 1) ?: return@forEachIndexed
            val length = previous.distanceTo(point)
            if (length > maxJoin) return@forEachIndexed
            val steps = ceil(length / step).toInt()
            for (k in 1 until steps) {
                val t = k.toFloat() / steps
                val between = FloorPoint(previous.x + (point.x - previous.x) * t, previous.z + (point.z - previous.z) * t)
                area.addDisc(between, radius, grid)
            }
        }
    }
    return area
}

private fun MutableSet<GridCell>.addDisc(center: FloorPoint, radius: Float, grid: FloorGrid) {
    val origin = grid.cellOf(center)
    val reach = ceil(radius / grid.cellSize).toInt() + 1
    val limit = radius * radius + EPSILON
    for (dc in -reach..reach) {
        for (dr in -reach..reach) {
            val cell = GridCell(origin.col + dc, origin.row + dr)
            val cellCenter = grid.centerOf(cell)
            val dx = cellCenter.x - center.x
            val dz = cellCenter.z - center.z
            if (dx * dx + dz * dz <= limit) add(cell)
        }
    }
}

/** Scan réseau placé sur la carte ; [scanId] : son identifiant en base, 0 s'il n'est pas connu. */
data class MapMeasurement(val point: FloorPoint, val level: SignalLevel, val scanId: Long = 0)

/**
 * Valeur d'une case : mesurée (couleur pleine) ou estimée. [confidence] vaut 1 pour une case
 * mesurée et baisse avec la distance à la mesure la plus proche.
 */
data class HeatCell(val level: SignalLevel, val measured: Boolean, val confidence: Float)

/**
 * Heatmap par cases. [area] contient toutes les cases où quelqu'un peut se trouver ; celles
 * absentes de [values] sont inconnues (trop loin de toute mesure) et s'affichent en gris.
 */
data class Heatmap(val grid: FloorGrid, val area: Set<GridCell>, val values: Map<GridCell, HeatCell>) {
    fun valueAt(cell: GridCell): HeatCell? = values[cell]
}

/**
 * Estime chaque case de [area] par pondération inverse au carré de la distance (IDW). La distance
 * est celle d'un parcours dans la zone, pas à vol d'oiseau : deux couloirs voisins ne se mélangent
 * pas. Une mesure hors de la zone y ajoute sa case : on y est forcément passé.
 */
fun buildHeatmap(
    area: Set<GridCell>,
    measurements: List<MapMeasurement>,
    grid: FloorGrid,
    maxRadius: Float = MapRules.ESTIMATE_RADIUS,
): Heatmap {
    val sources = measurements.groupBy { grid.cellOf(it.point) }
        .mapValues { (_, inCell) -> inCell.map { it.level.score }.average() }
    val domain = area + sources.keys
    val reached = HashMap<GridCell, MutableList<Reach>>()
    for ((cell, score) in sources) {
        pathDistances(cell, domain, grid.cellSize, maxRadius).forEach { (target, distance) ->
            reached.getOrPut(target) { mutableListOf() } += Reach(distance, score)
        }
    }
    val values = HashMap<GridCell, HeatCell>()
    for ((cell, reaches) in reached) {
        val own = sources[cell]
        if (own != null) {
            values[cell] = HeatCell(levelOf(own), measured = true, confidence = 1f)
            continue
        }
        var weighted = 0.0
        var total = 0.0
        var nearest = Float.MAX_VALUE
        for (reach in reaches) {
            val weight = 1.0 / (reach.distance * reach.distance)
            weighted += weight * reach.score
            total += weight
            nearest = min(nearest, reach.distance)
        }
        val confidence = (1f - nearest / maxRadius).coerceIn(0f, 1f)
        values[cell] = HeatCell(levelOf(weighted / total), measured = false, confidence = confidence)
    }
    return Heatmap(grid, domain, values)
}

private class Reach(val distance: Float, val score: Double)

/** Dijkstra sur les 8 voisins, sans couper les angles, limité à [maxRadius] mètres. */
private fun pathDistances(start: GridCell, domain: Set<GridCell>, cellSize: Float, maxRadius: Float): Map<GridCell, Float> {
    val best = HashMap<GridCell, Float>()
    best[start] = 0f
    val queue = PriorityQueue<Pair<GridCell, Float>>(compareBy { it.second })
    queue.add(start to 0f)
    while (queue.isNotEmpty()) {
        val (cell, distance) = queue.poll()!!
        if (distance > best.getValue(cell)) continue
        for ((dc, dr) in NEIGHBOURS) {
            val next = GridCell(cell.col + dc, cell.row + dr)
            if (next !in domain) continue
            val diagonal = dc != 0 && dr != 0
            if (diagonal && (GridCell(cell.col + dc, cell.row) !in domain || GridCell(cell.col, cell.row + dr) !in domain)) continue
            val candidate = distance + cellSize * (if (diagonal) SQRT_2 else 1f)
            if (candidate > maxRadius + EPSILON) continue
            if (candidate < (best[next] ?: Float.MAX_VALUE)) {
                best[next] = candidate
                queue.add(next to candidate)
            }
        }
    }
    return best
}

private val NEIGHBOURS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, -1 to 1, 1 to -1, 1 to 1)
private val SQRT_2 = sqrt(2f)
private const val EPSILON = 1e-4f

private val SignalLevel.score: Int
    get() = when (this) {
        SignalLevel.NONE -> 0
        SignalLevel.LOW -> 1
        SignalLevel.MEDIUM -> 2
        SignalLevel.HIGH -> 3
    }

/** Arrondi au plus proche, l'égalité tombant sur le niveau le plus faible. */
private fun levelOf(score: Double): SignalLevel {
    val rounded = ceil(score - 0.5).toInt().coerceIn(0, SignalLevel.entries.size - 1)
    return listOf(SignalLevel.NONE, SignalLevel.LOW, SignalLevel.MEDIUM, SignalLevel.HIGH)[rounded]
}

/**
 * Carte d'un enregistrement : trajectoire, mesures placées, obstacles vus par la caméra et heatmap
 * qui en découle. Les estimations ne traversent pas les obstacles.
 */
data class FloorMap(
    val segments: List<List<FloorPoint>>,
    val measurements: List<MapMeasurement>,
    val heatmap: Heatmap,
    val obstacles: Set<GridCell> = emptySet(),
) {
    companion object {
        fun build(
            segments: List<List<FloorPoint>>,
            measurements: List<MapMeasurement>,
            grid: FloorGrid = FloorGrid(),
            occupancy: Occupancy = Occupancy.EMPTY,
        ): FloorMap {
            val area = mapArea(segments, occupancy, grid)
            return FloorMap(segments, measurements, buildHeatmap(area, measurements, grid), occupancy.occupied - area)
        }
    }
}
