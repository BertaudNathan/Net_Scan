package com.example.MobileM2.Net_Scan.ui.theme

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PrimaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Button(onClick = onClick, modifier = modifier.defaultMinSize(minHeight = Design.touchTarget), content = content)
}

@Composable
fun SecondaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.defaultMinSize(minHeight = Design.touchTarget), content = content)
}

@Composable
fun TertiaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.defaultMinSize(minHeight = Design.touchTarget), content = content)
}

@Composable
fun DestructiveButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = Design.touchTarget),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        content = content,
    )
}
