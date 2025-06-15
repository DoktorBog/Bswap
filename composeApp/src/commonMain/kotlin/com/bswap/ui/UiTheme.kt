package com.bswap.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val DarkColorPalette = darkColorScheme(
    primary = UiColors.primaryForeground,
    secondary = UiColors.accentGray,
    background = UiColors.background,
    surface = UiColors.surface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = UiColors.primaryForeground,
    onSurface = UiColors.primaryForeground,
)

private val LightColorPalette = lightColorScheme(
    primary = UiColors.primaryForeground,
    secondary = UiColors.accentGray,
    background = UiColors.background,
    surface = UiColors.surface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = UiColors.primaryForeground,
    onSurface = UiColors.primaryForeground,
)

@Composable
fun UiTheme(
    background: Color = MaterialTheme.colorScheme.background,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val useDarkColors = if (background != MaterialTheme.colorScheme.background) {
        background.luminance() < 0.5f
    } else {
        darkTheme
    }

    val colors = if (useDarkColors) {
        DarkColorPalette.copy(background = background, surface = background)
    } else {
        LightColorPalette.copy(background = background, surface = background)
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        shapes = Shapes(),
        content = content
    )
}

