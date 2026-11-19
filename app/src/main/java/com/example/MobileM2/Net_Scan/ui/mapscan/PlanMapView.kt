package com.example.MobileM2.Net_Scan.ui.mapscan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.example.MobileM2.Net_Scan.R
import com.example.MobileM2.Net_Scan.domain.FloorMap
import com.example.MobileM2.Net_Scan.domain.FloorPlan
import com.example.MobileM2.Net_Scan.domain.FloorPoint
import com.example.MobileM2.Net_Scan.domain.PlanPoint
import com.example.MobileM2.Net_Scan.domain.PlanWall
import com.example.MobileM2.Net_Scan.ui.theme.PlanColors
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Plan importé avec la heatmap par-dessus, les murs tracés, les scans placés, la position choisie
 * (cible) et, pendant le réglage de l'échelle, les points d'étalonnage. Pendant le tracé des murs,
 * [wallChain] marque le début du prochain mur et [selectedWall] le mur choisi pour être effacé.
 * Toucher indique un point du plan, avec la tolérance d'un doigt en pixels du plan au zoom actuel ;
 * pincer zoome, glisser déplace, toucher deux fois revient à la vue d'ensemble.
 */
@Composable
internal fun PlanMapView(
    plan: FloorPlan,
    map: FloorMap?,
    position: PlanPoint?,
    calibration: List<PlanPoint>,
    contentPadding: PaddingValues,
    onTap: (point: PlanPoint, tolerancePixels: Float) -> Unit,
    modifier: Modifier = Modifier,
    walls: List<PlanWall> = emptyList(),
    wallChain: PlanPoint? = null,
    selectedWall: Long? = null,
) {
    val image by produceState<ImageBitmap?>(null, plan.imagePath) {
        value = withContext(Dispatchers.IO) { loadPlanImage(plan.imagePath) }
    }
    var zoom by remember(plan.imagePath) { mutableFloatStateOf(1f) }
    var pan by remember(plan.imagePath) { mutableStateOf(Offset.Zero) }
    val currentOnTap by rememberUpdatedState(onTap)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val description = planDescription(map, walls.size, position != null)
    val colors = MaterialTheme.colorScheme
    val surface = colors.surface
    val ink = colors.onSurface
    val heat = remember(map, plan) { map?.let { heatImage(it, plan) } }

    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = description }) {
        val bitmap = image
        if (bitmap == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
            return@BoxWithConstraints
        }
        val left = with(density) { contentPadding.calculateLeftPadding(layoutDirection).toPx() }
        val top = with(density) { contentPadding.calculateTopPadding().toPx() }
        val right = with(density) { contentPadding.calculateRightPadding(layoutDirection).toPx() }
        val bottom = with(density) { contentPadding.calculateBottomPadding().toPx() }
        val availableWidth = (constraints.maxWidth - left - right).coerceAtLeast(1f)
        val availableHeight = (constraints.maxHeight - top - bottom).coerceAtLeast(1f)
        // Vue d'ensemble : tout le plan tient dans la zone utile, sans déformation.
        val fit = min(availableWidth / plan.widthPx, availableHeight / plan.heightPx)
        val origin = Offset(left + (availableWidth - plan.widthPx * fit) / 2, top + (availableHeight - plan.heightPx * fit) / 2)
        fun toCanvas(point: PlanPoint) = Offset(origin.x + point.u * plan.widthPx * fit, origin.y + point.v * plan.heightPx * fit)
        fun toScreen(point: PlanPoint) = toCanvas(point) * zoom + pan
        fun toPlan(screen: Offset): PlanPoint {
            val canvas = (screen - pan) / zoom
            return PlanPoint((canvas.x - origin.x) / (plan.widthPx * fit), (canvas.y - origin.y) / (plan.heightPx * fit))
        }

        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(plan.imagePath) {
                    detectTransformGestures { centroid, panChange, zoomChange, _ ->
                        val next = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                        val applied = next / zoom
                        pan = (pan - centroid) * applied + centroid + panChange
                        zoom = next
                    }
                }
                .pointerInput(plan.imagePath, fit, origin) {
                    detectTapGestures(
                        onDoubleTap = {
                            zoom = 1f
                            pan = Offset.Zero
                        },
                        onTap = { currentOnTap(toPlan(it), TOUCH_TOLERANCE.toPx() / (fit * zoom)) },
                    )
                },
        ) {
            clipRect {
                withTransform({
                    translate(pan.x, pan.y)
                    scale(zoom, zoom, pivot = Offset.Zero)
                }) {
                    drawImage(
                        bitmap,
                        dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
                        dstSize = IntSize((plan.widthPx * fit).roundToInt(), (plan.heightPx * fit).roundToInt()),
                    )
                    val scale = plan.metersPerPixel
                    if (heat != null && map != null && scale != null) {
                        // Une case = un pixel, étiré sans lissage : bords nets et aucune jointure, à tout zoom.
                        val cellPixels = map.heatmap.grid.cellSize / scale * fit
                        clipRect(origin.x, origin.y, origin.x + plan.widthPx * fit, origin.y + plan.heightPx * fit) {
                            drawImage(
                                heat.image,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(heat.cols, heat.rows),
                                dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
                                dstSize = IntSize((heat.cols * cellPixels).roundToInt(), (heat.rows * cellPixels).roundToInt()),
                                filterQuality = FilterQuality.None,
                            )
                        }
                    }
                }

                // Murs et repères à taille constante, quel que soit le zoom. Les liserés d'abord : un
                // mur ne recouvre pas celui qu'il rejoint.
                val wallWidth = 4.dp.toPx()
                for (wall in walls) {
                    val width = if (wall.id == selectedWall) wallWidth * 2 else wallWidth
                    drawLine(PlanColors.casing, toScreen(wall.start), toScreen(wall.end), width + 3.dp.toPx(), StrokeCap.Round)
                }
                for (wall in walls) {
                    val selected = wall.id == selectedWall
                    drawLine(
                        if (selected) PlanColors.selected else PlanColors.wall,
                        toScreen(wall.start),
                        toScreen(wall.end),
                        if (selected) wallWidth * 2 else wallWidth,
                        StrokeCap.Round,
                    )
                }
                wallChain?.let(::toScreen)?.let { center ->
                    drawCircle(PlanColors.casing, 9.dp.toPx(), center)
                    drawCircle(PlanColors.wall, 6.dp.toPx(), center)
                }
                val radius = 7.dp.toPx()
                val ring = Stroke(width = 2.dp.toPx())
                val scale = plan.metersPerPixel
                if (map != null && scale != null) {
                    for (measurement in map.measurements) {
                        val center = toScreen(planPointOf(measurement.point, plan, scale))
                        drawCircle(levelColors(measurement.level).container, radius, center)
                        drawCircle(ink, radius, center, style = ring)
                    }
                }
                calibration.map(::toScreen).let { points ->
                    if (points.size == 2) {
                        drawLine(
                            ink,
                            points[0],
                            points[1],
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                        )
                    }
                    points.forEach { point ->
                        drawCircle(surface, radius + 2.dp.toPx(), point)
                        val arm = radius + 4.dp.toPx()
                        drawLine(ink, point - Offset(arm, 0f), point + Offset(arm, 0f), strokeWidth = 2.dp.toPx())
                        drawLine(ink, point - Offset(0f, arm), point + Offset(0f, arm), strokeWidth = 2.dp.toPx())
                    }
                }
                position?.let(::toScreen)?.let { center ->
                    drawCircle(surface, 14.dp.toPx(), center)
                    drawCircle(ink, 12.dp.toPx(), center, style = Stroke(width = 3.dp.toPx()))
                    drawCircle(ink, 4.dp.toPx(), center)
                }
            }
        }
    }
}

/** Heatmap en image : un pixel par case de la grille, transparent hors de la zone estimée. */
private class HeatImage(val image: ImageBitmap, val cols: Int, val rows: Int)

private fun heatImage(map: FloorMap, plan: FloorPlan): HeatImage? {
    val scale = plan.metersPerPixel ?: return null
    val cellSize = map.heatmap.grid.cellSize
    val cols = ceil(plan.widthPx * scale / cellSize).toInt().coerceAtLeast(1)
    val rows = ceil(plan.heightPx * scale / cellSize).toInt().coerceAtLeast(1)
    val bitmap = createBitmap(cols, rows)
    for ((cell, value) in map.heatmap.values) {
        if (cell.col !in 0 until cols || cell.row !in 0 until rows) continue
        val alpha = if (value.measured) MEASURED_ALPHA else 0.15f + 0.35f * value.confidence
        bitmap[cell.col, cell.row] = levelColors(value.level).container.copy(alpha = alpha).toArgb()
    }
    return HeatImage(bitmap.asImageBitmap(), cols, rows)
}

private fun planPointOf(point: FloorPoint, plan: FloorPlan, metersPerPixel: Float) =
    PlanPoint(point.x / metersPerPixel / plan.widthPx, point.z / metersPerPixel / plan.heightPx)

/** Plan décodé sans transparence : deux fois moins de mémoire, inutile pour un plan. */
private fun loadPlanImage(path: String): ImageBitmap? =
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })?.asImageBitmap()

@Composable
private fun planDescription(map: FloorMap?, wallCount: Int, positioned: Boolean): String {
    val count = map?.measurements?.size ?: 0
    val measurements = pluralStringResource(R.plurals.map_measurements, count, count)
    val walls = pluralStringResource(R.plurals.plan_walls, wallCount, wallCount)
    val position = stringResource(if (positioned) R.string.plan_position_marked else R.string.plan_position_missing)
    return stringResource(R.string.plan_description, stringResource(R.string.plan_contents, measurements, walls), position)
}

private const val MAX_ZOOM = 8f

/** La moitié d'une cible tactile : un doigt couvre au moins cela autour du point touché. */
private val TOUCH_TOLERANCE = 24.dp
private const val MEASURED_ALPHA = 0.6f
