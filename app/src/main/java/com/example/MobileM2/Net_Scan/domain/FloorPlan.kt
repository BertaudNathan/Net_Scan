package com.example.MobileM2.Net_Scan.domain

import kotlin.math.ceil
import kotlin.math.hypot
import kotlinx.coroutines.flow.Flow

/**
 * Point du plan, en fraction de sa largeur ([u]) et de sa hauteur ([v]), de 0 à 1 depuis le coin
 * haut gauche. Indépendant de l'échelle : régler l'échelle à nouveau ne déplace pas les mesures.
 */
data class PlanPoint(val u: Float, val v: Float)

/** Scan réseau placé à la main sur le plan. */
data class PlanMeasurement(val point: PlanPoint, val level: SignalLevel)

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

    fun contains(point: PlanPoint) = point.u in 0f..1f && point.v in 0f..1f
}

/** Réglages du mode manuel. */
object PlanRules {
    /** En dessous, deux points d'étalonnage sont trop proches pour donner une échelle fiable. */
    const val MIN_SCALE_PIXELS = 20f

    /** Les plans importés sont réduits à cette taille : lisibles en zoomant, sans saturer la mémoire. */
    const val MAX_IMAGE_SIDE = 2560
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

/**
 * Carte du mode manuel : la heatmap couvre le plan à moins de [MapRules.ESTIMATE_RADIUS] des
 * scans, à vol d'oiseau puisque les murs ne sont pas connus. Rien hors du plan. Null sans échelle.
 */
fun FloorMap.Companion.onPlan(plan: FloorPlan, measurements: List<PlanMeasurement>, grid: FloorGrid = FloorGrid()): FloorMap? {
    val widthMeters = plan.widthMeters ?: return null
    val heightMeters = plan.heightMeters ?: return null
    val placed = measurements.mapNotNull { m -> plan.toMeters(m.point)?.let { MapMeasurement(it, m.level) } }
    val cols = ceil(widthMeters / grid.cellSize).toInt()
    val rows = ceil(heightMeters / grid.cellSize).toInt()
    val near = accessibleArea(placed.map { listOf(it.point) }, grid, radius = MapRules.ESTIMATE_RADIUS)
    val area = near.filterTo(HashSet()) { it.col in 0 until cols && it.row in 0 until rows }
    val heatmap = buildHeatmap(area, placed, grid)
    // Seules les cases estimées sont dessinées : le reste du plan reste visible tel quel.
    return FloorMap(emptyList(), placed, heatmap.copy(area = heatmap.values.keys))
}

interface FloorPlanRepository {
    fun observePlan(visitId: Long): Flow<FloorPlan?>

    /** Enregistre un plan importé ; l'échelle est à régler ensuite. */
    suspend fun savePlan(plan: FloorPlan)

    suspend fun setScale(visitId: Long, metersPerPixel: Float)

    /** Scans de la visite placés à la main sur le plan. */
    fun observePlanMeasurements(visitId: Long): Flow<List<PlanMeasurement>>
}

/** Plan copié dans le stockage de l'application. */
data class ImportedPlan(val imagePath: String, val widthPx: Int, val heightPx: Int)

/** Copie un plan choisi par l'utilisateur (image ou PDF, identifié par [uri]) pour une visite. */
fun interface PlanImporter {
    suspend fun import(visitId: Long, uri: String): ImportedPlan
}
