package com.example.MobileM2.Net_Scan.domain

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Réglages de l'analyse des zones à mesurer, en mode manuel. */
object CoverageRules {
    /** Une case est couverte à moins de cette distance de parcours d'un scan, sans traverser de mur. */
    const val COVERAGE_RADIUS = 5f

    /** Ouverture la plus large prise pour une porte quand on cherche le contour du bâtiment. */
    const val MAX_DOOR_WIDTH = 2f

    /** En dessous de cette surface (m²), l'intérieur trouvé n'est pas un bâtiment : le contour est ouvert. */
    const val MIN_INTERIOR_AREA = 4f

    /** Un scan n'est suggéré que s'il couvre au moins cette surface (m²) encore à mesurer. */
    const val MIN_SUGGESTION_AREA = 2f

    const val MAX_SUGGESTIONS = 20
}

/** Zones du bâtiment qui manquent de scans. */
sealed interface Coverage {
    /** Les murs tracés ne ferment pas le contour du bâtiment : impossible de savoir ce qui est dedans. */
    data object OutlineOpen : Coverage

    /**
     * [interior] : cases du bâtiment ; [uncovered] : celles à plus de [CoverageRules.COVERAGE_RADIUS]
     * de tout scan. [suggestions] : où scanner pour les couvrir, la plus utile d'abord.
     */
    data class Analyzed(val interior: Set<GridCell>, val uncovered: Set<GridCell>, val suggestions: List<PlanPoint>) : Coverage {
        val ratio: Float get() = if (interior.isEmpty()) 1f else 1f - uncovered.size.toFloat() / interior.size

        /** Part couverte en pour cent, arrondie vers le bas : 100 seulement si tout est couvert. */
        val percent: Int get() = floor(ratio * 100).toInt()
    }
}

/**
 * Intérieur du bâtiment : les cases du plan qu'on n'atteint pas depuis son bord sans traverser un
 * mur tracé. Les ouvertures jusqu'à [CoverageRules.MAX_DOOR_WIDTH] comptent comme des portes : on
 * les bouche le temps de chercher le dehors (murs élargis), puis le dehors reprend le pied extérieur
 * des murs. Vide sans échelle ou si le contour est ouvert.
 */
fun buildingInterior(plan: FloorPlan, walls: List<PlanWall>, grid: FloorGrid): Set<GridCell> {
    val (cols, rows) = plan.gridBounds(grid) ?: return emptySet()
    val raster = Raster(cols, rows)
    val wall = BooleanArray(raster.size)
    for (cell in wallCells(plan, walls, grid)) wall[raster.index(cell.col, cell.row)] = true
    val reach = ceil(CoverageRules.MAX_DOOR_WIDTH / 2 / grid.cellSize).toInt()
    val closed = wall.copyOf()
    for (index in wall.indices) {
        if (!wall[index]) continue
        val col = raster.colOf(index)
        val row = raster.rowOf(index)
        for (dc in -reach..reach) {
            for (dr in -reach..reach) {
                if (dc * dc + dr * dr <= reach * reach && raster.contains(col + dc, row + dr)) closed[raster.index(col + dc, row + dr)] = true
            }
        }
    }
    val outside = BooleanArray(raster.size)
    val open = BooleanArray(raster.size) { !closed[it] }
    var frontier = IntList()
    raster.forEachBorder { if (open[it] && !outside[it]) { outside[it] = true; frontier.add(it) } }
    while (frontier.size > 0) {
        val next = IntList()
        frontier.forEach { index -> raster.forEachStep(index, open) { step, _ -> if (!outside[step]) { outside[step] = true; next.add(step) } } }
        frontier = next
    }
    // Le dehors reprend ce que l'élargissement des murs lui avait pris, sans traverser les vrais murs.
    val free = BooleanArray(raster.size) { !wall[it] }
    frontier = IntList()
    for (index in outside.indices) if (outside[index]) frontier.add(index)
    raster.forEachBorder { if (free[it] && !outside[it]) { outside[it] = true; frontier.add(it) } }
    repeat(reach) {
        val next = IntList()
        frontier.forEach { index -> raster.forEachStep(index, free) { step, _ -> if (!outside[step]) { outside[step] = true; next.add(step) } } }
        frontier = next
    }
    val interior = HashSet<GridCell>()
    for (index in free.indices) if (free[index] && !outside[index]) interior += raster.cellOf(index)
    return interior
}

/**
 * Zones du bâtiment à plus de [CoverageRules.COVERAGE_RADIUS] de parcours de tout scan ([scans], en
 * mètres), et scans suggérés pour les couvrir : à chaque fois, l'emplacement qui couvre le plus de
 * surface encore à mesurer. Null sans échelle.
 */
fun analyzeCoverage(plan: FloorPlan, scans: List<FloorPoint>, walls: List<PlanWall>, grid: FloorGrid = FloorGrid()): Coverage? {
    val (cols, rows) = plan.gridBounds(grid) ?: return null
    val interior = buildingInterior(plan, walls, grid)
    val cellArea = grid.cellSize * grid.cellSize
    if (interior.isEmpty() || interior.size * cellArea < CoverageRules.MIN_INTERIOR_AREA) return Coverage.OutlineOpen

    val raster = Raster(cols, rows)
    val passable = BooleanArray(raster.size)
    for (cell in interior) passable[raster.index(cell.col, cell.row)] = true
    // Comme pour la heatmap, un scan hors du bâtiment compte depuis sa case.
    val sources = scans.map(grid::cellOf).filter { raster.contains(it.col, it.row) }.map { raster.index(it.col, it.row) }
    sources.forEach { passable[it] = true }
    val search = BoundedSearch(raster, passable, radius = (CoverageRules.COVERAGE_RADIUS / grid.cellSize * STRAIGHT).roundToInt())

    val remaining = BooleanArray(raster.size)
    for (cell in interior) remaining[raster.index(cell.col, cell.row)] = true
    if (sources.isNotEmpty()) search.from(sources.toIntArray()) { remaining[it] = false }
    val uncovered = interior.filterTo(HashSet()) { remaining[raster.index(it.col, it.row)] }

    val minGain = ceil(CoverageRules.MIN_SUGGESTION_AREA / cellArea).toInt()
    val suggestions = suggestScans(raster, search, remaining, minGain).mapNotNull { plan.toPlan(grid.centerOf(raster.cellOf(it))) }
    return Coverage.Analyzed(interior, uncovered, suggestions)
}

/**
 * Glouton paresseux : l'emplacement qui couvre le plus de cases [remaining], puis on recommence.
 * Chaque emplacement part avec un majorant de son gain (un disque entier) ; un gain ne peut que
 * baisser, on ne le recalcule donc que pour l'emplacement en tête. Les emplacements candidats sont
 * espacés d'une case sur deux : sur un grand plan, des dizaines de milliers de cases sinon.
 */
private fun suggestScans(raster: Raster, search: BoundedSearch, remaining: BooleanArray, minGain: Int): List<Int> {
    fun gain(index: Int): Int {
        var count = 0
        search.from(intArrayOf(index)) { if (remaining[it]) count++ }
        return count
    }
    val queue = PriorityQueue(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first })
    val bound = search.discSize
    for (index in remaining.indices) {
        if (remaining[index] && raster.colOf(index) % 2 == 0 && raster.rowOf(index) % 2 == 0) queue += index to bound
    }
    val chosen = mutableListOf<Int>()
    while (chosen.size < CoverageRules.MAX_SUGGESTIONS) {
        val (index, bound) = queue.poll() ?: break
        if (bound < minGain) break
        val fresh = gain(index)
        if (fresh < (queue.peek()?.second ?: 0)) {
            queue += index to fresh
            continue
        }
        if (fresh < minGain) break
        chosen += index
        search.from(intArrayOf(index)) { remaining[it] = false }
    }
    return chosen
}

/** Grille du plan en tableau : une case par indice, ligne par ligne. */
private class Raster(val cols: Int, val rows: Int) {
    val size = cols * rows

    fun index(col: Int, row: Int) = row * cols + col
    fun colOf(index: Int) = index % cols
    fun rowOf(index: Int) = index / cols
    fun cellOf(index: Int) = GridCell(colOf(index), rowOf(index))
    fun contains(col: Int, row: Int) = col in 0 until cols && row in 0 until rows

    inline fun forEachBorder(action: (Int) -> Unit) {
        for (col in 0 until cols) {
            action(index(col, 0))
            action(index(col, rows - 1))
        }
        for (row in 0 until rows) {
            action(index(0, row))
            action(index(cols - 1, row))
        }
    }

    /** Voisins praticables, sans couper les angles (comme la heatmap), avec le coût du pas. */
    inline fun forEachStep(index: Int, passable: BooleanArray, action: (next: Int, cost: Int) -> Unit) {
        val col = colOf(index)
        val row = rowOf(index)
        for (k in STEP_COLS.indices) {
            val dc = STEP_COLS[k]
            val dr = STEP_ROWS[k]
            if (!contains(col + dc, row + dr)) continue
            val next = index(col + dc, row + dr)
            if (!passable[next]) continue
            val diagonal = dc != 0 && dr != 0
            if (diagonal && (!passable[index(col + dc, row)] || !passable[index(col, row + dr)])) continue
            action(next, if (diagonal) DIAGONAL else STRAIGHT)
        }
    }
}

/**
 * Plus courts chemins jusqu'à [radius] (pas de 10 en ligne droite, 14 en diagonale), par paquets de
 * distance égale. Les tableaux servent d'une recherche à l'autre : des milliers de recherches
 * sur un grand plan restent rapides.
 */
private class BoundedSearch(private val raster: Raster, private val passable: BooleanArray, private val radius: Int) {
    private val distance = IntArray(raster.size) { Int.MAX_VALUE }
    private val touched = IntList()
    private val buckets = Array(radius + 1) { IntList() }

    /** Nombre de cases à portée sans obstacle : aucune recherche n'en trouve plus. */
    val discSize: Int = run {
        val cells = radius / STRAIGHT
        var count = 0
        for (dc in -cells..cells) {
            for (dr in -cells..cells) {
                val straight = maxOf(abs(dc), abs(dr)) - minOf(abs(dc), abs(dr))
                if (straight * STRAIGHT + minOf(abs(dc), abs(dr)) * DIAGONAL <= radius) count++
            }
        }
        count
    }

    fun from(starts: IntArray, visit: (Int) -> Unit) {
        for (start in starts) {
            if (distance[start] == 0) continue
            distance[start] = 0
            touched.add(start)
            buckets[0].add(start)
        }
        for (d in 0..radius) {
            val bucket = buckets[d]
            for (k in 0 until bucket.size) {
                val index = bucket[k]
                if (distance[index] != d) continue
                visit(index)
                raster.forEachStep(index, passable) { next, cost ->
                    val candidate = d + cost
                    if (candidate <= radius && candidate < distance[next]) {
                        if (distance[next] == Int.MAX_VALUE) touched.add(next)
                        distance[next] = candidate
                        buckets[candidate].add(next)
                    }
                }
            }
            bucket.clear()
        }
        touched.forEach { distance[it] = Int.MAX_VALUE }
        touched.clear()
    }
}

/** Liste d'entiers sans boîtes. */
private class IntList {
    private var data = IntArray(16)
    var size = 0
        private set

    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    operator fun get(index: Int) = data[index]

    inline fun forEach(action: (Int) -> Unit) {
        for (k in 0 until size) action(this[k])
    }

    fun clear() {
        size = 0
    }
}

private const val STRAIGHT = 10
private const val DIAGONAL = 14
private val STEP_COLS = intArrayOf(-1, 1, 0, 0, -1, -1, 1, 1)
private val STEP_ROWS = intArrayOf(0, 0, -1, 1, -1, 1, -1, 1)
