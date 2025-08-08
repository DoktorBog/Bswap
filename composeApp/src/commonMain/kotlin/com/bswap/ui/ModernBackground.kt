package com.bswap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ModernBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0F0F23)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawWithContent {
                drawModernPattern(isDark)
                drawContent()
            }
    ) {
        content()
    }
}

private fun DrawScope.drawModernPattern(isDark: Boolean) {
    val width = size.width
    val height = size.height
    
    if (isDark) {
        // Dark theme: Subtle geometric patterns with glowing accents
        drawDarkPattern(width, height)
    } else {
        // Light theme: Clean gradient overlays
        drawLightPattern(width, height)
    }
}

private fun DrawScope.drawDarkPattern(width: Float, height: Float) {
    // Background gradient overlay
    val gradient = Brush.radialGradient(
        colors = listOf(
            Color(0xFF1A1B3A).copy(alpha = 0.8f),
            Color(0xFF0F0F23).copy(alpha = 0.95f),
            Color(0xFF0A0A1F).copy(alpha = 1f)
        ),
        center = Offset(width * 0.3f, height * 0.2f),
        radius = width * 0.8f
    )
    
    drawRect(
        brush = gradient,
        size = Size(width, height)
    )
    
    // Subtle geometric accents
    drawGeometricAccents(width, height, isDark = true)
}

private fun DrawScope.drawLightPattern(width: Float, height: Float) {
    // Light gradient overlay
    val gradient = Brush.radialGradient(
        colors = listOf(
            Color(0xFFEEF2FF).copy(alpha = 0.6f),
            Color(0xFFFAFAFA).copy(alpha = 0.3f),
            Color(0xFFFFFFFF).copy(alpha = 0f)
        ),
        center = Offset(width * 0.7f, height * 0.3f),
        radius = width * 0.6f
    )
    
    drawRect(
        brush = gradient,
        size = Size(width, height)
    )
    
    // Subtle geometric accents
    drawGeometricAccents(width, height, isDark = false)
}

private fun DrawScope.drawGeometricAccents(width: Float, height: Float, isDark: Boolean) {
    val accentColor = if (isDark) {
        Color(0xFF6366F1).copy(alpha = 0.1f)
    } else {
        Color(0xFF6366F1).copy(alpha = 0.05f)
    }
    
    // Draw subtle geometric shapes
    repeat(3) { index ->
        val angle = (index * 60f)
        val centerX = width * (0.2f + index * 0.3f)
        val centerY = height * (0.3f + index * 0.2f)
        val size = (width * 0.1f) + (index * 20f)
        
        rotate(angle, pivot = Offset(centerX, centerY)) {
            translate(centerX - size/2, centerY - size/2) {
                // Draw hexagonal shapes
                drawHexagon(
                    center = Offset(size/2, size/2),
                    radius = size/3,
                    color = accentColor
                )
            }
        }
    }
}

private fun DrawScope.drawHexagon(center: Offset, radius: Float, color: Color) {
    val points = mutableListOf<Offset>()
    repeat(6) { i ->
        val angle = (i * 60f) * (Math.PI / 180f)
        val x = center.x + radius * cos(angle).toFloat()
        val y = center.y + radius * sin(angle).toFloat()
        points.add(Offset(x, y))
    }
    
    // Draw the hexagon outline
    for (i in points.indices) {
        val start = points[i]
        val end = points[(i + 1) % points.size]
        drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = MaterialTheme.shapes.medium
            )
            .drawWithContent {
                // Glass morphism effect
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.1f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                )
                drawContent()
            }
    ) {
        content()
    }
}