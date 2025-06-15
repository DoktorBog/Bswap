package com.bswap.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    elevation: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = UiColors.surface),
        shape = CardDefaults.shape,
        elevation = CardDefaults.cardElevation(elevation),
        border = BorderStroke(1.dp, Color.White)
    ) { content() }
}
