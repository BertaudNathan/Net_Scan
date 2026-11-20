package com.example.MobileM2.Net_Scan.ui.visits

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MobileM2.Net_Scan.R
import com.example.MobileM2.Net_Scan.domain.MappingMode
import com.example.MobileM2.Net_Scan.domain.Visit
import com.example.MobileM2.Net_Scan.ui.theme.Design
import com.example.MobileM2.Net_Scan.ui.theme.DestructiveButton
import com.example.MobileM2.Net_Scan.ui.theme.PrimaryButton
import com.example.MobileM2.Net_Scan.ui.theme.TertiaryButton
import com.example.MobileM2.Net_Scan.ui.theme.WidthClass
import com.example.MobileM2.Net_Scan.ui.theme.currentWidthClass
import java.text.DateFormat
import java.util.Date

@Composable
fun VisitListScreen(
    onExit: () -> Unit,
    onPreferences: () -> Unit,
    onOpenVisit: (Long) -> Unit,
    viewModel: VisitListViewModel,
) {
    var showCreation by rememberSaveable { mutableStateOf(false) }
    var showExit by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val widthClass = currentWidthClass()
    val creationState by viewModel.creationState.collectAsStateWithLifecycle()

    BackHandler(enabled = !showCreation && !showExit) { showExit = true }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                        )
                        .padding(horizontal = Design.spaceLarge, vertical = Design.spaceMedium),
                    contentAlignment = Alignment.Center,
                ) {
                    PrimaryButton(
                        onClick = { showCreation = true },
                        // Sur tablette, un bouton large comme l'écran se lit mal : il garde une largeur de carte.
                        modifier = Modifier.widthIn(max = if (widthClass.isWide) Design.overlayWidth else Dp.Unspecified).fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.new_scan), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
    ) { insets ->
        Box(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentAlignment = Alignment.TopCenter,
        ) {
            VisitList(
                state = state,
                onRetry = viewModel::retry,
                onPreferences = onPreferences,
                onOpenVisit = onOpenVisit,
                columns = when (widthClass) {
                    WidthClass.COMPACT -> 1
                    WidthClass.MEDIUM -> 2
                    WidthClass.EXPANDED -> 3
                },
                modifier = Modifier.widthIn(max = if (widthClass.isWide) Design.wideContentWidth else Design.contentWidth).fillMaxSize(),
            )
        }
    }

    if (showCreation) {
        CreateVisitDialog(
            state = creationState,
            onCreate = viewModel::createVisit,
            onClose = {
                showCreation = false
                viewModel.resetCreation()
            },
        )
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
private fun VisitList(
    state: VisitListState,
    onRetry: () -> Unit,
    onPreferences: () -> Unit,
    onOpenVisit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 1,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(
            PaddingValues(horizontal = Design.spaceLarge, vertical = Design.spaceLarge),
        ),
        verticalArrangement = Arrangement.spacedBy(Design.spaceLarge),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreferences, modifier = Modifier.size(Design.touchTarget)) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.open_preferences),
                )
            }
            Spacer(Modifier.width(Design.spaceSmall))
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

        // Le texte d'introduction garde une longueur de ligne lisible, même sur tablette.
        Column(
            modifier = Modifier.widthIn(max = Design.contentWidth),
            verticalArrangement = Arrangement.spacedBy(Design.spaceSmall),
        ) {
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
            Text(
                text = stringResource(R.string.feature_scope),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Design.spaceMedium)) {
            Text(
                text = stringResource(R.string.list_heading),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            when (state) {
                VisitListState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.clearAndSetSemantics {})
                    Text(
                        stringResource(R.string.loading_scans),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                VisitListState.Empty -> {
                    Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.empty_description))
                }
                VisitListState.Error -> {
                    Text(
                        stringResource(R.string.error_scans),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    TertiaryButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                is VisitListState.Success -> {
                    // Sur grand écran, une grille : les cartes d'une même ligne prennent la même hauteur.
                    state.visits.chunked(columns).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(Design.spaceMedium),
                        ) {
                            row.forEach { visit ->
                                VisitCard(visit, onClick = { onOpenVisit(visit.id) }, modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Design.spaceSmall))
    }
}

@Composable
private fun VisitCard(visit: Visit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth()
            .clip(Design.cardShape)
            .clickable(onClickLabel = stringResource(R.string.open_visit), role = Role.Button, onClick = onClick),
        shape = Design.cardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                        text = visit.building,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (visit.client.isNotEmpty()) {
                        Text(
                            text = visit.client,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (visit.address.isNotEmpty()) {
                Text(
                    text = visit.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(
                    R.string.created_date,
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(visit.createdAt)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(if (visit.mode == MappingMode.MANUAL) R.string.visit_mode_manual else R.string.visit_mode_arcore),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Choix du mode de la visite, définitif : les mesures d'un mode ne se placent pas sur la carte de l'autre. */
@Composable
private fun MappingModeChoice(selected: MappingMode, enabled: Boolean, onSelect: (MappingMode) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Design.spaceExtraSmall),
    ) {
        Text(
            text = stringResource(R.string.mapping_mode_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        ModeOption(
            title = stringResource(R.string.mapping_mode_arcore),
            description = stringResource(R.string.mapping_mode_arcore_description),
            selected = selected == MappingMode.ARCORE,
            enabled = enabled,
            onClick = { onSelect(MappingMode.ARCORE) },
        )
        ModeOption(
            title = stringResource(R.string.mapping_mode_manual),
            description = stringResource(R.string.mapping_mode_manual_description),
            selected = selected == MappingMode.MANUAL,
            enabled = enabled,
            onClick = { onSelect(MappingMode.MANUAL) },
        )
    }
}

@Composable
private fun ModeOption(title: String, description: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = Design.touchTarget)
            .clip(Design.cardShape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Design.spaceExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Design.spaceSmall),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BuildingMark() {
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier = Modifier.size(Design.touchTarget)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .clearAndSetSemantics {},
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
private fun CreateVisitDialog(
    state: CreationState,
    onCreate: (String, String, String, MappingMode) -> Unit,
    onClose: () -> Unit,
) {
    var building by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(MappingMode.ARCORE) }
    val wide = currentWidthClass().isWide
    var client by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val invalidBuilding = state == CreationState.InvalidBuilding && building.isBlank()
    val focusRequester = remember { FocusRequester() }
    val requiredMessage = stringResource(R.string.building_required)
    val dialogTitle = stringResource(R.string.dialog_title)

    fun requestClose() {
        if (state == CreationState.Saving) return
        if (building.isNotBlank() || client.isNotBlank() || address.isNotBlank()) {
            showDiscard = true
        } else {
            onClose()
        }
    }

    LaunchedEffect(state) {
        if (state == CreationState.Saved) onClose()
        if (state == CreationState.InvalidBuilding) focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
            // Feuille du bas sur téléphone ; fenêtre centrée de la largeur d'une page sur tablette.
            contentAlignment = if (wide) Alignment.Center else Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier.matchParentSize().pointerInput(building, client, address, state) {
                    detectTapGestures { requestClose() }
                },
            )
            Surface(
                modifier = Modifier.widthIn(max = if (wide) Design.contentWidth else Dp.Unspecified)
                    .fillMaxWidth()
                    // Le fond reste visible en haut, même clavier ouvert : on peut toujours toucher à côté pour fermer.
                    .heightIn(max = maxHeight * SHEET_MAX_HEIGHT)
                    .semantics { paneTitle = dialogTitle }
                    .pointerInput(Unit) {
                        // Keep taps inside the sheet from reaching the dismissible backdrop.
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent()
                        }
                    },
                shape = Design.cardShape,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column {
                    Column(
                        modifier = Modifier.weight(1f, fill = false)
                            .align(Alignment.CenterHorizontally)
                            .widthIn(max = Design.contentWidth)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = Design.spaceLarge,
                                end = Design.spaceLarge,
                                top = Design.spaceMedium,
                            ),
                        verticalArrangement = Arrangement.spacedBy(Design.spaceMedium),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Design.spaceSmall),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = dialogTitle,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.weight(1f).semantics { heading() },
                            )
                            TertiaryButton(onClick = ::requestClose, enabled = state != CreationState.Saving) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                        Text(stringResource(R.string.dialog_description))
                        OutlinedTextField(
                            value = building,
                            onValueChange = { building = it },
                            label = { Text(stringResource(R.string.building_name)) },
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                                .semantics { if (invalidBuilding) error(requiredMessage) },
                            enabled = state != CreationState.Saving,
                            singleLine = true,
                            isError = invalidBuilding,
                            supportingText = if (invalidBuilding) {
                                {
                                    Text(
                                        requiredMessage,
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                                    )
                                }
                            } else null,
                        )
                        OutlinedTextField(
                            value = client,
                            onValueChange = { client = it },
                            label = { Text(stringResource(R.string.client_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state != CreationState.Saving,
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text(stringResource(R.string.address)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state != CreationState.Saving,
                            singleLine = true,
                        )
                        MappingModeChoice(mode, enabled = state != CreationState.Saving, onSelect = { mode = it })
                        if (state == CreationState.Error) {
                            Text(
                                text = stringResource(R.string.save_error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                            )
                        }
                        if (state == CreationState.Saving) {
                            Text(
                                text = stringResource(R.string.saving),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }
                    PrimaryButton(
                        onClick = { onCreate(building, client, address, mode) },
                        modifier = Modifier.fillMaxWidth().padding(Design.spaceLarge),
                        enabled = state != CreationState.Saving,
                    ) {
                        Text(stringResource(R.string.create_scan))
                    }
                }
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_message)) },
            confirmButton = {
                DestructiveButton(onClick = onClose) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                TertiaryButton(onClick = { showDiscard = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            },
        )
    }
}

private const val SHEET_MAX_HEIGHT = 0.85f
