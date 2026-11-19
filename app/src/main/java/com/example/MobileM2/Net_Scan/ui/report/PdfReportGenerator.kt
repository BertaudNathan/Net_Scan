package com.example.MobileM2.Net_Scan.ui.report

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.MobileM2.Net_Scan.R
import com.example.MobileM2.Net_Scan.domain.FloorMap
import com.example.MobileM2.Net_Scan.domain.FloorPlan
import com.example.MobileM2.Net_Scan.domain.FloorPoint
import com.example.MobileM2.Net_Scan.domain.MapRules
import com.example.MobileM2.Net_Scan.domain.NetScanMode
import com.example.MobileM2.Net_Scan.domain.NetworkTechnology
import com.example.MobileM2.Net_Scan.domain.ReportGenerator
import com.example.MobileM2.Net_Scan.domain.ReportPoint
import com.example.MobileM2.Net_Scan.domain.SignalLevel
import com.example.MobileM2.Net_Scan.domain.VisitReport
import com.example.MobileM2.Net_Scan.domain.reportFileName
import com.example.MobileM2.Net_Scan.ui.mapscan.MapBounds
import com.example.MobileM2.Net_Scan.ui.mapscan.MapColors
import com.example.MobileM2.Net_Scan.ui.mapscan.MapTransform
import com.example.MobileM2.Net_Scan.ui.mapscan.formatDuration
import com.example.MobileM2.Net_Scan.ui.mapscan.levelColors
import com.example.MobileM2.Net_Scan.ui.mapscan.levelName
import com.example.MobileM2.Net_Scan.ui.mapscan.planHeatBitmap
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dossier du cache partagé par le FileProvider (voir res/xml/file_paths.xml). */
internal const val REPORT_DIRECTORY = "reports"

/**
 * Rapport PDF A4 généré hors ligne avec [PdfDocument] : synthèse client, plan importé avec sa
 * heatmap (ou cadre à compléter sans plan) et placement de l'antenne, tableau des mesures et
 * annexe technique. Seul le dernier rapport est
 * gardé dans le cache de l'application.
 */
class PdfReportGenerator(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ReportGenerator {
    override suspend fun generate(report: VisitReport): File = withContext(io) {
        val directory = File(context.cacheDir, REPORT_DIRECTORY).apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(report.generatedAt))
        val file = File(directory, reportFileName(report.visit.building, date))
        val document = PdfDocument()
        try {
            ReportLayout(document, context.resources).draw(report)
            file.outputStream().use { document.writeTo(it) }
        } catch (error: Exception) {
            file.delete()
            throw error
        } finally {
            document.close()
        }
        file
    }
}

private object Pdf {
    // A4 en points PostScript (1/72 de pouce).
    const val WIDTH = 595
    const val HEIGHT = 842
    const val MARGIN = 40f
    const val FOOTER = 24f
    const val CONTENT_WIDTH = WIDTH - 2 * MARGIN
    const val CELL_PADDING = 5f

    /** Hauteur maximale du plan sur sa page, pour laisser la place au placement de l'antenne. */
    const val PLAN_MAX_HEIGHT = 400f

    const val NAVY = 0xFF0F056B.toInt()
    const val GREEN = 0xFFA7D7A9.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val TEXT = 0xFF1F2328.toInt()
    const val MUTED = 0xFF535D58.toInt()
    const val LINE = 0xFFB8BFBA.toInt()
    const val ROW = 0xFFF2F5F2.toInt()
}

private class ReportLayout(private val document: PdfDocument, private val res: Resources) {
    private var page: PdfDocument.Page? = null
    private var pageNumber = 0
    private var y = 0f
    private val canvas: Canvas get() = checkNotNull(page).canvas
    private val dateTime = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    private val dateOnly = DateFormat.getDateInstance(DateFormat.LONG)

    private val title = text(20f, Pdf.WHITE, bold = true)
    private val kicker = text(9f, Pdf.GREEN, bold = true)
    private val heading = text(14f, Pdf.NAVY, bold = true)
    private val label = text(9f, Pdf.MUTED, bold = true)
    private val body = text(10f, Pdf.TEXT)
    private val small = text(8f, Pdf.MUTED)
    private val cellHeader = text(8.5f, Pdf.WHITE, bold = true)
    private val cell = text(8.5f, Pdf.TEXT)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = Pdf.LINE }

    fun draw(report: VisitReport) {
        coverAndSummary(report)
        planPage(report)
        measures(report)
        technicalAnnex(report)
        finishPage()
    }

    // ---- Page 1 : informations de la visite et synthèse client -----------------------------

    private fun coverAndSummary(report: VisitReport) {
        newPage()
        val bandHeight = 96f
        fill.color = Pdf.NAVY
        canvas.drawRect(0f, 0f, Pdf.WIDTH.toFloat(), bandHeight, fill)
        y = 26f
        block(res.getString(R.string.brand_kicker), kicker)
        y += 4f
        block(res.getString(R.string.report_title_with_building, report.visit.building), title, maxLines = 2)
        y = bandHeight + 22f

        val missing = res.getString(R.string.report_not_provided)
        info(R.string.report_client, report.visit.client.ifBlank { missing })
        info(R.string.report_address, report.visit.address.ifBlank { missing })
        info(R.string.report_visit_date, dateOnly.format(Date(report.visit.createdAt)))
        info(R.string.report_generated, dateTime.format(Date(report.generatedAt)))
        info(R.string.report_operators, report.operators.joinToString(", ").ifEmpty { missing })
        info(R.string.report_point_count, report.points.size.toString())

        y += 12f
        section(R.string.report_summary)
        levelTiles(report)
        y += 10f
        val conclusion = when {
            report.isEmpty -> res.getString(R.string.report_no_points)
            report.weakPoints.isEmpty() -> res.getString(R.string.report_no_weak_points)
            else -> res.getString(R.string.report_weak_points, report.weakPoints.joinToString(", ") { it.label })
        }
        block(conclusion, body)
        y += 10f
        block(res.getString(R.string.report_thresholds), small)
    }

    private fun info(labelRes: Int, value: String) {
        val labelWidth = 130f
        val labelHeight = layout(res.getString(labelRes), label, labelWidth).height
        val valueLayout = layout(value, body, Pdf.CONTENT_WIDTH - labelWidth)
        val height = maxOf(labelHeight, valueLayout.height).toFloat() + 6f
        ensure(height)
        drawLayout(layout(res.getString(labelRes), label, labelWidth), Pdf.MARGIN, y + 1f)
        drawLayout(valueLayout, Pdf.MARGIN + labelWidth, y)
        y += height
    }

    private fun levelTiles(report: VisitReport) {
        val gap = 8f
        val width = (Pdf.CONTENT_WIDTH - gap * 3) / 4
        val height = 62f
        ensure(height)
        // Du meilleur au pire, comme une lecture de gauche à droite.
        SignalLevel.entries.reversed().forEachIndexed { index, level ->
            val left = Pdf.MARGIN + index * (width + gap)
            val colors = levelColors(level)
            fill.color = colors.container.toArgb()
            canvas.drawRoundRect(RectF(left, y, left + width, y + height), 6f, 6f, fill)
            val count = text(20f, colors.content.toArgb(), bold = true)
            val name = text(9f, colors.content.toArgb(), bold = true)
            drawLayout(layout(report.levelCounts.getValue(level).toString(), count, width, Layout.Alignment.ALIGN_CENTER), left, y + 8f)
            drawLayout(layout(res.getString(levelName(level)), name, width, Layout.Alignment.ALIGN_CENTER), left, y + 40f)
        }
        y += height
    }

    // ---- Page 2 : plan ou carte ARCore, et placement de l'antenne ---------------------------

    private fun planPage(report: VisitReport) {
        newPage()
        section(R.string.report_plan_title)
        val plan = report.plan
        val image = plan?.let { loadPlan(it.imagePath) }
        val recording = report.recordingMap
        val mapped = when {
            plan != null && image != null -> {
                importedPlan(report, plan, image)
                true
            }
            recording != null -> {
                recordingMap(report, recording)
                true
            }
            else -> {
                emptyPlanFrame()
                false
            }
        }

        // Sans carte dessinée (plan illisible compris), tous les points sont à reporter à la main.
        val toReport = if (mapped) report.unplacedPoints else report.points
        if (toReport.isNotEmpty()) {
            val legend = if (toReport == report.points) R.string.report_points_legend else R.string.report_points_unplaced
            block(res.getString(legend), label)
            y += 4f
            pointMarkers(toReport)
            y += 12f
        }

        section(R.string.report_antenna_title)
        listOf(
            R.string.report_antenna_location,
            R.string.report_antenna_height,
            R.string.report_antenna_power,
            R.string.report_antenna_notes,
        ).forEach { writeLine(it) }
    }

    /** Cadre vide à compléter à la main, quand la visite n'a pas de plan ou qu'il est illisible. */
    private fun emptyPlanFrame() {
        block(res.getString(R.string.report_plan_instructions), body)
        y += 8f
        val frameHeight = 360f
        ensure(frameHeight)
        val frame = RectF(Pdf.MARGIN, y, Pdf.MARGIN + Pdf.CONTENT_WIDTH, y + frameHeight)
        val dashed = Paint(stroke).apply { strokeWidth = 1.2f; color = Pdf.NAVY; pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f) }
        canvas.drawRoundRect(frame, 6f, 6f, dashed)
        val placeholder = layout(res.getString(R.string.report_plan_placeholder), text(12f, Pdf.MUTED), Pdf.CONTENT_WIDTH, Layout.Alignment.ALIGN_CENTER)
        drawLayout(placeholder, Pdf.MARGIN, frame.centerY() - placeholder.height / 2f)
        y += frameHeight + 14f
    }

    /** Plan importé, heatmap par-dessus et points numérotés à leur place, comme à l'écran. */
    private fun importedPlan(report: VisitReport, plan: FloorPlan, image: Bitmap) {
        val map = report.planMap
        val intro = if (map != null) {
            res.getString(R.string.report_plan_heatmap_intro, MapRules.ESTIMATE_RADIUS.toInt())
        } else {
            res.getString(R.string.report_plan_no_scale)
        }
        block(intro, body)
        y += 8f

        val fit = min(Pdf.CONTENT_WIDTH / plan.widthPx, Pdf.PLAN_MAX_HEIGHT / plan.heightPx)
        val width = plan.widthPx * fit
        val height = plan.heightPx * fit
        ensure(height)
        val left = Pdf.MARGIN + (Pdf.CONTENT_WIDTH - width) / 2
        val area = RectF(left, y, left + width, y + height)
        canvas.drawBitmap(image, null, area, Paint(Paint.FILTER_BITMAP_FLAG))

        val scale = plan.metersPerPixel
        val heat = if (map != null && scale != null) planHeatBitmap(map, plan) else null
        if (map != null && scale != null && heat != null) {
            // Une case = un carré net : l'image est agrandie sans lissage avant d'être posée sur la page.
            val factor = (HEAT_MAX_SIDE / maxOf(heat.width, heat.height)).coerceIn(1, HEAT_UPSCALE)
            val sharp = Bitmap.createScaledBitmap(heat, heat.width * factor, heat.height * factor, false)
            val cellPoints = map.heatmap.grid.cellSize / scale * fit
            canvas.save()
            canvas.clipRect(area)
            canvas.drawBitmap(sharp, null, RectF(left, area.top, left + heat.width * cellPoints, area.top + heat.height * cellPoints), Paint())
            canvas.restore()
        }
        canvas.drawRect(area, stroke)

        report.placedPoints.forEach { point ->
            val position = checkNotNull(point.scan.planPosition)
            planMarker(point, left + position.u * width, area.top + position.v * height)
        }
        y += height + 14f
    }

    /**
     * Carte du dernier parcours ARCore vue de dessus, dessinée comme à l'écran en thème clair :
     * heatmap, sol vu sans estimation hachuré, obstacles, trajet et points numérotés.
     */
    private fun recordingMap(report: VisitReport, map: FloorMap) {
        block(res.getString(R.string.report_recording_intro, MapRules.ESTIMATE_RADIUS.toInt()), body)
        y += 8f

        val bounds = MapBounds.of(map)
        val scale = min(Pdf.CONTENT_WIDTH / bounds.extentX, Pdf.PLAN_MAX_HEIGHT / bounds.extentZ)
        val height = bounds.extentZ * scale
        ensure(height + SCALE_BAR_SPACE)
        val area = RectF(Pdf.MARGIN, y, Pdf.MARGIN + Pdf.CONTENT_WIDTH, y + height)
        val transform = MapTransform.fit(map, area.right, area.bottom, area.left, area.top, 0f, 0f)
        fun at(point: FloorPoint) = transform.toScreen(point)

        val surface = Color(Pdf.WHITE)
        val outline = Color(Pdf.MUTED)
        val cellSize = map.heatmap.grid.cellSize
        val cellPoints = cellSize * transform.scale
        // Léger recouvrement : pas de jointure visible entre cases voisines, toutes opaques.
        val tile = cellPoints + 0.4f
        canvas.save()
        canvas.clipRect(area)
        for (cell in map.heatmap.area) {
            val value = map.heatmap.valueAt(cell)
            fill.color = (if (value == null) MapColors.unexplored(surface, outline) else MapColors.cell(surface, value)).toArgb()
            val corner = at(FloorPoint(cell.col * cellSize, cell.row * cellSize))
            canvas.drawRect(corner.x, corner.y, corner.x + tile, corner.y + tile, fill)
        }
        val hatch = Paint(stroke).apply { color = MapColors.hatch(outline).toArgb(); strokeWidth = 0.4f }
        for (cell in map.heatmap.area) {
            if (map.heatmap.valueAt(cell) != null) continue
            val corner = at(FloorPoint(cell.col * cellSize, cell.row * cellSize))
            canvas.drawLine(corner.x, corner.y + cellPoints, corner.x + cellPoints, corner.y, hatch)
        }
        fill.color = MapColors.obstacle(surface, Color(Pdf.TEXT)).toArgb()
        for (cell in map.obstacles) {
            val corner = at(FloorPoint(cell.col * cellSize, cell.row * cellSize))
            canvas.drawRect(corner.x, corner.y, corner.x + tile, corner.y + tile, fill)
        }
        val trail = Paint(stroke).apply {
            color = Pdf.TEXT
            strokeWidth = 1.2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (segment in map.segments) {
            if (segment.size < 2) continue
            val path = Path()
            segment.forEachIndexed { index, point ->
                val screen = at(point)
                val jump = index > 0 && segment[index - 1].distanceTo(point) > MapRules.MAX_JOIN
                if (index == 0 || jump) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
            }
            canvas.drawPath(path, trail)
        }
        canvas.restore()
        canvas.drawRect(area, stroke)

        report.placedPoints.forEach { point ->
            val center = at(checkNotNull(point.scan.position))
            planMarker(point, center.x, center.y)
        }
        y += height + 6f
        scaleBar(transform.scale)
        y += 14f
    }

    /** Barre d'échelle sous la carte : 1, 2, 5, 10… mètres, la plus courte qui mesure au moins 40 pt. */
    private fun scaleBar(pointsPerMeter: Float) {
        val meters = SCALE_STEPS.firstOrNull { it * pointsPerMeter >= 40f } ?: SCALE_STEPS.last()
        val length = meters * pointsPerMeter
        val bar = Paint(stroke).apply { color = Pdf.TEXT; strokeWidth = 1.2f }
        val baseline = y + 6f
        canvas.drawLine(Pdf.MARGIN, baseline, Pdf.MARGIN + length, baseline, bar)
        canvas.drawLine(Pdf.MARGIN, baseline - 3f, Pdf.MARGIN, baseline + 3f, bar)
        canvas.drawLine(Pdf.MARGIN + length, baseline - 3f, Pdf.MARGIN + length, baseline + 3f, bar)
        val caption = layout(res.getString(R.string.report_scale_meters, meters), small, 80f)
        drawLayout(caption, Pdf.MARGIN + length + 6f, baseline - caption.height / 2f)
        y += SCALE_BAR_SPACE - 6f
    }

    /** Pastille numérotée posée sur le plan, cerclée pour rester lisible sur la heatmap. */
    private fun planMarker(point: ReportPoint, x: Float, centerY: Float) {
        val radius = 8f
        val colors = levelColors(point.scan.summary.level)
        fill.color = colors.container.toArgb()
        canvas.drawCircle(x, centerY, radius, fill)
        canvas.drawCircle(x, centerY, radius, Paint(stroke).apply { color = Pdf.NAVY; strokeWidth = 1f })
        val marker = layout(point.label, text(6.5f, colors.content.toArgb(), bold = true), radius * 2, Layout.Alignment.ALIGN_CENTER)
        drawLayout(marker, x - radius, centerY - marker.height / 2f)
    }

    /** Plan réduit à une résolution d'impression, pour garder un PDF léger. Null s'il est illisible. */
    private fun loadPlan(path: String): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PLAN_MAX_PIXELS) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (error: Exception) {
        null
    }

    /** Pastilles « P1 », « P2 »… à la couleur du niveau, à recopier sur le plan. */
    private fun pointMarkers(points: List<ReportPoint>) {
        val size = 30f
        val gap = 8f
        val perRow = ((Pdf.CONTENT_WIDTH + gap) / (size + gap)).toInt()
        points.chunked(perRow).forEach { row ->
            ensure(size)
            row.forEachIndexed { index, point ->
                val left = Pdf.MARGIN + index * (size + gap)
                val colors = levelColors(point.scan.summary.level)
                fill.color = colors.container.toArgb()
                canvas.drawCircle(left + size / 2, y + size / 2, size / 2, fill)
                val marker = layout(point.label, text(8.5f, colors.content.toArgb(), bold = true), size, Layout.Alignment.ALIGN_CENTER)
                drawLayout(marker, left, y + (size - marker.height) / 2f)
            }
            y += size + gap
        }
    }

    private fun writeLine(labelRes: Int) {
        val height = 26f
        ensure(height)
        val labelLayout = layout(res.getString(labelRes), label, 150f)
        drawLayout(labelLayout, Pdf.MARGIN, y + height - labelLayout.height - 4f)
        canvas.drawLine(Pdf.MARGIN + 150f, y + height - 4f, Pdf.MARGIN + Pdf.CONTENT_WIDTH, y + height - 4f, stroke)
        y += height
    }

    // ---- Tableaux ---------------------------------------------------------------------------

    private fun measures(report: VisitReport) {
        newPage()
        section(R.string.report_measures_title)
        if (report.isEmpty) {
            block(res.getString(R.string.report_no_points), body)
            return
        }
        val unknown = res.getString(R.string.report_missing_value)
        table(
            headers = listOf(R.string.report_col_point, R.string.report_col_date, R.string.report_col_operator,
                R.string.report_col_technology, R.string.report_col_level, R.string.report_col_duration),
            weights = listOf(0.8f, 2.2f, 1.8f, 1f, 1.3f, 1f),
            rows = report.points.map { point ->
                val scan = point.scan
                listOf(
                    Cell(point.label),
                    Cell(dateTime.format(Date(scan.startedAt))),
                    Cell(scan.operatorName?.takeIf(String::isNotBlank) ?: unknown),
                    Cell(technology(scan.summary.technology) ?: unknown),
                    Cell(res.getString(levelName(scan.summary.level)), scan.summary.level),
                    Cell(formatDuration(scan.durationSeconds)),
                )
            },
        )
    }

    private fun technicalAnnex(report: VisitReport) {
        if (report.isEmpty) return
        y += 18f
        ensure(80f)
        section(R.string.report_annex_title)
        block(res.getString(R.string.report_annex_intro), small)
        y += 6f
        val unknown = res.getString(R.string.report_missing_value)
        table(
            headers = listOf(R.string.report_col_point, R.string.report_col_rsrp, R.string.report_col_sinr,
                R.string.report_col_band, R.string.report_col_cell, R.string.report_col_samples, R.string.report_col_mode),
            weights = listOf(0.8f, 1.2f, 1f, 0.8f, 1.5f, 0.9f, 1.6f),
            rows = report.points.map { point ->
                val summary = point.scan.summary
                listOf(
                    Cell(point.label),
                    Cell(summary.rsrpDbm?.let { res.getString(R.string.unit_dbm, decimal(it)) } ?: unknown),
                    Cell(summary.sinrDb?.let { res.getString(R.string.unit_db, decimal(it)) } ?: unknown),
                    Cell(summary.band?.toString() ?: unknown),
                    Cell(summary.cellId?.toString() ?: unknown),
                    Cell(summary.sampleCount.toString()),
                    Cell(mode(point.scan.mode)),
                )
            },
        )
    }

    private class Cell(val text: String, val level: SignalLevel? = null)

    private fun table(headers: List<Int>, weights: List<Float>, rows: List<List<Cell>>) {
        val total = weights.sum()
        val widths = weights.map { Pdf.CONTENT_WIDTH * it / total }
        fun drawHeader() {
            val layouts = headers.mapIndexed { i, h -> layout(res.getString(h), cellHeader, widths[i] - 2 * Pdf.CELL_PADDING) }
            val height = layouts.maxOf { it.height } + 2 * Pdf.CELL_PADDING
            ensure(height + 20f)
            fill.color = Pdf.NAVY
            canvas.drawRect(Pdf.MARGIN, y, Pdf.MARGIN + Pdf.CONTENT_WIDTH, y + height, fill)
            var x = Pdf.MARGIN
            layouts.forEachIndexed { i, l -> drawLayout(l, x + Pdf.CELL_PADDING, y + Pdf.CELL_PADDING); x += widths[i] }
            y += height
        }
        drawHeader()
        rows.forEachIndexed { index, row ->
            val layouts = row.mapIndexed { i, c ->
                val paint = c.level?.let { text(8.5f, levelColors(it).content.toArgb(), bold = true) } ?: cell
                layout(c.text, paint, widths[i] - 2 * Pdf.CELL_PADDING)
            }
            val height = layouts.maxOf { it.height } + 2 * Pdf.CELL_PADDING
            if (y + height > bottom()) {
                newPage()
                drawHeader()
            }
            if (index % 2 == 1) {
                fill.color = Pdf.ROW
                canvas.drawRect(Pdf.MARGIN, y, Pdf.MARGIN + Pdf.CONTENT_WIDTH, y + height, fill)
            }
            var x = Pdf.MARGIN
            row.forEachIndexed { i, c ->
                c.level?.let {
                    fill.color = levelColors(it).container.toArgb()
                    canvas.drawRect(x + 2f, y + 2f, x + widths[i] - 2f, y + height - 2f, fill)
                }
                drawLayout(layouts[i], x + Pdf.CELL_PADDING, y + Pdf.CELL_PADDING)
                x += widths[i]
            }
            canvas.drawLine(Pdf.MARGIN, y + height, Pdf.MARGIN + Pdf.CONTENT_WIDTH, y + height, stroke)
            y += height
        }
    }

    // ---- Mise en page -----------------------------------------------------------------------

    private fun section(titleRes: Int) {
        ensure(40f)
        block(res.getString(titleRes), heading)
        fill.color = Pdf.GREEN
        canvas.drawRect(Pdf.MARGIN, y + 2f, Pdf.MARGIN + 48f, y + 5f, fill)
        y += 14f
    }

    /** Paragraphe sur toute la largeur ; passe à la page suivante s'il ne tient pas. */
    private fun block(value: String, paint: TextPaint, maxLines: Int = Int.MAX_VALUE) {
        val l = layout(value, paint, Pdf.CONTENT_WIDTH, maxLines = maxLines)
        ensure(l.height.toFloat())
        drawLayout(l, Pdf.MARGIN, y)
        y += l.height
    }

    private fun bottom() = Pdf.HEIGHT - Pdf.MARGIN - Pdf.FOOTER

    private fun ensure(height: Float) {
        if (y + height > bottom()) newPage()
    }

    private fun newPage() {
        finishPage()
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(Pdf.WIDTH, Pdf.HEIGHT, pageNumber).create())
        y = Pdf.MARGIN
    }

    private fun finishPage() {
        val current = page ?: return
        val footer = layout(res.getString(R.string.report_footer, pageNumber), small, Pdf.CONTENT_WIDTH, Layout.Alignment.ALIGN_OPPOSITE)
        drawLayout(footer, Pdf.MARGIN, Pdf.HEIGHT - Pdf.MARGIN - footer.height + 8f)
        document.finishPage(current)
        page = null
    }

    private fun layout(
        value: String,
        paint: TextPaint,
        width: Float,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        maxLines: Int = Int.MAX_VALUE,
    ): StaticLayout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width.toInt().coerceAtLeast(1))
        .setAlignment(alignment)
        .setMaxLines(maxLines)
        .setEllipsize(if (maxLines == Int.MAX_VALUE) null else android.text.TextUtils.TruncateAt.END)
        .build()

    private fun drawLayout(layout: StaticLayout, x: Float, top: Float) {
        canvas.save()
        canvas.translate(x, top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun text(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun technology(value: NetworkTechnology): String? = when (value) {
        NetworkTechnology.GSM -> res.getString(R.string.technology_2g)
        NetworkTechnology.UMTS -> res.getString(R.string.technology_3g)
        NetworkTechnology.LTE -> res.getString(R.string.technology_4g)
        NetworkTechnology.NR -> res.getString(R.string.technology_5g)
        NetworkTechnology.UNKNOWN -> null
    }

    private fun mode(value: NetScanMode): String = when (value) {
        is NetScanMode.Timer -> res.getString(R.string.net_scan_mode_timer, value.seconds)
        NetScanMode.Free -> res.getString(R.string.net_scan_mode_free)
    }

    private fun decimal(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.getDefault(), "%.1f", value)

    private companion object {
        /** Environ 300 dpi sur la largeur utile d'une page A4. */
        const val PLAN_MAX_PIXELS = 2048

        /** Agrandissement de la heatmap avant tracé, borné pour ne pas saturer la mémoire. */
        const val HEAT_UPSCALE = 4
        const val HEAT_MAX_SIDE = 4096

        const val SCALE_BAR_SPACE = 20f
        val SCALE_STEPS = listOf(1, 2, 5, 10, 20, 50, 100)
    }
}
