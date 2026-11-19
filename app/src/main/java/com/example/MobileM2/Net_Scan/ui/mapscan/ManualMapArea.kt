package com.example.MobileM2.Net_Scan.ui.mapscan

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
import com.example.MobileM2.Net_Scan.domain.NetScanMode
import com.example.MobileM2.Net_Scan.domain.PlanPoint
import com.example.MobileM2.Net_Scan.ui.theme.Design
import com.example.MobileM2.Net_Scan.ui.theme.PrimaryButton
import com.example.MobileM2.Net_Scan.ui.theme.SignalColors
import com.example.MobileM2.Net_Scan.ui.theme.TertiaryButton

/** Actions propres au mode manuel. */
internal class PlanActions(
    val importPlan: () -> Unit,
    val placeHere: (PlanPoint) -> Unit,
    val setScale: (PlanPoint, PlanPoint, Float) -> Boolean,
)

/**
 * Carte du mode manuel : importer un plan, en régler l'échelle, puis toucher sa position avant
 * chaque scan réseau. La heatmap (cases, distances) est la même qu'en mode ARCore.
 */
@Composable
internal fun ManualMapArea(
    plan: PlanState,
    importStatus: ImportStatus,
    position: PlanPoint?,
    mode: NetScanMode,
    netScan: NetScanState,
    actions: MapActions,
    planActions: PlanActions,
    modifier: Modifier = Modifier,
) {
    var adjustingScale by rememberSaveable { mutableStateOf(false) }
    val calibration = remember { mutableStateListOf<PlanPoint>() }
    val calibrating = plan is PlanState.NeedsScale || (plan is PlanState.Ready && adjustingScale)
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
                    position = if (calibrating) null else position,
                    calibration = calibration,
                    contentPadding = PaddingValues(
                        start = Design.spaceSmall,
                        end = Design.spaceSmall,
                        top = Design.mapTopInset,
                        bottom = Design.mapBottomInset,
                    ),
                    onTap = { point ->
                        when {
                            !shownPlan.contains(point) -> Unit
                            calibrating -> if (calibration.size < 2) calibration += point
                            netScan == NetScanState.Idle -> planActions.placeHere(point)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(Modifier.fillMaxSize().background(filter))
            Column(Modifier.fillMaxSize().padding(Design.spaceSmall)) {
                Row(modifier = Modifier.fillMaxWidth().heightIn(min = Design.touchTarget), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Design.spaceExtraSmall)) {
                        planInstruction(plan, calibrating, calibration.size, position, netScan)?.let {
                            StatusChip(stringResource(it), warning = false)
                        }
                    }
                    if (plan is PlanState.Ready && !calibrating && netScan == NetScanState.Idle) {
                        TertiaryButton(onClick = {
                            calibration.clear()
                            adjustingScale = true
                        }) { Text(stringResource(R.string.plan_scale_adjust)) }
                        MapLegendButton(forPlan = true)
                    }
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

private fun planInstruction(plan: PlanState, calibrating: Boolean, points: Int, position: PlanPoint?, netScan: NetScanState): Int? = when {
    netScan != NetScanState.Idle -> null
    calibrating -> if (points == 0) R.string.plan_scale_first else R.string.plan_scale_second
    plan !is PlanState.Ready -> null
    position == null -> R.string.plan_place_yourself
    else -> R.string.plan_position_set
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
