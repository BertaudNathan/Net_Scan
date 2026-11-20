package com.example.MobileM2.Net_Scan.ui.mapscan

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.MobileM2.Net_Scan.R
import com.example.MobileM2.Net_Scan.domain.Coverage
import com.example.MobileM2.Net_Scan.domain.CoverageRules
import com.example.MobileM2.Net_Scan.domain.NetScan
import com.example.MobileM2.Net_Scan.domain.NetScanMode
import com.example.MobileM2.Net_Scan.domain.PlanPoint
import com.example.MobileM2.Net_Scan.ui.theme.Design
import com.example.MobileM2.Net_Scan.ui.theme.DestructiveButton
import com.example.MobileM2.Net_Scan.ui.theme.PrimaryButton
import com.example.MobileM2.Net_Scan.ui.theme.SecondaryButton
import com.example.MobileM2.Net_Scan.ui.theme.SignalColors
import com.example.MobileM2.Net_Scan.ui.theme.TertiaryButton
import kotlin.math.roundToInt

/** Actions propres au mode manuel. */
internal class PlanActions(
    val importPlan: () -> Unit,
    /** Toucher du plan : choisit la mesure touchée, sinon place la personne. */
    val tap: (PlanPoint, Float) -> Unit,
    val setScale: (PlanPoint, PlanPoint, Float) -> Boolean,
    val toggleCoverage: () -> Unit,
    val measureAtSelected: () -> Unit,
    val clearSelection: () -> Unit,
    /** Demande la suppression d'un scan, à confirmer. */
    val deleteScan: (NetScan) -> Unit,
)

/** Tracé des murs sur le plan. */
internal class WallActions(
    val edit: () -> Unit,
    val finish: () -> Unit,
    val selectTool: (WallTool) -> Unit,
    val tap: (PlanPoint, Float) -> Unit,
    val newWall: () -> Unit,
    val undo: () -> Unit,
    val deleteSelected: () -> Unit,
)

/**
 * Carte du mode manuel : importer un plan, en régler l'échelle, y tracer les murs si on le souhaite,
 * puis toucher sa position avant chaque scan réseau. La heatmap (cases, distances) est la même qu'en
 * mode ARCore ; elle contourne les murs tracés.
 */
@Composable
internal fun ManualMapArea(
    plan: PlanState,
    importStatus: ImportStatus,
    position: PlanPoint?,
    mode: NetScanMode,
    netScan: NetScanState,
    wallEditor: WallEditor?,
    coverage: CoverageState,
    selectedScan: NetScan?,
    deletionFailed: Boolean,
    actions: MapActions,
    planActions: PlanActions,
    wallActions: WallActions,
    onShowHistory: () -> Unit,
    onOpenReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var adjustingScale by rememberSaveable { mutableStateOf(false) }
    val calibration = remember { mutableStateListOf<PlanPoint>() }
    val calibrating = plan is PlanState.NeedsScale || (plan is PlanState.Ready && adjustingScale)
    val walls = (plan as? PlanState.Ready)?.walls.orEmpty()
    val editor = wallEditor.takeIf { plan is PlanState.Ready && !calibrating }
    // Les zones à mesurer restent visibles pendant le tracé des murs : on voit le contour se fermer.
    val analyzed = ((coverage as? CoverageState.Shown)?.coverage as? Coverage.Analyzed)?.takeIf { plan is PlanState.Ready && !calibrating }
    // Hauteur des boutons du haut, qui passent à la ligne sur un écran étroit ou en grands caractères.
    var commandsHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val level = when (netScan) {
        is NetScanState.Running -> netScan.summary?.level
        is NetScanState.Completed -> netScan.result.summary.level
        else -> null
    }
    val filter by animateColorAsState(
        targetValue = level?.let { levelColors(it).container.copy(alpha = SignalColors.FILTER_ALPHA) } ?: Color.Transparent,
        label = "signal filter",
    )
    val shownPlan = (plan as? PlanState.Ready)?.plan ?: (plan as? PlanState.NeedsScale)?.plan

    Surface(
        modifier = modifier,
        shape = Design.cardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (shownPlan != null) {
                PlanMapView(
                    plan = shownPlan,
                    map = (plan as? PlanState.Ready)?.map,
                    position = if (calibrating || editor != null) null else position,
                    calibration = calibration,
                    walls = walls,
                    wallChain = editor?.chainStart,
                    selectedWall = editor?.selected,
                    coverage = analyzed,
                    selectedScan = selectedScan?.id?.takeIf { !calibrating && editor == null },
                    contentPadding = PaddingValues(
                        start = Design.spaceSmall,
                        end = Design.spaceSmall,
                        // Boutons, puis la ligne de la consigne : le plan commence dessous.
                        top = Design.spaceSmall * 2 + with(density) { commandsHeight.toDp() } + Design.touchTarget,
                        bottom = Design.mapBottomInset,
                    ),
                    onTap = { point, tolerance ->
                        when {
                            // Un mur peut s'arrêter sur le bord du plan : le toucher juste à côté compte.
                            editor != null -> wallActions.tap(point, tolerance)
                            !shownPlan.contains(point) -> Unit
                            calibrating -> if (calibration.size < 2) calibration += point
                            netScan == NetScanState.Idle -> planActions.tap(point, tolerance)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(Modifier.fillMaxSize().background(filter))
            Column(Modifier.fillMaxSize().padding(Design.spaceSmall)) {
                // Commandes de la visite en haut (elles passent à la ligne sur un écran étroit), consigne dessous.
                FlowRow(
                    modifier = Modifier.fillMaxWidth().onSizeChanged { commandsHeight = it.height },
                    horizontalArrangement = Arrangement.spacedBy(Design.spaceExtraSmall, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(Design.spaceExtraSmall),
                ) {
                    TertiaryButton(onClick = onShowHistory, enabled = netScan !is NetScanState.Running) {
                        Text(stringResource(R.string.scan_history_open))
                    }
                    TertiaryButton(onClick = onOpenReport, enabled = netScan !is NetScanState.Running) {
                        Text(stringResource(R.string.report_open))
                    }
                    if (plan is PlanState.Ready) {
                        // Toujours là une fois le plan prêt, quitte à être inactifs : le plan ne bouge pas d'un mode à l'autre.
                        val idle = !calibrating && netScan == NetScanState.Idle && editor == null
                        TertiaryButton(onClick = wallActions.edit, enabled = idle) { Text(stringResource(R.string.plan_walls_edit)) }
                        TertiaryButton(
                            onClick = {
                                calibration.clear()
                                adjustingScale = true
                            },
                            enabled = idle,
                        ) { Text(stringResource(R.string.plan_scale_adjust)) }
                        FilterChip(
                            selected = coverage != CoverageState.Hidden,
                            onClick = planActions.toggleCoverage,
                            label = { Text(stringResource(R.string.coverage_toggle)) },
                            enabled = !calibrating,
                            // Toujours la même icône : la largeur du bouton ne change pas quand on l'active.
                            leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().heightIn(min = Design.touchTarget), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        planInstruction(plan, calibrating, calibration.size, position, netScan, editor, coverage, selectedScan != null)?.let {
                            StatusChip(it.text, warning = it.warning)
                        }
                    }
                    if (plan is PlanState.Ready && !calibrating) MapLegendButton(forPlan = true)
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        netScan != NetScanState.Idle -> NetScanPanel(netScan, actions)
                        importStatus == ImportStatus.IMPORTING -> Loading(stringResource(R.string.plan_importing))
                        plan is PlanState.Loading -> Loading(null)
                        plan is PlanState.Error -> MessageCard(title = null, body = stringResource(R.string.plan_error))
                        plan is PlanState.Missing -> ImportCard(failed = importStatus == ImportStatus.FAILED, onImport = planActions.importPlan)
                    }
                }
                when {
                    calibrating -> CalibrationControls(
                        canCancel = plan is PlanState.Ready,
                        hasPoints = calibration.isNotEmpty(),
                        onRestart = { calibration.clear() },
                        onCancel = {
                            calibration.clear()
                            adjustingScale = false
                        },
                    )
                    editor != null -> WallControls(editor, canUndo = editor.chainStart != null || walls.isNotEmpty(), wallActions)
                    plan is PlanState.Ready && netScan == NetScanState.Idle && selectedScan != null ->
                        SelectedScanCard(selectedScan, deletionFailed, planActions)
                    plan is PlanState.Ready && netScan == NetScanState.Idle -> PlanScanControls(mode, position != null, actions)
                }
            }
        }
    }

    if (calibrating && calibration.size == 2) {
        ScaleDialog(
            onConfirm = { meters ->
                planActions.setScale(calibration[0], calibration[1], meters).also { saved ->
                    if (saved) {
                        calibration.clear()
                        adjustingScale = false
                    }
                }
            },
            onDismiss = { calibration.clear() },
        )
    }
}

private class Instruction(val text: String, val warning: Boolean = false)

@Composable
private fun planInstruction(
    plan: PlanState,
    calibrating: Boolean,
    points: Int,
    position: PlanPoint?,
    netScan: NetScanState,
    editor: WallEditor?,
    coverage: CoverageState,
    scanSelected: Boolean,
): Instruction? = when {
    netScan != NetScanState.Idle -> null
    calibrating -> Instruction(stringResource(if (points == 0) R.string.plan_scale_first else R.string.plan_scale_second))
    plan !is PlanState.Ready -> null
    editor != null -> when {
        editor.saveFailed -> Instruction(stringResource(R.string.plan_walls_save_failed), warning = true)
        editor.tool == WallTool.ERASE ->
            Instruction(stringResource(if (editor.selected == null) R.string.plan_walls_pick else R.string.plan_walls_picked))
        editor.chainStart == null -> Instruction(stringResource(R.string.plan_walls_first))
        else -> Instruction(stringResource(R.string.plan_walls_next))
    }
    scanSelected -> Instruction(stringResource(R.string.plan_scan_selected))
    position != null -> Instruction(stringResource(R.string.plan_position_set))
    coverage is CoverageState.Shown -> coverageInstruction(coverage.coverage)
    coverage == CoverageState.Computing -> Instruction(stringResource(R.string.coverage_computing))
    else -> Instruction(stringResource(R.string.plan_place_yourself))
}

@Composable
private fun coverageInstruction(coverage: Coverage): Instruction = when {
    coverage == Coverage.OutlineOpen ->
        Instruction(stringResource(R.string.coverage_outline_open, CoverageRules.MAX_DOOR_WIDTH.roundToInt()), warning = true)
    coverage !is Coverage.Analyzed -> Instruction(stringResource(R.string.plan_place_yourself))
    coverage.suggestions.isNotEmpty() -> {
        val count = coverage.suggestions.size
        Instruction(stringResource(R.string.coverage_summary, coverage.percent, pluralStringResource(R.plurals.coverage_suggestions, count, count)))
    }
    coverage.uncovered.isEmpty() -> Instruction(stringResource(R.string.coverage_complete))
    else -> Instruction(stringResource(R.string.coverage_corners, coverage.percent))
}

@Composable
private fun Loading(text: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Design.spaceSmall)) {
        CircularProgressIndicator(modifier = Modifier.clearAndSetSemantics {})
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

@Composable
private fun ImportCard(failed: Boolean, onImport: () -> Unit) {
    Surface(
        modifier = Modifier.widthIn(max = Design.overlayWidth),
        shape = Design.cardShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(Design.spaceMedium),
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
                text = stringResource(R.string.plan_missing_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.plan_missing_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (failed) {
                Text(
                    text = stringResource(R.string.plan_import_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            PrimaryButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.plan_import))
            }
        }
    }
}

@Composable
private fun CalibrationControls(canCancel: Boolean, hasPoints: Boolean, onRestart: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Design.spaceSmall, Alignment.CenterHorizontally),
    ) {
        if (hasPoints) TertiaryButton(onClick = onRestart) { Text(stringResource(R.string.plan_scale_restart)) }
        if (canCancel) TertiaryButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}

/**
 * Tracer (chaque toucher termine un mur et commence le suivant) ou effacer (toucher un mur, puis
 * confirmer). Chaque changement est enregistré aussitôt : « Terminer » ne fait que quitter le tracé.
 */
@Composable
private fun WallControls(editor: WallEditor, canUndo: Boolean, actions: WallActions) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Design.spaceSmall),
    ) {
        val tools = listOf(WallTool.DRAW to R.string.plan_walls_draw, WallTool.ERASE to R.string.plan_walls_erase)
        SingleChoiceSegmentedButtonRow {
            tools.forEachIndexed { index, (tool, label) ->
                SegmentedButton(
                    selected = editor.tool == tool,
                    onClick = { actions.selectTool(tool) },
                    shape = SegmentedButtonDefaults.itemShape(index, tools.size),
                ) { Text(stringResource(label)) }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Design.spaceExtraSmall, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Design.spaceExtraSmall),
        ) {
            when (editor.tool) {
                WallTool.DRAW -> {
                    TertiaryButton(onClick = actions.undo, enabled = canUndo) { Text(stringResource(R.string.plan_walls_undo)) }
                    TertiaryButton(onClick = actions.newWall, enabled = editor.chainStart != null) {
                        Text(stringResource(R.string.plan_walls_new))
                    }
                }
                WallTool.ERASE -> if (editor.selected != null) {
                    DestructiveButton(onClick = actions.deleteSelected) { Text(stringResource(R.string.plan_walls_delete)) }
                }
            }
            SecondaryButton(onClick = actions.finish) { Text(stringResource(R.string.plan_walls_done)) }
        }
    }
}

/** Sans ARCore, pas d'enregistrement : le scan réseau reste centré, seul en bas de la carte. */
@Composable
private fun PlanScanControls(mode: NetScanMode, positioned: Boolean, actions: MapActions) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Design.spaceSmall),
    ) {
        ModeSelector(mode, actions.selectMode)
        PrimaryButton(onClick = actions.startNetScan, enabled = positioned) {
            Icon(painterResource(R.drawable.ic_network_scan), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Design.spaceSmall))
            Text(stringResource(R.string.net_scan_start), textAlign = TextAlign.Center)
        }
    }
}

/**
 * Mesure touchée sur le plan : son résultat, puis la supprimer (après confirmation), mesurer à
 * nouveau au même endroit, ou revenir au plan.
 */
@Composable
private fun SelectedScanCard(scan: NetScan, deletionFailed: Boolean, actions: PlanActions) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = Design.cardShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(Design.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(Design.spaceSmall),
        ) {
            ScanSummary(scan)
            if (deletionFailed) {
                Text(
                    stringResource(R.string.scan_delete_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Design.spaceExtraSmall, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(Design.spaceExtraSmall),
            ) {
                DestructiveButton(onClick = { actions.deleteScan(scan) }) { Text(stringResource(R.string.scan_delete)) }
                TertiaryButton(onClick = actions.measureAtSelected) { Text(stringResource(R.string.scan_measure_here)) }
                SecondaryButton(onClick = actions.clearSelection) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

/** Longueur réelle entre les deux points touchés. Retourne false si elle est refusée. */
@Composable
private fun ScaleDialog(onConfirm: (Float) -> Boolean, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val invalidMessage = stringResource(R.string.plan_scale_invalid)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_scale_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Design.spaceSmall)) {
                Text(stringResource(R.string.plan_scale_body))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        invalid = false
                    },
                    label = { Text(stringResource(R.string.plan_scale_field)) },
                    singleLine = true,
                    isError = invalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = if (invalid) {
                        { Text(invalidMessage, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth().semantics { if (invalid) error(invalidMessage) },
                )
            }
        },
        confirmButton = {
            PrimaryButton(onClick = {
                val meters = text.trim().replace(',', '.').toFloatOrNull()
                invalid = meters == null || !onConfirm(meters)
            }) { Text(stringResource(R.string.plan_scale_validate)) }
        },
        dismissButton = {
            TertiaryButton(onClick = onDismiss) { Text(stringResource(R.string.plan_scale_restart)) }
        },
    )
}
