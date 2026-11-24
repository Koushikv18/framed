package com.aesthetic.framed.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FramedColorScheme = darkColorScheme(
    primary = StudioText,
    onPrimary = StudioBlack,
    surface = StudioDark,
    onSurface = StudioText,
    surfaceVariant = StudioSurface,
    onSurfaceVariant = StudioTextSecondary,
    background = StudioBlack,
    onBackground = StudioText,
    outline = StudioBorder
)

@Composable
fun FramedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FramedColorScheme,
        content = content
    )
}
