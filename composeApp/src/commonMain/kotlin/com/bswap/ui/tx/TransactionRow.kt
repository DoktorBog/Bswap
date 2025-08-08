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
import androidx.compose.ui.graphics.Brush
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
 * Card displaying detailed information about a transaction in Solflare-style layout.
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
            // Avatar-style icon
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (tx.asset == SolanaTx.Asset.SPL) {
                    if (!tx.tokenLogo.isNullOrEmpty()) {
                        // Try to show token logo
                        AsyncImage(
                            model = tx.tokenLogo,
                            contentDescription = "${tx.tokenSymbol ?: tx.tokenName} logo",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentScale = ContentScale.Crop,
                            fallback = null,
                            error = null
                        )
                    } else {
                        // No logo - show token symbol
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tx.tokenSymbol?.take(2)?.uppercase() ?: "TK",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // SOL transactions - show SOL avatar
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF9945FF),
                                        Color(0xFF14F195)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "◎",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            // Transaction details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Main transaction action - Solflare style
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val actionText = when {
                        tx.asset == SolanaTx.Asset.SPL && tx.incoming -> "Buy"
                        tx.asset == SolanaTx.Asset.SPL && !tx.incoming -> "Sell"
                        tx.incoming -> "Received"
                        else -> "Sent"
                    }
                    
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Show token symbol for SPL transactions
                    if (tx.asset == SolanaTx.Asset.SPL && !tx.tokenSymbol.isNullOrEmpty()) {
                        Text(
                            text = tx.tokenSymbol!!,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // Token name for SPL or address for SOL - Solflare style
                Text(
                    text = when {
                        tx.asset == SolanaTx.Asset.SPL && !tx.tokenName.isNullOrEmpty() -> 
                            tx.tokenName!!
                        tx.asset == SolanaTx.Asset.SPL && !tx.tokenSymbol.isNullOrEmpty() -> 
                            "${tx.tokenSymbol} Token"
                        tx.asset == SolanaTx.Asset.SPL -> "Unknown Token"
                        else -> "${if (tx.incoming) "From" else "To"}: ${tx.address.take(8)}...${tx.address.takeLast(4)}"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Transaction time or signature (simplified for now)
                Text(
                    text = "Recently", // In real app, this would show relative time
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            // Amount section - Solflare style
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Primary amount with token symbol
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val amountColor = if (tx.incoming) 
                        Color(0xFF10B981) // Green for incoming
                    else 
                        MaterialTheme.colorScheme.onSurface
                    
                    Text(
                        text = if (tx.incoming) "+" else "-",
                        color = amountColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = String.format("%.4f", tx.amount),
                        color = amountColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    val tokenSymbol = when {
                        tx.asset == SolanaTx.Asset.SPL && !tx.tokenSymbol.isNullOrEmpty() -> tx.tokenSymbol!!
                        tx.asset == SolanaTx.Asset.SPL -> "TOKEN"
                        else -> "SOL"
                    }
                    
                    Text(
                        text = tokenSymbol,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // USD estimate only for SOL transactions
                if (tx.asset == SolanaTx.Asset.SOL) {
                    Text(
                        text = "${if (tx.incoming) "+" else "-"}$${String.format("%.2f", tx.amount * 200)}", // Mock SOL price
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
