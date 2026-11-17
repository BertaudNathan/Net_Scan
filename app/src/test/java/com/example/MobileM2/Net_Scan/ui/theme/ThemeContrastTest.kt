package com.example.MobileM2.Net_Scan.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test fun textColorsMeetMinimumContrastInBothThemes() {
        listOf(LightColors, DarkColors).forEach { scheme ->
            assertContrast(scheme.onPrimary, scheme.primary)
            assertContrast(scheme.onSecondaryContainer, scheme.secondaryContainer)
            assertContrast(scheme.onBackground, scheme.background)
            assertContrast(scheme.onSurface, scheme.surface)
            assertContrast(scheme.onSurfaceVariant, scheme.background)
            assertContrast(scheme.onSurfaceVariant, scheme.surface)
            assertContrast(scheme.primary, scheme.surface)
            assertContrast(scheme.error, scheme.surface)
            assertContrast(scheme.error, scheme.background)
            assertContrast(scheme.onError, scheme.error)
        }
    }

    @Test fun inputOutlinesRemainVisible() {
        listOf(LightColors, DarkColors).forEach { scheme: ColorScheme ->
            assertContrast(scheme.outline, scheme.surface, minimum = 3f)
        }
    }

    @Test fun signalLevelLabelsStayReadable() {
        listOf(SignalColors.high, SignalColors.medium, SignalColors.low, SignalColors.none).forEach {
            assertContrast(it.content, it.container)
        }
    }

    @Test fun recordButtonSymbolsStandOut() {
        assertContrast(RecordColors.start, RecordColors.container, minimum = 3f)
        assertContrast(RecordColors.stop, RecordColors.container, minimum = 3f)
        assertContrast(RecordColors.ring, RecordColors.container, minimum = 3f)
    }

    private fun assertContrast(foreground: Color, background: Color, minimum: Float = 4.5f) {
        val first = foreground.luminance()
        val second = background.luminance()
        val ratio = (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
        assertTrue("Contrast $ratio is below $minimum for $foreground on $background", ratio >= minimum)
    }
}
