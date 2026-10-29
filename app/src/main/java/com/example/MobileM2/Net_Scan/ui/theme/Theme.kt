package com.example.MobileM2.Net_Scan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Navy = Color(0xFF0F056B)
private val Green = Color(0xFFA7D7A9)
private val White = Color(0xFFFFFFFF)
private val LightBackground = Color(0xFFF7F8F6)
private val LightMuted = Color(0xFF535D58)
private val LightOutline = Color(0xFF737D78)
private val DarkBackground = Color(0xFF111510)
private val DarkSurface = Color(0xFF1C221D)
private val DarkText = Color(0xFFE7EDE7)
private val DarkMuted = Color(0xFFC1CEC2)
private val DarkOutline = Color(0xFF9BA99C)
private val ErrorLight = Color(0xFFB3261E)
private val ErrorDark = Color(0xFFFFB4AB)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = White,
    primaryContainer = Green,
    onPrimaryContainer = Navy,
    secondary = Navy,
    onSecondary = White,
    secondaryContainer = Green,
    onSecondaryContainer = Navy,
    background = LightBackground,
    onBackground = Navy,
    surface = White,
    onSurface = Navy,
    surfaceVariant = LightBackground,
    surfaceContainerHigh = White,
    onSurfaceVariant = LightMuted,
    outline = LightOutline,
    error = ErrorLight,
    onError = White,
)

private val DarkColors = darkColorScheme(
    primary = Green,
    onPrimary = Navy,
    primaryContainer = Navy,
    onPrimaryContainer = White,
    secondary = Green,
    onSecondary = Navy,
    secondaryContainer = Navy,
    onSecondaryContainer = White,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurface,
    surfaceContainerHigh = DarkSurface,
    onSurfaceVariant = DarkMuted,
    outline = DarkOutline,
    error = ErrorDark,
    onError = DarkBackground,
)

object Design {
    val spaceExtraSmall = 4.dp
    val spaceSmall = 8.dp
    val spaceMedium = 16.dp
    val spaceLarge = 24.dp
    val spaceExtraLarge = 32.dp
    val iconSize = 24.dp
    val touchTarget = 48.dp
    val contentWidth = 640.dp
    val cardShape = RoundedCornerShape(20.dp)
    val badgeShape = RoundedCornerShape(50)
}

@Composable
fun NetScanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
