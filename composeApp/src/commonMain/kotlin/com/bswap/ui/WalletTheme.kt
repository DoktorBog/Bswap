package com.bswap.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme

private val LightColorPalette = lightColorScheme()
private val DarkColorPalette = darkColorScheme()

@Composable
fun WalletTheme(
    dynamic: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = rememberColorScheme(dynamic, darkTheme)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}

@Composable
internal expect fun rememberColorScheme(dynamic: Boolean, darkTheme: Boolean): ColorScheme

