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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Professional color palette for Bswap trading app
private val BswapLightColors = lightColorScheme(
    primary = Color(0xFF6366F1), // Modern indigo
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF10B981), // Success green
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFFF59E0B), // Warning amber
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF92400E),
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFD1D5DB),
    outlineVariant = Color(0xFFE5E7EB)
)

private val BswapDarkColors = darkColorScheme(
    primary = Color(0xFF818CF8), // Brighter indigo for dark theme
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFEEF2FF),
    secondary = Color(0xFF34D399), // Brighter green
    onSecondary = Color(0xFF064E3B),
    secondaryContainer = Color(0xFF059669),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFFFBBF24), // Brighter amber
    onTertiary = Color(0xFF92400E),
    tertiaryContainer = Color(0xFFD97706),
    onTertiaryContainer = Color(0xFFFEF3C7),
    error = Color(0xFFF87171),
    onError = Color(0xFF991B1B),
    errorContainer = Color(0xFFDC2626),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF0F0F23), // Deep dark blue
    onBackground = Color(0xFFF9FAFB),
    surface = Color(0xFF1A1B3A), // Dark surface with slight blue tint
    onSurface = Color(0xFFF9FAFB),
    surfaceVariant = Color(0xFF252547), // Elevated surface
    onSurfaceVariant = Color(0xFFD1D5DB),
    outline = Color(0xFF4B5563),
    outlineVariant = Color(0xFF374151)
)

// Enhanced typography with better hierarchy
private val BswapTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.15).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.15).sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// Modern shapes with consistent border radius
private val BswapShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun WalletTheme(
    dynamic: Boolean = false, // Default to custom theme
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (dynamic) {
        rememberColorScheme(dynamic, darkTheme)
    } else {
        if (darkTheme) BswapDarkColors else BswapLightColors
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = BswapTypography,
        shapes = BswapShapes,
        content = content
    )
}

@Composable
internal expect fun rememberColorScheme(dynamic: Boolean, darkTheme: Boolean): ColorScheme