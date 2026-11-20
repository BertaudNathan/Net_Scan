package com.example.MobileM2.Net_Scan.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Largeur de la fenêtre, selon les seuils Material 3 : téléphone en portrait (compacte), petite
 * tablette ou téléphone en paysage (moyenne), tablette en paysage (étendue).
 */
enum class WidthClass {
    COMPACT, MEDIUM, EXPANDED;

    val isWide: Boolean get() = this != COMPACT

    companion object {
        const val MEDIUM_MIN_DP = 600
        const val EXPANDED_MIN_DP = 840

        fun of(widthDp: Int): WidthClass = when {
            widthDp >= EXPANDED_MIN_DP -> EXPANDED
            widthDp >= MEDIUM_MIN_DP -> MEDIUM
            else -> COMPACT
        }
    }
}

/** Largeur actuelle de la fenêtre ; suit la rotation et le multi-fenêtre. */
@Composable
fun currentWidthClass(): WidthClass = WidthClass.of(LocalConfiguration.current.screenWidthDp)

/** Tablette : le plus petit côté de l'écran fait au moins 600 dp, quelle que soit l'orientation. */
@Composable
fun isTablet(): Boolean = LocalConfiguration.current.smallestScreenWidthDp >= WidthClass.MEDIUM_MIN_DP
