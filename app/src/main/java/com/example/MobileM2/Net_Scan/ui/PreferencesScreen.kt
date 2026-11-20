package com.example.MobileM2.Net_Scan.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.example.MobileM2.Net_Scan.R
import com.example.MobileM2.Net_Scan.domain.Handedness
import com.example.MobileM2.Net_Scan.domain.ThemeMode
import com.example.MobileM2.Net_Scan.ui.theme.Design
import com.example.MobileM2.Net_Scan.ui.theme.currentWidthClass

/**
 * Préférences : thème et main dominante. Sur téléphone, les deux sections se suivent ; sur tablette,
 * la page prend toute la largeur et les sections sont côte à côte.
 */
@Composable
fun PreferencesScreen(
    state: PreferencesState.Ready,
    onThemeSelected: (ThemeMode) -> Unit,
    onHandednessSelected: (Handedness) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler {
        if (!state.saving) onBack()
    }
    val wide = currentWidthClass().isWide
    val theme: @Composable (Modifier) -> Unit = { modifier ->
        ChoiceSection(
            title = R.string.theme_title,
            description = R.string.theme_description,
            options = ThemeMode.entries.map { it to themeLabel(it) },
            selected = state.theme,
            enabled = !state.saving,
            onSelect = onThemeSelected,
            modifier = modifier,
        )
    }
    val handedness: @Composable (Modifier) -> Unit = { modifier ->
        ChoiceSection(
            title = R.string.handedness_title,
            description = R.string.handedness_description,
            options = Handedness.entries.map { it to handednessLabel(it) },
            selected = state.handedness,
            enabled = !state.saving,
            onSelect = onHandednessSelected,
            modifier = modifier,
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
    ) { insets ->
        Box(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = if (wide) Design.wideContentWidth else Design.contentWidth).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Design.spaceLarge),
                verticalArrangement = Arrangement.spacedBy(Design.spaceLarge),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, enabled = !state.saving, modifier = Modifier.size(Design.touchTarget)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_to_scans),
                        )
                    }
                    Text(
                        text = stringResource(R.string.preferences_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = Design.spaceSmall).semantics { heading() },
                    )
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Design.spaceExtraLarge)) {
                        theme(Modifier.weight(1f))
                        handedness(Modifier.weight(1f))
                    }
                } else {
                    theme(Modifier.fillMaxWidth())
                    handedness(Modifier.fillMaxWidth())
                }
                if (state.saving) {
                    Text(
                        text = stringResource(R.string.saving),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (state.saveError) {
                    Text(
                        text = stringResource(R.string.preferences_save_error),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
            }
        }
    }
}

/** Titre, explication, puis un choix unique parmi [options] (valeur, libellé). */
@Composable
private fun <T> ChoiceSection(
    @StringRes title: Int,
    @StringRes description: Int,
    options: List<Pair<T, Int>>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Design.spaceSmall)) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(modifier = Modifier.selectableGroup()) {
            options.forEach { (value, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().sizeIn(minHeight = Design.touchTarget)
                        .selectable(
                            selected = selected == value,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(value) },
                        )
                        .padding(vertical = Design.spaceSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Design.spaceMedium),
                ) {
                    RadioButton(selected = selected == value, onClick = null, enabled = enabled)
                    Text(text = stringResource(label), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private fun themeLabel(theme: ThemeMode): Int = when (theme) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun handednessLabel(handedness: Handedness): Int = when (handedness) {
    Handedness.RIGHT -> R.string.handedness_right
    Handedness.LEFT -> R.string.handedness_left
}
