package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LilacDarkColorScheme = darkColorScheme(
    primary = LilacPrimary,
    onPrimary = LilacOnPrimary,
    primaryContainer = LilacPrimaryContainer,
    onPrimaryContainer = LilacOnPrimaryContainer,
    secondary = LilacSecondary,
    onSecondary = LilacOnSecondary,
    secondaryContainer = LilacSecondaryContainer,
    onSecondaryContainer = LilacOnSecondaryContainer,
    tertiary = LilacTertiary,
    onTertiary = LilacOnTertiary,
    tertiaryContainer = LilacTertiaryContainer,
    onTertiaryContainer = LilacOnTertiaryContainer,
    background = GraphiteBackground,
    onBackground = LilacOnSurface,
    surface = GraphiteSurface,
    onSurface = LilacOnSurface,
    surfaceVariant = GraphiteSurfaceContainerHigh,
    onSurfaceVariant = LilacOnSurfaceVariant,
    surfaceContainerLowest = GraphiteSurfaceContainerLowest,
    surfaceContainerLow = GraphiteSurfaceContainerLow,
    surfaceContainer = GraphiteSurfaceContainer,
    surfaceContainerHigh = GraphiteSurfaceContainerHigh,
    surfaceContainerHighest = GraphiteSurfaceContainerHighest,
    outline = LilacOutline,
    outlineVariant = LilacOutlineVariant,
    error = LilacError,
    onError = LilacOnError,
    errorContainer = LilacErrorContainer,
    onErrorContainer = LilacOnErrorContainer
)

@Composable
fun LilacPixelTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LilacDarkColorScheme,
        typography = LilacPixelTypography,
        shapes = LilacPixelShapes,
        content = content
    )
}
