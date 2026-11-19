package com.example.MobileM2.Net_Scan.ui.mapscan

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MobileM2.Net_Scan.R
import com.example.MobileM2.Net_Scan.domain.NetScanMode
import com.example.MobileM2.Net_Scan.domain.NetScanSummary
import com.example.MobileM2.Net_Scan.domain.Handedness
import com.example.MobileM2.Net_Scan.domain.NetworkTechnology
import com.example.MobileM2.Net_Scan.domain.OperatorStatus
import com.example.MobileM2.Net_Scan.domain.SignalLevel
import com.example.MobileM2.Net_Scan.ui.theme.Design
import com.example.MobileM2.Net_Scan.ui.theme.DestructiveButton
import com.example.MobileM2.Net_Scan.ui.theme.LevelColors
import com.example.MobileM2.Net_Scan.ui.theme.PrimaryButton
import com.example.MobileM2.Net_Scan.ui.theme.RecordButton
import com.example.MobileM2.Net_Scan.ui.theme.RecordColors
import com.example.MobileM2.Net_Scan.ui.theme.SecondaryButton
import com.example.MobileM2.Net_Scan.ui.theme.SignalColors
import com.example.MobileM2.Net_Scan.ui.theme.TertiaryButton
import java.util.Locale

private val SIGNAL_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.READ_PHONE_STATE,
)

@Composable
fun MapScanScreen(
    viewModel: MapScanViewModel,
    onClose: () -> Unit,
    onOpenReport: () -> Unit = {},
    handedness: Handedness = Handedness.RIGHT,
    cameraPreview: @Composable (Modifier) -> Unit = { CameraPreview(it) },
) {
    val visit by viewModel.visit.collectAsStateWithLifecycle()
    val operator by viewModel.operator.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val netScan by viewModel.netScan.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var showLeave by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val busy = recording is RecordingState.Recording || netScan is NetScanState.Running
    val context = LocalContext.current

    LockPortrait()
    KeepScreenOn(busy)

    val requestClose = { if (busy) showLeave = true else onClose() }
    BackHandler(onBack = requestClose)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (hasSignalPermissions(context)) viewModel.startNetScan() else viewModel.onPermissionsDenied()
    }
    val startNetScan = {
        if (hasSignalPermissions(context)) viewModel.startNetScan() else permissionLauncher.launch(SIGNAL_PERMISSIONS)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(insets)
                .padding(horizontal = Design.spaceMedium, vertical = Design.spaceSmall),
            verticalArrangement = Arrangement.spacedBy(Design.spaceMedium),
        ) {
            Header(
                title = (visit as? VisitState.Loaded)?.visit?.building,
                operator = operator,
                isSimulated = viewModel.isSimulated,
                handedness = handedness,
                cameraPreview = cameraPreview,
                onClose = requestClose,
            )
            when (val state = visit) {
                VisitState.Loading -> CenteredMessage(stringResource(R.string.visit_loading), Modifier.weight(1f), loading = true)
                VisitState.Missing -> CenteredMessage(stringResource(R.string.visit_missing), Modifier.weight(1f), onClose = onClose)
                VisitState.Error -> CenteredMessage(stringResource(R.string.visit_error), Modifier.weight(1f), onClose = onClose)
                is VisitState.Loaded -> MapArea(
                    mode = mode,
                    netScan = netScan,
                    recording = recording,
                    handedness = handedness,
                    onShowHistory = { showHistory = true },
                    onOpenReport = onOpenReport,
                    actions = MapActions(
                        startNetScan = startNetScan,
                        selectMode = viewModel::selectMode,
                        cancel = viewModel::cancelNetScan,
                        finish = viewModel::finishNetScan,
                        dismiss = viewModel::dismissNetScan,
                        retrySave = viewModel::retrySave,
                        toggleRecording = viewModel::toggleRecording,
                    ),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }

    if (showHistory) {
        ScanHistoryDialog(
            state = history,
            onDismiss = { showHistory = false },
            onRetry = viewModel::retryHistory,
        )
    }

    if (showLeave) {
        AlertDialog(
            onDismissRequest = { showLeave = false },
            title = { Text(stringResource(R.string.leave_visit_title)) },
            text = { Text(stringResource(R.string.leave_visit_message)) },
            confirmButton = {
                DestructiveButton(onClick = {
                    showLeave = false
                    onClose()
                }) { Text(stringResource(R.string.exit)) }
            },
            dismissButton = {
                TertiaryButton(onClick = { showLeave = false }) { Text(stringResource(R.string.stay)) }
            },
        )
    }
}

private class MapActions(
    val startNetScan: () -> Unit,
    val selectMode: (NetScanMode) -> Unit,
    val cancel: () -> Unit,
    val finish: () -> Unit,
    val dismiss: () -> Unit,
    val retrySave: () -> Unit,
    val toggleRecording: () -> Unit,
)

@Composable
private fun Header(
    title: String?,
    operator: OperatorStatus,
    isSimulated: Boolean,
    handedness: Handedness,
    cameraPreview: @Composable (Modifier) -> Unit,
    onClose: () -> Unit,
) {
    val close: @Composable () -> Unit = {
        IconButton(onClick = onClose, modifier = Modifier.size(Design.touchTarget)) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.map_scan_close))
        }
    }
    val preview: @Composable () -> Unit = {
        Box(
            modifier = Modifier.size(Design.previewWidth, Design.previewHeight)
                .clip(Design.previewShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, Design.previewShape),
            contentAlignment = Alignment.Center,
        ) {
            cameraPreview(Modifier.fillMaxSize())
        }
    }
    val closeAtStart = handedness.atStart(LocalLayoutDirection.current)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (closeAtStart) close() else preview()
        Column(
            modifier = Modifier.weight(1f)
                .heightIn(min = Design.previewHeight)
                .padding(horizontal = Design.spaceSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Design.spaceExtraSmall, Alignment.CenterVertically),
        ) {
            Text(
                text = title.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = operatorLabel(operator),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (isSimulated) Badge(stringResource(R.string.simulated_data))
        }
        if (closeAtStart) preview() else close()
    }
}

@Composable
private fun MapArea(
    mode: NetScanMode,
    netScan: NetScanState,
    recording: RecordingState,
    handedness: Handedness,
    onShowHistory: () -> Unit,
    onOpenReport: () -> Unit,
    actions: MapActions,
    modifier: Modifier = Modifier,
) {
    val level = when (netScan) {
        is NetScanState.Running -> netScan.summary?.level
        is NetScanState.Completed -> netScan.result.summary.level
        else -> null
    }
    // Le filtre se colore selon le niveau mesuré ; il reste transparent tant qu'il n'y a rien à montrer.
    val filter by animateColorAsState(
        targetValue = level?.let { levelColors(it).container.copy(alpha = SignalColors.FILTER_ALPHA) } ?: Color.Transparent,
        label = "signal filter",
    )
    Surface(
        modifier = modifier,
        shape = Design.cardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(Modifier.fillMaxSize().background(filter)) {
            Column(Modifier.fillMaxSize().padding(Design.spaceSmall)) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = Design.touchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (recording is RecordingState.Recording) {
                        RecordingIndicator(recording.elapsedSeconds, Modifier.weight(1f, fill = false))
                    }
                    Spacer(Modifier.weight(1f))
                    TertiaryButton(
                        onClick = onShowHistory,
                        enabled = recording !is RecordingState.Recording && netScan !is NetScanState.Running,
                    ) { Text(stringResource(R.string.scan_history_open)) }
                    TertiaryButton(
                        onClick = onOpenReport,
                        enabled = recording !is RecordingState.Recording && netScan !is NetScanState.Running,
                    ) { Text(stringResource(R.string.report_open)) }
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (netScan == NetScanState.Idle) {
                        MapPlaceholder(recording = recording is RecordingState.Recording)
                    } else {
                        NetScanPanel(netScan, actions)
                    }
                }
                BottomControls(
                    showNetScanControls = netScan == NetScanState.Idle,
                    mode = mode,
                    recording = recording is RecordingState.Recording,
                    handedness = handedness,
                    actions = actions,
                )
            }
        }
    }
}

@Composable
private fun BottomControls(
    showNetScanControls: Boolean,
    mode: NetScanMode,
    recording: Boolean,
    handedness: Handedness,
    actions: MapActions,
) {
    val record: @Composable () -> Unit = {
        RecordButton(
            recording = recording,
            contentDescription = stringResource(if (recording) R.string.record_stop else R.string.record_start),
            onClick = actions.toggleRecording,
        )
    }
    // Un espace de la taille du bouton d'enregistrement garde le scan réseau centré sur la carte.
    val balance: @Composable () -> Unit = { Spacer(Modifier.width(Design.recordButtonSize)) }
    val recordAtStart = handedness.atStart(LocalLayoutDirection.current)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Design.spaceSmall),
    ) {
        if (recordAtStart) record() else balance()
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Design.spaceSmall),
        ) {
            if (showNetScanControls) {
                ModeSelector(mode, actions.selectMode)
                PrimaryButton(onClick = actions.startNetScan) {
                    Icon(painterResource(R.drawable.ic_network_scan), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Design.spaceSmall))
                    Text(stringResource(R.string.net_scan_start), textAlign = TextAlign.Center)
                }
            }
        }
        if (recordAtStart) balance() else record()
    }
}

private fun Handedness.atStart(direction: LayoutDirection): Boolean =
    (this == Handedness.LEFT) == (direction == LayoutDirection.Ltr)

@Composable
private fun ModeSelector(mode: NetScanMode, onSelect: (NetScanMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SecondaryButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_timer), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Design.spaceSmall))
            Text(modeLabel(mode), textAlign = TextAlign.Center)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NetScanMode.TIMER_CHOICES.forEach { seconds ->
                ModeItem(
                    label = stringResource(R.string.net_scan_mode_timer_item, seconds),
                    selected = mode == NetScanMode.Timer(seconds),
                ) {
                    onSelect(NetScanMode.Timer(seconds))
                    expanded = false
                }
            }
            ModeItem(
                label = stringResource(R.string.net_scan_mode_free_item, NetScanMode.MIN_DURATION_SECONDS),
                selected = mode == NetScanMode.Free,
            ) {
                onSelect(NetScanMode.Free)
                expanded = false
            }
        }
    }
}

@Composable
private fun ModeItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = { RadioButton(selected = selected, onClick = null) },
    )
}

@Composable
private fun NetScanPanel(state: NetScanState, actions: MapActions) {
    Surface(
        modifier = Modifier.widthIn(max = Design.overlayWidth).fillMaxWidth(),
        shape = Design.cardShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(Design.spaceMedium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Design.spaceSmall),
        ) {
            when (state) {
                is NetScanState.Running -> RunningContent(state, actions)
                is NetScanState.Completed -> CompletedContent(state, actions)
                is NetScanState.Unavailable -> UnavailableContent(state.problem, actions)
                NetScanState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun RunningContent(state: NetScanState.Running, actions: MapActions) {
    PanelTitle(stringResource(R.string.net_scan_running))
    LevelBadge(state.summary?.level)
    val remaining = state.remainingSeconds
    Text(
        text = if (remaining != null) {
            stringResource(R.string.net_scan_remaining, formatDuration(remaining))
        } else {
            stringResource(R.string.net_scan_elapsed, formatDuration(state.elapsedSeconds))
        },
        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
    )
    if (state.mode == NetScanMode.Free && !state.canFinish) {
        Text(
            text = stringResource(R.string.net_scan_min_duration, NetScanMode.MIN_DURATION_SECONDS),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TechnicalDetails(state.summary)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Design.spaceSmall, Alignment.CenterHorizontally),
    ) {
        TertiaryButton(onClick = actions.cancel) { Text(stringResource(R.string.cancel)) }
        if (state.mode == NetScanMode.Free) {
            PrimaryButton(onClick = actions.finish, enabled = state.canFinish) {
                Text(stringResource(R.string.net_scan_finish))
            }
        }
    }
}

@Composable
private fun CompletedContent(state: NetScanState.Completed, actions: MapActions) {
    val summary = state.result.summary
    PanelTitle(stringResource(R.string.net_scan_result))
    LevelBadge(summary.level)
    Text(
        text = pluralStringResource(
            R.plurals.net_scan_result_basis,
            summary.sampleCount,
            summary.sampleCount,
            formatDuration(state.result.durationSeconds),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    val (message, color) = when (state.saveState) {
        SaveState.SAVING -> stringResource(R.string.net_scan_saving) to MaterialTheme.colorScheme.onSurfaceVariant
        SaveState.SAVED -> stringResource(R.string.net_scan_saved) to MaterialTheme.colorScheme.onSurface
        SaveState.FAILED -> stringResource(R.string.net_scan_save_error) to MaterialTheme.colorScheme.error
    }
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    TechnicalDetails(summary)
    if (state.saveState == SaveState.FAILED) {
        PrimaryButton(onClick = actions.retrySave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.retry))
        }
        TertiaryButton(onClick = actions.dismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.net_scan_discard_result))
        }
    } else {
        PrimaryButton(
            onClick = actions.dismiss,
            enabled = state.saveState == SaveState.SAVED,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.close))
        }
    }
}

@Composable
private fun UnavailableContent(problem: NetScanProblem, actions: MapActions) {
    val context = LocalContext.current
    Text(
        text = stringResource(problemMessage(problem)),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    val settings = settingsIntent(context, problem)
    when {
        settings != null -> SecondaryButton(
            onClick = { openSettings(context, settings) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.open_settings)) }
        problem == NetScanProblem.NO_DATA || problem == NetScanProblem.SIGNAL_ERROR -> SecondaryButton(
            onClick = actions.startNetScan,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.retry)) }
    }
    PrimaryButton(onClick = actions.dismiss, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.close))
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
}

/** Le mot du niveau, en gros, sur la couleur du niveau. Pas de chiffres pour le commercial. */
@Composable
private fun LevelBadge(level: SignalLevel?) {
    if (level == null) {
        Row(
            modifier = Modifier.heightIn(min = 72.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Design.spaceSmall),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp).clearAndSetSemantics {})
            Text(stringResource(R.string.net_scan_measuring), style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    val colors = levelColors(level)
    val name = stringResource(levelName(level))
    val description = stringResource(R.string.level_description, name)
    val locale = LocalConfiguration.current.locales[0]
    Surface(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
        shape = Design.cardShape,
        color = colors.container,
        contentColor = colors.content,
    ) {
        BasicText(
            text = name.uppercase(locale),
            modifier = Modifier.padding(horizontal = Design.spaceMedium, vertical = Design.spaceMedium),
            style = MaterialTheme.typography.displayMedium.copy(
                color = colors.content,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 45.sp),
        )
    }
}

/** Valeurs brutes pour le technicien, repliées par défaut. */
@Composable
internal fun TechnicalDetails(summary: NetScanSummary?) {
    if (summary == null) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    TertiaryButton(onClick = { expanded = !expanded }) {
        Text(stringResource(if (expanded) R.string.details_hide else R.string.details_show))
    }
    if (!expanded) return
    val unavailable = stringResource(R.string.detail_unavailable)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Design.spaceExtraSmall)) {
        DetailRow(stringResource(R.string.detail_technology), technologyLabel(summary.technology) ?: unavailable)
        DetailRow(
            stringResource(R.string.detail_rsrp),
            summary.rsrpDbm?.let { stringResource(R.string.unit_dbm, formatDecimal(it)) } ?: unavailable,
        )
        DetailRow(
            stringResource(R.string.detail_sinr),
            summary.sinrDb?.let { stringResource(R.string.unit_db, formatDecimal(it)) } ?: unavailable,
        )
        DetailRow(stringResource(R.string.detail_band), summary.band?.toString() ?: unavailable)
        DetailRow(stringResource(R.string.detail_cell), summary.cellId?.toString() ?: unavailable)
        DetailRow(stringResource(R.string.detail_samples), summary.sampleCount.toString())
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

@Composable
private fun MapPlaceholder(recording: Boolean) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(Design.spaceMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Design.spaceSmall),
    ) {
        Icon(
            painterResource(R.drawable.ic_map),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.map_placeholder_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(if (recording) R.string.map_placeholder_recording else R.string.map_placeholder_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Badge(stringResource(R.string.prototype_badge))
    }
}

@Composable
private fun RecordingIndicator(elapsedSeconds: Int, modifier: Modifier = Modifier) {
    val duration = formatDuration(elapsedSeconds)
    val description = stringResource(R.string.recording_indicator_description, duration)
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        shape = Design.badgeShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(10.dp).background(RecordColors.start, CircleShape))
            Text(
                text = stringResource(R.string.recording_indicator, duration),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            )
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(
        shape = Design.badgeShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Design.spaceSmall, vertical = Design.spaceExtraSmall),
        )
    }
}

@Composable
private fun CenteredMessage(
    text: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    onClose: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Design.spaceMedium, Alignment.CenterVertically),
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.clearAndSetSemantics {})
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (onClose != null) PrimaryButton(onClick = onClose) { Text(stringResource(R.string.close)) }
    }
}

/** L'appareil photo et ARCore se tiennent à la verticale : l'écran reste en portrait sur téléphone. */
@Composable
private fun LockPortrait() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity.requestedOrientation = previous }
    }
}

/** Évite que l'écran s'éteigne pendant un scan réseau ou un enregistrement. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun operatorLabel(status: OperatorStatus): String = when (status) {
    OperatorStatus.Unknown -> stringResource(R.string.operator_loading)
    is OperatorStatus.Connected -> {
        val name = status.name ?: stringResource(R.string.operator_unknown_name)
        technologyLabel(status.technology)?.let { stringResource(R.string.operator_with_technology, name, it) } ?: name
    }
    OperatorStatus.NoService -> stringResource(R.string.operator_no_service)
    OperatorStatus.NoSim -> stringResource(R.string.operator_no_sim)
    OperatorStatus.RadioOff -> stringResource(R.string.operator_radio_off)
    OperatorStatus.Unavailable -> stringResource(R.string.operator_unavailable)
}

@Composable
internal fun technologyLabel(technology: NetworkTechnology): String? = when (technology) {
    NetworkTechnology.GSM -> stringResource(R.string.technology_2g)
    NetworkTechnology.UMTS -> stringResource(R.string.technology_3g)
    NetworkTechnology.LTE -> stringResource(R.string.technology_4g)
    NetworkTechnology.NR -> stringResource(R.string.technology_5g)
    NetworkTechnology.UNKNOWN -> null
}

@Composable
internal fun modeLabel(mode: NetScanMode): String = when (mode) {
    is NetScanMode.Timer -> stringResource(R.string.net_scan_mode_timer, mode.seconds)
    NetScanMode.Free -> stringResource(R.string.net_scan_mode_free)
}

internal fun levelColors(level: SignalLevel): LevelColors = when (level) {
    SignalLevel.NONE -> SignalColors.none
    SignalLevel.LOW -> SignalColors.low
    SignalLevel.MEDIUM -> SignalColors.medium
    SignalLevel.HIGH -> SignalColors.high
}

internal fun levelName(level: SignalLevel): Int = when (level) {
    SignalLevel.NONE -> R.string.level_none
    SignalLevel.LOW -> R.string.level_low
    SignalLevel.MEDIUM -> R.string.level_medium
    SignalLevel.HIGH -> R.string.level_high
}

private fun problemMessage(problem: NetScanProblem): Int = when (problem) {
    NetScanProblem.PERMISSION_DENIED -> R.string.problem_permission
    NetScanProblem.NO_TELEPHONY -> R.string.problem_no_telephony
    NetScanProblem.NO_SIM -> R.string.problem_no_sim
    NetScanProblem.AIRPLANE_MODE -> R.string.problem_airplane
    NetScanProblem.LOCATION_DISABLED -> R.string.problem_location
    NetScanProblem.NO_DATA -> R.string.problem_no_data
    NetScanProblem.SIGNAL_ERROR -> R.string.problem_signal_error
}

private fun settingsIntent(context: Context, problem: NetScanProblem): Intent? = when (problem) {
    NetScanProblem.PERMISSION_DENIED -> Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.fromParts("package", context.packageName, null),
    )
    NetScanProblem.LOCATION_DISABLED -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    NetScanProblem.AIRPLANE_MODE -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
    else -> null
}

private fun openSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        openAppSettings(context)
    }
}

private fun hasSignalPermissions(context: Context): Boolean = listOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.READ_PHONE_STATE,
).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

internal fun formatDuration(seconds: Int): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)

private fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.getDefault(), "%.1f", value)
