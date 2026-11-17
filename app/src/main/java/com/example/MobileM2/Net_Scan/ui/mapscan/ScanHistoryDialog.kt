package com.example.MobileM2.Net_Scan.ui.mapscan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.MobileM2.Net_Scan.R
import com.example.MobileM2.Net_Scan.domain.NetScan
import com.example.MobileM2.Net_Scan.ui.theme.Design
import com.example.MobileM2.Net_Scan.ui.theme.TertiaryButton
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ScanHistoryDialog(state: ScanHistoryState, onDismiss: () -> Unit, onRetry: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_history_title)) },
        text = {
            when (state) {
                ScanHistoryState.Loading -> Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.scan_history_loading))
                }
                ScanHistoryState.Empty -> Text(
                    stringResource(R.string.scan_history_empty),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                ScanHistoryState.Error -> Column {
                    Text(
                        stringResource(R.string.scan_history_error),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    TertiaryButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                is ScanHistoryState.Success -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(Design.spaceMedium),
                ) {
                    items(state.scans, key = { it.id }) { scan -> HistoryEntry(scan) }
                }
            }
        },
        confirmButton = {
            TertiaryButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun HistoryEntry(scan: NetScan) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }
    val colors = levelColors(scan.summary.level)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Design.cardShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(Design.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(Design.spaceSmall),
        ) {
            Text(
                stringResource(R.string.scan_history_date, dateFormat.format(Date(scan.startedAt))),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Surface(shape = Design.badgeShape, color = colors.container, contentColor = colors.content) {
                Text(
                    text = stringResource(R.string.level_description, stringResource(levelName(scan.summary.level))),
                    modifier = Modifier.padding(horizontal = Design.spaceSmall, vertical = Design.spaceExtraSmall),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(stringResource(R.string.scan_history_operator, scan.operatorName ?: stringResource(R.string.operator_unknown_name)))
            Text(stringResource(R.string.scan_history_mode, modeLabel(scan.mode)))
            Text(
                pluralStringResource(
                    R.plurals.net_scan_result_basis,
                    scan.summary.sampleCount,
                    scan.summary.sampleCount,
                    formatDuration(scan.durationSeconds),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            TechnicalDetails(scan.summary)
        }
    }
}
