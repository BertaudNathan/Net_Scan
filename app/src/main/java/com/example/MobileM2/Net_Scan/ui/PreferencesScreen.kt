package com.example.MobileM2.Net_Scan.ui

import androidx.activity.compose.BackHandler
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
import com.example.MobileM2.Net_Scan.domain.ThemeMode
import com.example.MobileM2.Net_Scan.ui.theme.Design

@Composable
fun PreferencesScreen(
    state: PreferencesState.Ready,
    onThemeSelected: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(enabled = state.saving) {}
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
    ) { insets ->
        Box(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = Design.contentWidth).fillMaxWidth()
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
                Column(verticalArrangement = Arrangement.spacedBy(Design.spaceSmall)) {
                    Text(
                        text = stringResource(R.string.theme_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.theme_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Column(modifier = Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { theme ->
                        val label = when (theme) {
                            ThemeMode.SYSTEM -> R.string.theme_system
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = Design.touchTarget)
                                .selectable(
                                    selected = state.theme == theme,
                                    enabled = !state.saving,
                                    role = Role.RadioButton,
                                    onClick = { onThemeSelected(theme) },
                                )
                                .padding(vertical = Design.spaceSmall),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Design.spaceMedium),
                        ) {
                            RadioButton(selected = state.theme == theme, onClick = null, enabled = !state.saving)
                            Text(text = stringResource(label), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (state.saving) {
                    Text(
                        text = stringResource(R.string.saving),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (state.saveError) {
                    Text(
                        text = stringResource(R.string.theme_save_error),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
            }
        }
    }
}
