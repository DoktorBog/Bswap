package com.bswap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
@Composable
fun TrianglesBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawTriangles()
    }
}

private fun DrawScope.drawTriangles() {
    val sizeStep = size.minDimension / 4f
    val light = Color.LightGray.copy(alpha = 0.2f)
    for (row in 0..3) {
        for (col in 0..3) {
            if ((row + col) % 2 == 0) {
                val x = col * sizeStep
                val y = row * sizeStep
                val path = Path().apply {
                    moveTo(x, y)
                    lineTo(x + sizeStep, y)
                    lineTo(x + sizeStep / 2f, y + sizeStep)
                    close()
                }
                drawPath(path, light)
            }
        }
    }
}
