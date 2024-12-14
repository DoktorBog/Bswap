package com.bswap.app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bswap.app.models.TokenProfile

@Composable
fun AssetItem(
    asset: TokenProfile,
    openLink: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable {
                openLink(asset.url)
            },
        elevation = 4.dp
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            AsyncImage(
                model = asset.icon,
                contentDescription = "",
                modifier = Modifier.size(50.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = asset.tokenAddress, style = MaterialTheme.typography.h6)
                Text(text = "Chain ID: ${asset.chainId}", style = MaterialTheme.typography.body2)
                Text(
                    text = asset.description ?: "No Description",
                    style = MaterialTheme.typography.body2
                )
            }
        }
    }
}