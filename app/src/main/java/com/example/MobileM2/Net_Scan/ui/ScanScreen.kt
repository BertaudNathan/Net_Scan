package com.example.MobileM2.Net_Scan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.MobileM2.Net_Scan.R
import com.example.MobileM2.Net_Scan.ui.theme.Design
import com.example.MobileM2.Net_Scan.ui.theme.DestructiveButton
import com.example.MobileM2.Net_Scan.ui.theme.PrimaryButton
import com.example.MobileM2.Net_Scan.ui.theme.TertiaryButton

@Composable
fun ScanScreen(onExit: () -> Unit, viewModel: ScanListViewModel = viewModel()) {
    var showCreation by rememberSaveable { mutableStateOf(false) }
    var showExit by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = !showCreation && !showExit) { showExit = true }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
    ) { insets ->
        Box(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentAlignment = Alignment.TopCenter,
        ) {
            ScanList(
                state = viewModel.state,
                onCreate = { showCreation = true },
                modifier = Modifier.widthIn(max = Design.contentWidth).fillMaxSize(),
            )
        }
    }

    if (showCreation) {
        CreateScanDialog(onClose = { showCreation = false })
    }
    if (showExit) {
        AlertDialog(
            onDismissRequest = { showExit = false },
            title = { Text(stringResource(R.string.exit_title)) },
            text = { Text(stringResource(R.string.exit_message)) },
            confirmButton = {
                DestructiveButton(onClick = onExit) { Text(stringResource(R.string.exit)) }
            },
            dismissButton = {
                TertiaryButton(onClick = { showExit = false }) { Text(stringResource(R.string.stay)) }
            },
        )
    }
}

@Composable
private fun ScanList(state: ScanListState, onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(
            PaddingValues(horizontal = Design.spaceLarge, vertical = Design.spaceLarge),
        ),
        verticalArrangement = Arrangement.spacedBy(Design.spaceLarge),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BuildingMark()
            Spacer(Modifier.width(Design.spaceSmall))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.brand_kicker),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Design.spaceSmall)) {
            Text(
                text = stringResource(R.string.scans_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.scans_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PrimaryButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.new_scan), style = MaterialTheme.typography.labelLarge)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Design.spaceMedium)) {
            Text(
                text = stringResource(R.string.list_heading),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            when (state) {
                ScanListState.Loading -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.loading_scans))
                }
                ScanListState.Empty -> {
                    Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.empty_description))
                }
                ScanListState.Error -> Text(
                    stringResource(R.string.error_scans),
                    color = MaterialTheme.colorScheme.error,
                )
                is ScanListState.Success -> {
                    Column(verticalArrangement = Arrangement.spacedBy(Design.spaceExtraSmall)) {
                        Text(
                            text = stringResource(R.string.demo_data),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.demo_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.scans.forEach { ScanCard(it) }
                }
            }
        }
        Spacer(Modifier.height(Design.spaceSmall))
    }
}

@Composable
private fun ScanCard(scan: ScanExample) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Design.cardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Design.spaceExtraSmall,
    ) {
        Column(
            modifier = Modifier.padding(Design.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(Design.spaceMedium),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Design.spaceMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BuildingMark()
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(scan.name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(scan.description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                shape = Design.badgeShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = stringResource(scan.status),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(
                        horizontal = Design.spaceSmall,
                        vertical = Design.spaceExtraSmall,
                    ),
                )
            }
            Text(
                text = stringResource(scan.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BuildingMark() {
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier = Modifier.size(Design.touchTarget)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(Design.iconSize)) {
            val stroke = size.width / 12f
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
                color = ink,
                start = Offset(size.width * x1, size.height * y1),
                end = Offset(size.width * x2, size.height * y2),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            line(.25f, .85f, .25f, .2f)
            line(.25f, .2f, .75f, .2f)
            line(.75f, .2f, .75f, .85f)
            line(.15f, .85f, .85f, .85f)
            line(.4f, .4f, .4f, .4f)
            line(.6f, .4f, .6f, .4f)
            line(.4f, .6f, .4f, .6f)
            line(.6f, .6f, .6f, .6f)
        }
    }
}

@Composable
private fun CreateScanDialog(onClose: () -> Unit) {
    var building by rememberSaveable { mutableStateOf("") }
    var client by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var showNotice by rememberSaveable { mutableStateOf(false) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    fun requestClose() {
        if (building.isNotBlank() || client.isNotBlank() || address.isNotBlank()) {
            showDiscard = true
        } else {
            onClose()
        }
    }

    AlertDialog(
        onDismissRequest = ::requestClose,
        title = { Text(stringResource(R.string.dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Design.spaceMedium),
            ) {
                Text(stringResource(R.string.dialog_description))
                OutlinedTextField(
                    value = building,
                    onValueChange = { building = it },
                    label = { Text(stringResource(R.string.building_name)) },
                    placeholder = { Text(stringResource(R.string.building_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = client,
                    onValueChange = { client = it },
                    label = { Text(stringResource(R.string.client_name)) },
                    placeholder = { Text(stringResource(R.string.client_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.address)) },
                    placeholder = { Text(stringResource(R.string.address_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    singleLine = true,
                )
                Text(
                    text = stringResource(if (showNotice) R.string.prototype_notice else R.string.form_illustration_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        },
        confirmButton = {
            PrimaryButton(onClick = { showNotice = true }) { Text(stringResource(R.string.create_scan)) }
        },
        dismissButton = {
            TertiaryButton(onClick = ::requestClose) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_message)) },
            confirmButton = {
                DestructiveButton(onClick = onClose) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                TertiaryButton(onClick = { showDiscard = false }) { Text(stringResource(R.string.keep_editing)) }
            },
        )
    }
}
