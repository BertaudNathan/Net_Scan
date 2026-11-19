package com.example.MobileM2.Net_Scan.ui.mapscan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.MobileM2.Net_Scan.R
import com.example.MobileM2.Net_Scan.domain.FloorMap
import com.example.MobileM2.Net_Scan.domain.FloorPoint
import com.example.MobileM2.Net_Scan.domain.FloorPose
import com.example.MobileM2.Net_Scan.domain.HeatCell
import com.example.MobileM2.Net_Scan.domain.MapRules
import com.example.MobileM2.Net_Scan.domain.SignalLevel
import kotlin.math.max
import kotlin.math.min

/**
 * Carte vue de dessus : cases mesurées en couleur pleine, cases estimées atténuées, sol vu sans
 * estimation hachuré, murs et obstacles en plein, trajet, scans réseau et position actuelle. La
 * direction de départ de l'enregistrement pointe vers le haut.
 */
@Composable
internal fun FloorMapView(
    map: FloorMap,
    pose: FloorPose?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val description = mapDescription(map, showsPosition = pose != null)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = description }) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val transform = remember(map, width, height, contentPadding, density, layoutDirection) {
            with(density) {
                MapTransform.fit(
                    map = map,
                    width = width,
                    height = height,
                    left = contentPadding.calculateLeftPadding(layoutDirection).toPx(),
                    top = contentPadding.calculateTopPadding().toPx(),
                    right = contentPadding.calculateRightPadding(layoutDirection).toPx(),
                    bottom = contentPadding.calculateBottomPadding().toPx(),
                )
            }
        }
        // Deux calques : la carte ne se redessine qu'à chaque reconstruction, la position à chaque pas.
        MapLayer(map, transform, Modifier.fillMaxSize())
        if (pose != null) PoseLayer(pose, transform, Modifier.fillMaxSize())
    }
}

@Composable
private fun MapLayer(map: FloorMap, transform: MapTransform, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val surface = colors.surface
    val unexplored = MapColors.unexplored(surface, colors.outline)
    val hatch = MapColors.hatch(colors.outline)
    val obstacle = MapColors.obstacle(surface, colors.onSurface)
    // Le trajet et la position prennent la couleur du texte : le vert de la charte est celui du niveau HAUT.
    val trail = colors.onSurface
    val outline = colors.onSurface
    Canvas(modifier) {
        val cellSize = map.heatmap.grid.cellSize
        val cellPx = cellSize * transform.scale
        // Un pixel de recouvrement évite les jointures visibles entre cases voisines.
        val tile = Size(cellPx + 1f, cellPx + 1f)
        for (cell in map.heatmap.area) {
            val value = map.heatmap.valueAt(cell)
            val color = if (value == null) unexplored else MapColors.cell(surface, value)
            drawRect(color, topLeft = transform.toScreen(FloorPoint(cell.col * cellSize, cell.row * cellSize)), size = tile)
        }
        // Hachures sur les cases inconnues : les diagonales de cases voisines forment des rayures continues.
        val hatchWidth = 1.dp.toPx()
        for (cell in map.heatmap.area) {
            if (map.heatmap.valueAt(cell) != null) continue
            val corner = transform.toScreen(FloorPoint(cell.col * cellSize, cell.row * cellSize))
            drawLine(hatch, Offset(corner.x, corner.y + cellPx), Offset(corner.x + cellPx, corner.y), hatchWidth)
        }
        for (cell in map.obstacles) {
            drawRect(obstacle, topLeft = transform.toScreen(FloorPoint(cell.col * cellSize, cell.row * cellSize)), size = tile)
        }

        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        for (segment in map.segments) {
            if (segment.size < 2) continue
            val path = Path()
            segment.forEachIndexed { index, point ->
                val screen = transform.toScreen(point)
                val jump = index > 0 && segment[index - 1].distanceTo(point) > MapRules.MAX_JOIN
                if (index == 0 || jump) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
            }
            drawPath(path, trail, style = stroke)
        }

        val radius = 7.dp.toPx()
        val ring = Stroke(width = 2.dp.toPx())
        for (measurement in map.measurements) {
            val center = transform.toScreen(measurement.point)
            drawCircle(levelColors(measurement.level).container, radius, center)
            drawCircle(outline, radius, center, style = ring)
        }
    }
}

@Composable
private fun PoseLayer(pose: FloorPose, transform: MapTransform, modifier: Modifier) {
    val marker = MaterialTheme.colorScheme.onSurface
    val halo = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        val center = transform.toScreen(pose.point)
        val radius = 7.dp.toPx()
        rotate(degrees = Math.toDegrees(pose.headingRadians.toDouble()).toFloat(), pivot = center) {
            val arrow = Path().apply {
                moveTo(center.x, center.y - radius * 2.4f)
                lineTo(center.x - radius, center.y - radius * 0.4f)
                lineTo(center.x + radius, center.y - radius * 0.4f)
                close()
            }
            drawPath(arrow, marker)
        }
        drawCircle(halo, radius + 2.dp.toPx(), center)
        drawCircle(marker, radius, center)
    }
}

/** Couleurs de la carte, toutes opaques : les cases peuvent se recouvrir sans se foncer. */
internal object MapColors {
    fun unexplored(surface: Color, outline: Color): Color = lerp(surface, outline, 0.15f)

    fun hatch(outline: Color): Color = outline

    /** Murs et obstacles : presque la couleur du texte, nettement plus sombres (ou clairs) que le sol. */
    fun obstacle(surface: Color, onSurface: Color): Color = lerp(surface, onSurface, 0.8f)

    fun cell(surface: Color, value: HeatCell): Color =
        lerp(surface, levelColors(value.level).container, if (value.measured) 1f else 0.25f + 0.5f * value.confidence)
}

/** Passage des mètres aux pixels : toute la carte tient dans la zone utile, sans déformation. */
@Immutable
internal data class MapTransform(val scale: Float, val originX: Float, val originY: Float) {
    fun toScreen(point: FloorPoint) = Offset(originX + point.x * scale, originY + point.z * scale)

    companion object {
        /** Étendue minimale affichée, pour ne pas zoomer à l'excès au premier pas. */
        const val MIN_EXTENT_METERS = 8f

        fun fit(map: FloorMap, width: Float, height: Float, left: Float, top: Float, right: Float, bottom: Float): MapTransform {
            val bounds = MapBounds.of(map)
            val availableWidth = max(1f, width - left - right)
            val availableHeight = max(1f, height - top - bottom)
            val scale = min(availableWidth / bounds.extentX, availableHeight / bounds.extentZ)
            return MapTransform(
                scale = scale,
                originX = left + availableWidth / 2 - bounds.centerX * scale,
                originY = top + availableHeight / 2 - bounds.centerZ * scale,
            )
        }
    }
}

/** Étendue affichée de la carte, en mètres, au moins [MapTransform.MIN_EXTENT_METERS] de côté. */
internal data class MapBounds(val centerX: Float, val centerZ: Float, val extentX: Float, val extentZ: Float) {
    companion object {
        fun of(map: FloorMap): MapBounds {
            val cellSize = map.heatmap.grid.cellSize
            var minX = Float.MAX_VALUE
            var minZ = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxZ = -Float.MAX_VALUE
            for (cell in map.heatmap.area + map.obstacles) {
                minX = min(minX, cell.col * cellSize)
                minZ = min(minZ, cell.row * cellSize)
                maxX = max(maxX, (cell.col + 1) * cellSize)
                maxZ = max(maxZ, (cell.row + 1) * cellSize)
            }
            if (minX > maxX) {
                minX = 0f
                minZ = 0f
                maxX = 0f
                maxZ = 0f
            }
            return MapBounds(
                centerX = (minX + maxX) / 2,
                centerZ = (minZ + maxZ) / 2,
                extentX = max(maxX - minX, MapTransform.MIN_EXTENT_METERS),
                extentZ = max(maxZ - minZ, MapTransform.MIN_EXTENT_METERS),
            )
        }
    }
}

@Composable
private fun mapDescription(map: FloorMap, showsPosition: Boolean): String {
    val counts = map.measurements.groupingBy { it.level }.eachCount()
    val levels = listOf(SignalLevel.HIGH, SignalLevel.MEDIUM, SignalLevel.LOW, SignalLevel.NONE).mapNotNull { level ->
        counts[level]?.let { stringResource(R.string.map_level_count, stringResource(levelName(level)), it) }
    }
    val measurements = pluralStringResource(R.plurals.map_measurements, map.measurements.size, map.measurements.size)
    val details = if (levels.isEmpty()) measurements else stringResource(R.string.map_measurements_detail, measurements, levels.joinToString())
    val base = stringResource(R.string.map_description, details)
    return if (showsPosition) "$base ${stringResource(R.string.map_position_shown)}" else base
}
