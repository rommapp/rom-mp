package com.rommapp.rommp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MidnightTurntable = darkColorScheme(
    primary = Brass,
    onPrimary = VinylBlack,
    primaryContainer = FeltRaised,
    onPrimaryContainer = Cream,
    secondary = MutedText,
    onSecondary = VinylBlack,
    secondaryContainer = FeltRaised,
    onSecondaryContainer = Cream,
    tertiary = Brass,
    onTertiary = VinylBlack,
    background = VinylBlack,
    onBackground = Cream,
    surface = Felt,
    onSurface = Cream,
    surfaceVariant = FeltRaised,
    onSurfaceVariant = MutedText,
    outline = MutedText,
    outlineVariant = FeltRaised,
)

@Composable
fun RomMMusicTheme(content: @Composable () -> Unit) {
    // Dark, warm, single-accent record-player theme.
    MaterialTheme(
        colorScheme = MidnightTurntable,
        typography = RomMTypography,
        content = content,
    )
}
