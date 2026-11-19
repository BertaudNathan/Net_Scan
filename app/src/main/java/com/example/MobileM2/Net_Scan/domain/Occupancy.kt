package com.example.MobileM2.Net_Scan.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

/** Point 3D dans le repère ARCore de l'enregistrement, en mètres, y vers le haut. */
data class ScenePoint(val x: Float, val y: Float, val z: Float)

/**
 * Ce que la caméra a vu à un instant : sa position et des points de la scène (profondeur ARCore
 * ou nuage de points). [floorY] est la hauteur du sol quand ARCore a détecté un plan horizontal.
 * Aucune image n'est conservée, seulement ces points.
 */
class SceneObservation(val camera: ScenePoint, val points: List<ScenePoint>, val floorY: Float?)

/** Cases vues libres (sol) et cases occupées (murs, rayonnages, obstacles). */
data class Occupancy(val free: Set<GridCell>, val occupied: Set<GridCell>) {
    companion object {
        val EMPTY = Occupancy(emptySet(), emptySet())
    }
}

/** Réglages de la carte d'occupation (palier 2). */
object OccupancyRules {
    /** Au-delà, la profondeur ARCore est trop imprécise. */
    const val MAX_RANGE = 4f
    const val MIN_RANGE = 0.3f

    /**
     * Un point à moins de cette hauteur du sol est le sol. La tolérance grandit avec la distance :
     * la profondeur se trompe davantage au loin, surtout sur un sol vu en biais.
     */
    const val FLOOR_TOLERANCE = 0.15f
    const val FLOOR_TOLERANCE_PER_METER = 0.05f

    /** Entre le sol (plus cette marge) et le plafond, un point peut appartenir à un obstacle. */
    const val OBSTACLE_MARGIN = 0.15f
    const val OBSTACLE_MIN_HEIGHT = 0.3f
    const val OBSTACLE_MAX_HEIGHT = 2f

    /**
     * Une case n'est un mur que si ce qu'on y voit s'élève d'au moins cette hauteur : un plateau de
     * table, ou un sol mal estimé, est plat et ne bloque pas le passage du regard.
     */
    const val MIN_WALL_EXTENT = 0.4f

    /** Log-odds : une case devient obstacle après deux observations, libre après deux aussi. */
    const val HIT = 0.85f
    const val MISS = -0.4f
    const val LIMIT = 4f
    const val OCCUPIED_ABOVE = 1.2f
    const val FREE_BELOW = -0.7f

    /**
     * Sans plan détecté : le sol est la hauteur la plus fréquente (tranches de 5 cm) des points vus
     * nettement sous la caméra et pas trop loin. Tant qu'elle n'est pas établie, rien n'est cartographié.
     */
    const val FLOOR_SEARCH_DEPTH = 0.6f
    const val FLOOR_SEARCH_RANGE = 3f
    const val FLOOR_BIN = 0.05f
    const val MIN_FLOOR_SUPPORT = 30
}

/**
 * Grille d'occupation vue de dessus, construite à partir de ce que filme la caméra. Pour chaque
 * point vu, les cases entre la caméra et lui sont libres ; s'il est à hauteur d'obstacle, sa case
 * est occupée. Chaque case cumule ces indices (log-odds) : un reflet isolé ne crée pas de mur,
 * et voir à travers une case déjà marquée finit par la libérer. Sûr entre fils d'exécution.
 */
class OccupancyMap(val grid: FloorGrid = FloorGrid(), initial: Map<GridCell, Float> = emptyMap()) {
    private val logOdds = HashMap(initial)
    private val changed = HashSet<GridCell>()
    private var planeFloor: Float? = null
    private val floorHistogram = HashMap<Int, Int>()

    /** Retourne true si l'observation a été prise en compte (le sol doit être connu). */
    @Synchronized
    fun integrate(observation: SceneObservation): Boolean {
        val floorY = floorOf(observation) ?: return false
        val camera = observation.camera
        val origin = grid.cellOf(FloorPoint(camera.x, camera.z))
        // Une seule voix par case et par observation : des centaines de rayons ne valent pas plus qu'un.
        val misses = HashSet<GridCell>()
        val lowest = HashMap<GridCell, Float>()
        val highest = HashMap<GridCell, Float>()
        misses += origin
        for (point in observation.points) {
            val range = hypot(point.x - camera.x, point.z - camera.z)
            if (range > OccupancyRules.MAX_RANGE) continue
            if (hypot(range, point.y - camera.y) < OccupancyRules.MIN_RANGE) continue
            val height = point.y - floorY
            val tolerance = OccupancyRules.FLOOR_TOLERANCE + OccupancyRules.FLOOR_TOLERANCE_PER_METER * range
            val target = grid.cellOf(FloorPoint(point.x, point.z))
            when {
                abs(height) <= tolerance -> traverse(origin, target, includeEnd = true, into = misses)
                height >= maxOf(OccupancyRules.OBSTACLE_MIN_HEIGHT, tolerance + OccupancyRules.OBSTACLE_MARGIN) &&
                    height <= OccupancyRules.OBSTACLE_MAX_HEIGHT -> {
                    traverse(origin, target, includeEnd = false, into = misses)
                    lowest[target] = minOf(lowest[target] ?: height, height)
                    highest[target] = maxOf(highest[target] ?: height, height)
                }
                else -> Unit
            }
        }
        val hits = lowest.keys.filterTo(HashSet()) { highest.getValue(it) - lowest.getValue(it) >= OccupancyRules.MIN_WALL_EXTENT }
        hits.forEach { update(it, OccupancyRules.HIT) }
        misses.forEach { if (it !in hits) update(it, OccupancyRules.MISS) }
        return true
    }

    @Synchronized
    fun snapshot(): Occupancy {
        val free = HashSet<GridCell>()
        val occupied = HashSet<GridCell>()
        for ((cell, value) in logOdds) {
            when {
                value > OccupancyRules.OCCUPIED_ABOVE -> occupied += cell
                value < OccupancyRules.FREE_BELOW -> free += cell
            }
        }
        return Occupancy(free, occupied)
    }

    /** Cases modifiées depuis le dernier appel, avec leur valeur, à sauvegarder. */
    @Synchronized
    fun drainChanges(): Map<GridCell, Float> = changed.associateWith { logOdds.getValue(it) }.also { changed.clear() }

    /** Après un échec d'écriture : ces cases seront sauvegardées au prochain appel. */
    @Synchronized
    fun restoreChanges(cells: Collection<GridCell>) {
        changed += cells
    }

    private fun update(cell: GridCell, delta: Float) {
        val next = ((logOdds[cell] ?: 0f) + delta).coerceIn(-OccupancyRules.LIMIT, OccupancyRules.LIMIT)
        if (next != logOdds[cell]) {
            logOdds[cell] = next
            changed += cell
        }
    }

    /** Sol : plan ARCore s'il y en a un, sinon la hauteur la plus fréquente des points bas ; null tant qu'inconnu. */
    private fun floorOf(observation: SceneObservation): Float? {
        observation.floorY?.let { planeFloor = it }
        planeFloor?.let { return it }
        val camera = observation.camera
        val ceiling = camera.y - OccupancyRules.FLOOR_SEARCH_DEPTH
        for (point in observation.points) {
            if (point.y >= ceiling) continue
            if (hypot(point.x - camera.x, point.z - camera.z) > OccupancyRules.FLOOR_SEARCH_RANGE) continue
            val bin = floor(point.y / OccupancyRules.FLOOR_BIN).toInt()
            floorHistogram[bin] = (floorHistogram[bin] ?: 0) + 1
        }
        val best = floorHistogram.entries
            .filter { (it.key + 1) * OccupancyRules.FLOOR_BIN <= ceiling }
            .maxByOrNull { it.value }
            ?: return null
        if (best.value < OccupancyRules.MIN_FLOOR_SUPPORT) return null
        return (best.key + 0.5f) * OccupancyRules.FLOOR_BIN
    }

    /** Cases traversées par la ligne de vue (Bresenham sur les indices de case). */
    private fun traverse(from: GridCell, to: GridCell, includeEnd: Boolean, into: MutableSet<GridCell>) {
        var col = from.col
        var row = from.row
        val dCol = abs(to.col - from.col)
        val dRow = -abs(to.row - from.row)
        val stepCol = if (from.col < to.col) 1 else -1
        val stepRow = if (from.row < to.row) 1 else -1
        var error = dCol + dRow
        while (true) {
            val atEnd = col == to.col && row == to.row
            if (!atEnd || includeEnd) into += GridCell(col, row)
            if (atEnd) return
            val doubled = 2 * error
            if (doubled >= dRow) {
                error += dRow
                col += stepCol
            }
            if (doubled <= dCol) {
                error += dCol
                row += stepRow
            }
        }
    }
}

/**
 * Zone où quelqu'un peut se trouver : cases sous la trajectoire (élargie de
 * [MapRules.ACCESS_RADIUS]) et sol vu libre, moins les obstacles. Une case par laquelle on est
 * passé n'est jamais un obstacle.
 */
fun mapArea(segments: List<List<FloorPoint>>, occupancy: Occupancy, grid: FloorGrid): Set<GridCell> {
    val walked = pathCells(segments, grid)
    val obstacles = occupancy.occupied - walked
    return (accessibleArea(segments, grid) + occupancy.free) - obstacles
}

/** Cases traversées par la trajectoire elle-même. */
fun pathCells(segments: List<List<FloorPoint>>, grid: FloorGrid): Set<GridCell> {
    val cells = HashSet<GridCell>()
    val step = grid.cellSize / 2
    for (segment in segments) {
        segment.forEachIndexed { index, point ->
            cells += grid.cellOf(point)
            val previous = segment.getOrNull(index - 1) ?: return@forEachIndexed
            val length = previous.distanceTo(point)
            if (length > MapRules.MAX_JOIN) return@forEachIndexed
            val steps = max(1, ceil(length / step).toInt())
            for (k in 1 until steps) {
                val t = k.toFloat() / steps
                cells += grid.cellOf(FloorPoint(previous.x + (point.x - previous.x) * t, previous.z + (point.z - previous.z) * t))
            }
        }
    }
    return cells
}
