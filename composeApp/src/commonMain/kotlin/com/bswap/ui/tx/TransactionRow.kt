package com.bswap.ui.tx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.bswap.ui.Preview
import androidx.compose.ui.unit.dp
import com.bswap.ui.WalletTheme
import com.bswap.ui.ModernCard
import com.bswap.shared.model.SolanaTx
import coil3.compose.AsyncImage

/**
 * Card displaying detailed information about a transaction.
 */
@Composable
fun TransactionRow(tx: SolanaTx, modifier: Modifier = Modifier) {
    ModernCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("TransactionRow"),
        elevation = 2.dp,
        onClick = { /* Handle transaction details click */ }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Token icon or arrow icon
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (tx.asset == SolanaTx.Asset.SPL && !tx.tokenLogo.isNullOrEmpty()) {
                    // Show token logo for SPL tokens with fallback
                    AsyncImage(
                        model = tx.tokenLogo,
                        contentDescription = "${tx.tokenSymbol ?: tx.tokenName} logo",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop,
                        fallback = null,
                        error = null,
                        onError = {
                            // If image fails to load, we'll show the fallback icon below
                        }
                    )
                } else {
                    // Show direction arrow with modern styling
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                if (tx.incoming) 
                                    MaterialTheme.colorScheme.secondaryContainer
                                else 
                                    MaterialTheme.colorScheme.tertiaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when {
                            tx.incoming -> Icons.Default.ArrowDownward
                            else -> Icons.Default.ArrowUpward
                        }
                        val iconColor = if (tx.incoming) 
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else 
                            MaterialTheme.colorScheme.onTertiaryContainer
                            
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                // Show token symbol overlay for SPL tokens
                if (tx.asset == SolanaTx.Asset.SPL && !tx.tokenSymbol.isNullOrEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        tonalElevation = 4.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = tx.tokenSymbol!!.take(1),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            // Transaction details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Transaction type and token info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (tx.incoming) "Received" else "Sent",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (tx.asset == SolanaTx.Asset.SPL && !tx.tokenSymbol.isNullOrEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                        ) {
                            Text(
                                text = tx.tokenSymbol!!,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                // Token name or address - make SPL token names more prominent
                Text(
                    text = when {
                        tx.asset == SolanaTx.Asset.SPL && !tx.tokenName.isNullOrEmpty() -> 
                            tx.tokenName!!
                        tx.asset == SolanaTx.Asset.SPL && !tx.tokenSymbol.isNullOrEmpty() -> 
                            "${tx.tokenSymbol} Token"
                        else -> "${if (tx.incoming) "From" else "To"}: ${tx.address.take(8)}...${tx.address.takeLast(4)}"
                    },
                    style = if (tx.asset == SolanaTx.Asset.SPL) {
                        MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = if (tx.asset == SolanaTx.Asset.SPL) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Transaction signature
                Text(
                    text = "TX: ${tx.signature.take(12)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            
            // Amount section
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val tokenSymbol = when {
                    tx.asset == SolanaTx.Asset.SPL && !tx.tokenSymbol.isNullOrEmpty() -> tx.tokenSymbol!!
                    tx.asset == SolanaTx.Asset.SPL -> "TOKEN"
                    else -> "SOL"
                }
                
                val amountColor = if (tx.incoming) 
                    MaterialTheme.colorScheme.secondary 
                else 
                    MaterialTheme.colorScheme.error
                
                Text(
                    text = "${if (tx.incoming) "+" else "-"}${String.format("%.4f", tx.amount)}",
                    color = amountColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = tokenSymbol,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                
                // Show USD estimate only for SOL transactions
                if (tx.asset == SolanaTx.Asset.SOL) {
                    Text(
                        text = "≈$${String.format("%.2f", tx.amount * 200)}", // Mock SOL price
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun TransactionRowPreview() {
    WalletTheme {
        Column {
            // SOL transaction
            TransactionRow(
                tx = SolanaTx("abcdef", "DestinationAddress", 1.23, incoming = true)
            )
            
            // SPL token transaction
            TransactionRow(
                tx = SolanaTx(
                    signature = "xyz789",
                    address = "SenderAddress", 
                    amount = 1000.0,
                    incoming = false,
                    asset = SolanaTx.Asset.SPL,
                    mint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                    tokenName = "USD Coin",
                    tokenSymbol = "USDC",
                    tokenLogo = "https://raw.githubusercontent.com/solana-labs/token-list/main/assets/mainnet/EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v/logo.png"
                )
            )
        }
    }
}
