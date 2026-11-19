package com.example.MobileM2.Net_Scan.ui.report

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MobileM2.Net_Scan.R
import com.example.MobileM2.Net_Scan.domain.SignalLevel
import com.example.MobileM2.Net_Scan.domain.VisitReport
import com.example.MobileM2.Net_Scan.ui.mapscan.levelColors
import com.example.MobileM2.Net_Scan.ui.mapscan.levelName
import com.example.MobileM2.Net_Scan.ui.theme.Design
import com.example.MobileM2.Net_Scan.ui.theme.PrimaryButton
import com.example.MobileM2.Net_Scan.ui.theme.SecondaryButton
import com.example.MobileM2.Net_Scan.ui.theme.TertiaryButton
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReportScreen(viewModel: ReportViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    BackHandler(onBack = onBack)

    val download = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME)) { uri ->
        if (uri != null) viewModel.exportTo { context.contentResolver.openOutputStream(uri) }
    }

    message?.let { current ->
        val text = stringResource(when (current) {
            ReportMessage.SAVED -> R.string.report_saved
            ReportMessage.SAVE_FAILED -> R.string.report_save_failed
            ReportMessage.NO_APP -> R.string.report_no_app
        })
        LaunchedEffect(current) {
            snackbar.showSnackbar(text)
            viewModel.messageShown()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = Design.contentWidth).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Design.spaceLarge),
                verticalArrangement = Arrangement.spacedBy(Design.spaceLarge),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(Design.touchTarget)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.report_back))
                    }
                    Text(
                        text = stringResource(R.string.report_screen_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = Design.spaceSmall).semantics { heading() },
                    )
                }
                when (val current = state) {
                    ReportState.Loading -> Status(stringResource(R.string.report_generating), loading = true)
                    ReportState.Missing -> Status(stringResource(R.string.report_missing)) {
                        TertiaryButton(onClick = onBack) { Text(stringResource(R.string.close)) }
                    }
                    ReportState.Error -> Status(stringResource(R.string.report_error), error = true) {
                        TertiaryButton(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
                    }
                    is ReportState.Ready -> Ready(
                        state = current,
                        exporting = exporting,
                        onDownload = { download.launch(current.file.name) },
                        onSend = { if (!sendByEmail(context, current)) viewModel.noAppToSend() },
                    )
                }
            }
        }
    }
}

@Composable
private fun Ready(state: ReportState.Ready, exporting: Boolean, onDownload: () -> Unit, onSend: () -> Unit) {
    val report = state.report
    Summary(report)
    if (report.isEmpty) {
        Text(
            text = stringResource(R.string.report_empty_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(Design.spaceSmall)) {
        PrimaryButton(onClick = onDownload, enabled = !exporting, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (exporting) R.string.report_downloading else R.string.report_download))
        }
        SecondaryButton(onClick = onSend, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.report_send))
        }
        Text(
            text = stringResource(R.string.report_plan_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = stringResource(R.string.report_preview_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
    )
    PdfPreview(state.file)
}

@Composable
private fun Summary(report: VisitReport) {
    Surface(
        shape = Design.cardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Design.spaceMedium), verticalArrangement = Arrangement.spacedBy(Design.spaceSmall)) {
            Text(report.visit.building, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (report.visit.client.isNotBlank()) Text(report.visit.client, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = pluralStringResource(R.plurals.report_point_total, report.points.size, report.points.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Design.spaceSmall),
                verticalArrangement = Arrangement.spacedBy(Design.spaceSmall),
            ) {
                SignalLevel.entries.reversed().forEach { level ->
                    val colors = levelColors(level)
                    Text(
                        text = stringResource(R.string.report_level_count, stringResource(levelName(level)), report.levelCounts.getValue(level)),
                        color = colors.content,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.background(colors.container, Design.badgeShape)
                            .padding(horizontal = Design.spaceMedium, vertical = Design.spaceSmall),
                    )
                }
            }
        }
    }
}

/** Aperçu fidèle : les pages du PDF généré, rendues en images. */
@Composable
private fun PdfPreview(file: File) {
    val pages by produceState<PreviewState>(PreviewState.Loading, file) {
        value = try {
            PreviewState.Ready(withContext(Dispatchers.IO) { renderPages(file, PREVIEW_WIDTH_PX) })
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            PreviewState.Error
        }
    }
    when (val current = pages) {
        PreviewState.Loading -> Status(stringResource(R.string.report_preview_loading), loading = true)
        PreviewState.Error -> Status(stringResource(R.string.report_preview_error))
        is PreviewState.Ready -> current.pages.forEachIndexed { index, page ->
            Surface(
                shape = Design.previewShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Image(
                    bitmap = page,
                    contentDescription = stringResource(R.string.report_preview_page, index + 1),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().aspectRatio(page.width.toFloat() / page.height),
                )
            }
        }
    }
}

private sealed interface PreviewState {
    data object Loading : PreviewState
    data object Error : PreviewState
    data class Ready(val pages: List<ImageBitmap>) : PreviewState
}

@Composable
private fun Status(
    text: String,
    loading: Boolean = false,
    error: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Design.spaceLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Design.spaceMedium),
    ) {
        if (loading) CircularProgressIndicator()
        Text(
            text = text,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        action?.invoke()
    }
}

private fun renderPages(file: File, width: Int): List<ImageBitmap> =
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(width, width * page.height / page.width, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            }
        }
    }

/** Ouvre le choix d'application (messagerie, Gmail…) avec le PDF en pièce jointe. */
private fun sendByEmail(context: Context, state: ReportState.Ready): Boolean {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}$REPORT_AUTHORITY_SUFFIX", state.file)
    val building = state.report.visit.building
    val send = Intent(Intent.ACTION_SEND).apply {
        type = PDF_MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.report_email_subject, building))
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.report_email_body, building))
        clipData = ClipData.newRawUri(state.file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.report_send_chooser)))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private const val PDF_MIME = "application/pdf"
private const val PREVIEW_WIDTH_PX = 900

/** Doit correspondre à l'autorité déclarée dans le manifeste. */
internal const val REPORT_AUTHORITY_SUFFIX = ".reports"
