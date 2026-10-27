package com.countmymacros.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Every role is set: Material's defaults are its purple baseline, which leaked into
// containers and surfaces (seen on a real device in dark mode).
private val LightColors = lightColorScheme(
    primary = Color(0xFF5B7A1A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4EBC8),
    onPrimaryContainer = Color(0xFF3D5010),
    secondary = Color(0xFF5D6148),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E5C8),
    onSecondaryContainer = Color(0xFF1A1D0A),
    tertiary = Color(0xFF8A4F25),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC5),
    onTertiaryContainer = Color(0xFF321200),
    background = Color(0xFFFBFCF4),
    onBackground = Color(0xFF1B1C17),
    surface = Color(0xFFFBFCF4),
    onSurface = Color(0xFF1B1C17),
    surfaceVariant = Color(0xFFE3E4D5),
    onSurfaceVariant = Color(0xFF46483C),
    surfaceTint = Color(0xFF5B7A1A),
    outline = Color(0xFF76786B),
    outlineVariant = Color(0xFFC6C8B9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F6EC),
    surfaceContainer = Color(0xFFEFF0E6),
    surfaceContainerHigh = Color(0xFFE9EBE0),
    surfaceContainerHighest = Color(0xFFE3E5DA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC2D88B),
    onPrimary = Color(0xFF2B3500),
    primaryContainer = Color(0xFF3D5010),
    onPrimaryContainer = Color(0xFFE4EBC8),
    secondary = Color(0xFFC5C9AC),
    onSecondary = Color(0xFF2E331C),
    secondaryContainer = Color(0xFF454A32),
    onSecondaryContainer = Color(0xFFE2E5C8),
    tertiary = Color(0xFFF2B990),
    onTertiary = Color(0xFF4A2405),
    tertiaryContainer = Color(0xFF6B3912),
    onTertiaryContainer = Color(0xFFFFDCC5),
    background = Color(0xFF13140F),
    onBackground = Color(0xFFE4E3DA),
    surface = Color(0xFF13140F),
    onSurface = Color(0xFFE4E3DA),
    surfaceVariant = Color(0xFF46483C),
    onSurfaceVariant = Color(0xFFC6C8B9),
    surfaceTint = Color(0xFFC2D88B),
    outline = Color(0xFF909284),
    outlineVariant = Color(0xFF46483C),
    surfaceContainerLowest = Color(0xFF0E0F0A),
    surfaceContainerLow = Color(0xFF1B1C17),
    surfaceContainer = Color(0xFF1F201B),
    surfaceContainerHigh = Color(0xFF2A2B25),
    surfaceContainerHighest = Color(0xFF35352F),
)

/** One color per macro, used everywhere (bars, labels, charts). */
object MacroColors {
    val Protein = Color(0xFFE5534B)
    val Carbs = Color(0xFFE8A33D)
    val Fat = Color(0xFF4A90D9)
}

/** Text-weight versions of [MacroColors] that pass 4.5:1 contrast, for labels on background. */
object MacroTextColors {
    val ProteinLight = Color(0xFFB3302A)
    val CarbsLight = Color(0xFF8A5700)
    val FatLight = Color(0xFF2A64A8)
    val ProteinDark = Color(0xFFFF8A80)
    val CarbsDark = Color(0xFFF2C46B)
    val FatDark = Color(0xFF8AB8F0)
}

/** Protein/carbs/fat text colors that meet contrast in the current theme. */
@Composable
fun macroTextColors(): Triple<Color, Color, Color> = if (isSystemInDarkTheme()) {
    Triple(MacroTextColors.ProteinDark, MacroTextColors.CarbsDark, MacroTextColors.FatDark)
} else {
    Triple(MacroTextColors.ProteinLight, MacroTextColors.CarbsLight, MacroTextColors.FatLight)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
