package com.bswap.ui

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberColorScheme(dynamic: Boolean, darkTheme: Boolean): ColorScheme {
    val context = LocalContext.current
    return if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // Fallback to our custom color schemes
        if (darkTheme) {
            darkColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF818CF8),
                onPrimary = androidx.compose.ui.graphics.Color(0xFF1E1B4B),
                primaryContainer = androidx.compose.ui.graphics.Color(0xFF3730A3),
                onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFEEF2FF),
                secondary = androidx.compose.ui.graphics.Color(0xFF34D399),
                background = androidx.compose.ui.graphics.Color(0xFF0F0F23),
                onBackground = androidx.compose.ui.graphics.Color(0xFFF9FAFB),
                surface = androidx.compose.ui.graphics.Color(0xFF1A1B3A),
                onSurface = androidx.compose.ui.graphics.Color(0xFFF9FAFB),
                surfaceVariant = androidx.compose.ui.graphics.Color(0xFF252547)
            )
        } else {
            lightColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF6366F1),
                onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
                primaryContainer = androidx.compose.ui.graphics.Color(0xFFEEF2FF),
                onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF1E1B4B),
                secondary = androidx.compose.ui.graphics.Color(0xFF10B981),
                background = androidx.compose.ui.graphics.Color(0xFFFAFAFA),
                onBackground = androidx.compose.ui.graphics.Color(0xFF111827),
                surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
                onSurface = androidx.compose.ui.graphics.Color(0xFF111827),
                surfaceVariant = androidx.compose.ui.graphics.Color(0xFFF3F4F6)
            )
        }
    }
}
