package com.example.MobileM2.Net_Scan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.MobileM2.Net_Scan.domain.ThemeMode

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

internal val LightColors = lightColorScheme(
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

internal val DarkColors = darkColorScheme(
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
    val previewWidth = 96.dp
    val previewHeight = 128.dp
    val previewShape = RoundedCornerShape(12.dp)
    val recordButtonSize = 56.dp
    val overlayWidth = 360.dp
    val legendWidth = 280.dp
    val swatchShape = RoundedCornerShape(4.dp)

    /** Marges de la carte sous les bandeaux du haut et les commandes du bas. */
    val mapTopInset = 56.dp
    val mapBottomInset = 128.dp
}

/** Fond et texte d'un niveau de signal. Chaque paire garde un contraste d'au moins 4,5:1, quel que soit le thème. */
@Immutable
data class LevelColors(val container: Color, val content: Color)

/**
 * La charte impose le bleu nuit et le vert ; le rouge et le jaune s'ajoutent pour que les niveaux
 * se comprennent sans légende. Le vert de la charte marque le meilleur niveau.
 */
object SignalColors {
    val high = LevelColors(Green, Navy)
    val medium = LevelColors(Color(0xFFFFD54F), Navy)
    val low = LevelColors(Color(0xFFC62828), White)
    val none = LevelColors(Color(0xFF5F6368), White)

    /** Opacité du filtre posé sur la carte pendant un scan réseau. */
    const val FILTER_ALPHA = 0.35f
}

/** Bouton d'enregistrement façon caméra : pastille rouge pour démarrer, carré noir pour arrêter. */
object RecordColors {
    val container = White
    val ring = Color(0xFF5F6368)
    val start = Color(0xFFD32F2F)
    val stop = Color(0xFF111111)
}

@Composable
fun NetScanTheme(theme: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
