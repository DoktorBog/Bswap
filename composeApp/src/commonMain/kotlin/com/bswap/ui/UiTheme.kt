package com.bswap.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp


private val DarkColorPalette = darkColorScheme(
    primary = Color.White,
    secondary = Color.White,
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1C),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightColorPalette = lightColorScheme(
    primary = Color.Black,
    secondary = Color.Black,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
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
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}

