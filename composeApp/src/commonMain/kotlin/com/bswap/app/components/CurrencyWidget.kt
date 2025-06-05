package com.bswap.app.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bswap.app.models.PriceDetails
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun CurrencyWidget(
    rate: PriceDetails?,
    lastFetched: Instant?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "SOL Exchange Rates", style = MaterialTheme.typography.h6)
            if (rate != null) {
                Text(text = "USD: ${rate.usd}")
                Text(text = "EUR: ${rate.eur}")
            } else {
                Text(text = "Loading...")
            }
            lastFetched?.let {
                val localDateTime = it.toLocalDateTime(TimeZone.currentSystemDefault())
                Text(
                    text = "Last updated: $localDateTime",
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }
}
