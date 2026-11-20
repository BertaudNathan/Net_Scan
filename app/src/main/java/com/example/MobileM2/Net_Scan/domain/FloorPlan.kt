package com.example.MobileM2.Net_Scan.domain

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.flow.Flow

/**
 * Point du plan, en fraction de sa largeur ([u]) et de sa hauteur ([v]), de 0 à 1 depuis le coin
 * haut gauche. Indépendant de l'échelle : régler l'échelle à nouveau ne déplace pas les mesures.
 */
data class PlanPoint(val u: Float, val v: Float)

/** Scan réseau placé à la main sur le plan ; [scanId] : son identifiant en base, 0 s'il n'est pas connu. */
data class PlanMeasurement(val point: PlanPoint, val level: SignalLevel, val scanId: Long = 0)

/** Mur tracé à la main sur le plan, d'une extrémité à l'autre. */
data class PlanWall(val id: Long, val start: PlanPoint, val end: PlanPoint)

/**
 * Plan importé d'une visite en mode manuel. [metersPerPixel] est null tant que l'échelle n'est pas
 * réglée : sans elle, pas de distance, donc pas de heatmap.
 */
data class FloorPlan(
    val visitId: Long,
    val imagePath: String,
    val widthPx: Int,
    val heightPx: Int,
    val metersPerPixel: Float?,
) {
    val widthMeters: Float? get() = metersPerPixel?.let { widthPx * it }
    val heightMeters: Float? get() = metersPerPixel?.let { heightPx * it }

    /** Position au sol, en mètres depuis le coin haut gauche (x à droite, z vers le bas). */
    fun toMeters(point: PlanPoint): FloorPoint? = metersPerPixel?.let {
        FloorPoint(point.u * widthPx * it, point.v * heightPx * it)
    }

    /** Inverse de [toMeters]. */
    fun toPlan(point: FloorPoint): PlanPoint? = metersPerPixel?.let {
        PlanPoint(point.x / it / widthPx, point.z / it / heightPx)
    }

    fun contains(point: PlanPoint) = point.u in 0f..1f && point.v in 0f..1f
}

/** Réglages du mode manuel. */
object PlanRules {
    /** En dessous, deux points d'étalonnage sont trop proches pour donner une échelle fiable. */
    const val MIN_SCALE_PIXELS = 20f

    /** Les plans importés sont réduits à cette taille : lisibles en zoomant, sans saturer la mémoire. */
    const val MAX_IMAGE_SIDE = 2560

    /** En dessous, les deux extrémités d'un mur sont un même point touché deux fois. */
    const val MIN_WALL_PIXELS = 10f

    /** Un mur à moins de cet angle de l'horizontale ou de la verticale est redressé. */
    const val STRAIGHTEN_DEGREES = 5f
}

/**
 * Échelle (mètres par pixel) à partir de deux points du plan et de la distance réelle qui les
 * sépare, par exemple les deux extrémités d'un mur ou d'une porte. Null si c'est inexploitable.
 */
fun scaleFrom(plan: FloorPlan, a: PlanPoint, b: PlanPoint, meters: Float): Float? {
    val pixels = hypot((a.u - b.u) * plan.widthPx, (a.v - b.v) * plan.heightPx)
    if (pixels < PlanRules.MIN_SCALE_PIXELS || !(meters > 0f) || meters.isInfinite()) return null
    return meters / pixels
}

/** Longueur d'un mur en pixels du plan. */
fun wallPixels(plan: FloorPlan, a: PlanPoint, b: PlanPoint): Float = plan.pixelDistance(a, b)

/**
 * Extrémité d'un mur à partir du point touché [raw], le mur partant de [from] (null pour son
 * début). Dans l'ordre : elle rejoint une extrémité de mur à moins de [snapPixels] ; sinon le mur se
 * redresse à l'horizontale ou à la verticale s'il en est proche, puis rejoint un mur tout proche le
 * long duquel il s'arrête. Sans cela, il resterait un passage entre deux murs qui se touchent.
 */
fun snapWallPoint(plan: FloorPlan, raw: PlanPoint, from: PlanPoint?, walls: List<PlanWall>, snapPixels: Float): PlanPoint {
    val ends = walls.flatMap { listOf(it.start, it.end) }
    ends.minByOrNull { plan.pixelDistance(it, raw) }
        ?.takeIf { plan.pixelDistance(it, raw) <= snapPixels }
        ?.let { return it }
    val straight = if (from == null) raw else straighten(plan, from, raw)
    return walls.map { plan.closestOn(it, straight) }
        .minByOrNull { plan.pixelDistance(it, straight) }
        ?.takeIf { plan.pixelDistance(it, straight) <= snapPixels }
        ?: straight
}

/** Mur le plus proche du point touché, à moins de [tolerancePixels]. */
fun wallNear(plan: FloorPlan, point: PlanPoint, walls: List<PlanWall>, tolerancePixels: Float): PlanWall? =
    walls.minByOrNull { plan.pixelDistance(plan.closestOn(it, point), point) }
        ?.takeIf { plan.pixelDistance(plan.closestOn(it, point), point) <= tolerancePixels }

/** Mesure enregistrée la plus proche du point touché, à moins de [tolerancePixels]. */
fun measurementNear(plan: FloorPlan, point: PlanPoint, measurements: List<PlanMeasurement>, tolerancePixels: Float): PlanMeasurement? =
    measurements.filter { it.scanId != 0L }
        .minByOrNull { plan.pixelDistance(it.point, point) }
        ?.takeIf { plan.pixelDistance(it.point, point) <= tolerancePixels }

/**
 * Cases de la grille traversées par les murs, dans le plan. Les cases d'un même mur se touchent au
 * moins par un coin, ce qui suffit à le rendre infranchissable (voir [buildHeatmap]). Vide sans échelle.
 */
fun wallCells(plan: FloorPlan, walls: List<PlanWall>, grid: FloorGrid): Set<GridCell> {
    val (cols, rows) = plan.gridBounds(grid) ?: return emptySet()
    val cells = HashSet<GridCell>()
    for (wall in walls) {
        val a = plan.toMeters(wall.start) ?: continue
        val b = plan.toMeters(wall.end) ?: continue
        // Un pas d'un quart de case : deux points successifs sont dans la même case ou dans une voisine.
        val steps = max(1, ceil(a.distanceTo(b) / (grid.cellSize / 4)).toInt())
        for (k in 0..steps) {
            val t = k.toFloat() / steps
            val cell = grid.cellOf(FloorPoint(a.x + (b.x - a.x) * t, a.z + (b.z - a.z) * t))
            if (cell.col in 0 until cols && cell.row in 0 until rows) cells += cell
        }
    }
    return cells
}

/**
 * Carte du mode manuel : la heatmap couvre le plan à moins de [MapRules.ESTIMATE_RADIUS] des
 * scans. Les estimations contournent les murs tracés ([walls]) sans les traverser ; sans mur, elles
 * vont à vol d'oiseau. Rien hors du plan. Null sans échelle.
 */
fun FloorMap.Companion.onPlan(
    plan: FloorPlan,
    measurements: List<PlanMeasurement>,
    grid: FloorGrid = FloorGrid(),
    walls: List<PlanWall> = emptyList(),
): FloorMap? {
    val (cols, rows) = plan.gridBounds(grid) ?: return null
    val placed = measurements.mapNotNull { m -> plan.toMeters(m.point)?.let { MapMeasurement(it, m.level, m.scanId) } }
    val blocked = wallCells(plan, walls, grid)
    val near = accessibleArea(placed.map { listOf(it.point) }, grid, radius = MapRules.ESTIMATE_RADIUS)
    val area = near.filterTo(HashSet()) { it.col in 0 until cols && it.row in 0 until rows && it !in blocked }
    val heatmap = buildHeatmap(area, placed, grid)
    // Seules les cases estimées sont dessinées : le reste du plan reste visible tel quel.
    return FloorMap(emptyList(), placed, heatmap.copy(area = heatmap.values.keys), obstacles = blocked)
}

/** Nombre de colonnes et de lignes de la grille qui couvrent le plan ; null sans échelle. */
internal fun FloorPlan.gridBounds(grid: FloorGrid): Pair<Int, Int>? {
    val widthMeters = widthMeters ?: return null
    val heightMeters = heightMeters ?: return null
    return ceil(widthMeters / grid.cellSize).toInt() to ceil(heightMeters / grid.cellSize).toInt()
}

private fun FloorPlan.pixelDistance(a: PlanPoint, b: PlanPoint): Float = hypot((a.u - b.u) * widthPx, (a.v - b.v) * heightPx)

/** Point du mur le plus proche de [point], calculé en pixels : un plan n'est pas carré. */
private fun FloorPlan.closestOn(wall: PlanWall, point: PlanPoint): PlanPoint {
    val dx = (wall.end.u - wall.start.u) * widthPx
    val dy = (wall.end.v - wall.start.v) * heightPx
    val length2 = dx * dx + dy * dy
    if (length2 == 0f) return wall.start
    val px = (point.u - wall.start.u) * widthPx
    val py = (point.v - wall.start.v) * heightPx
    val t = ((px * dx + py * dy) / length2).coerceIn(0f, 1f)
    return PlanPoint(wall.start.u + (wall.end.u - wall.start.u) * t, wall.start.v + (wall.end.v - wall.start.v) * t)
}

private fun straighten(plan: FloorPlan, from: PlanPoint, raw: PlanPoint): PlanPoint {
    val dx = abs(raw.u - from.u) * plan.widthPx
    val dy = abs(raw.v - from.v) * plan.heightPx
    val degrees = Math.toDegrees(atan2(dy, dx).toDouble())
    return when {
        degrees <= PlanRules.STRAIGHTEN_DEGREES -> PlanPoint(raw.u, from.v)
        degrees >= 90 - PlanRules.STRAIGHTEN_DEGREES -> PlanPoint(from.u, raw.v)
        else -> raw
    }
}

interface FloorPlanRepository {
    fun observePlan(visitId: Long): Flow<FloorPlan?>

    /** Enregistre un plan importé ; l'échelle est à régler ensuite. */
    suspend fun savePlan(plan: FloorPlan)

    suspend fun setScale(visitId: Long, metersPerPixel: Float)

    /** Scans de la visite placés à la main sur le plan. */
    fun observePlanMeasurements(visitId: Long): Flow<List<PlanMeasurement>>

    /** Murs tracés sur le plan, du plus ancien au plus récent. */
    fun observeWalls(visitId: Long): Flow<List<PlanWall>>

    /** Ajoute un mur et renvoie son identifiant. */
    suspend fun addWall(visitId: Long, start: PlanPoint, end: PlanPoint): Long

    suspend fun deleteWall(id: Long)
}

/** Plan copié dans le stockage de l'application. */
data class ImportedPlan(val imagePath: String, val widthPx: Int, val heightPx: Int)

/** Copie un plan choisi par l'utilisateur (image ou PDF, identifié par [uri]) pour une visite. */
fun interface PlanImporter {
    suspend fun import(visitId: Long, uri: String): ImportedPlan
}
