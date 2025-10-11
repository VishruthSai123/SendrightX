/*
 * Copyright (C) 2025 SendRight 4.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.app.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vishruth.key1.R
import com.vishruth.key1.ime.ai.AiUsageTracker
import com.vishruth.key1.user.UserManager

// Subscription colors to match SubscriptionScreen
private val SubscriptionGreen = Color(0xFF46BB23)
private val SubscriptionGreenLight = Color(0xFF66CC43)
private val SubscriptionGreenDark = Color(0xFF2A7A15)

@Composable
fun AiUsageCard(
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Get AI usage tracker and user manager
    val aiUsageTracker = remember { AiUsageTracker.getInstance() }
    val userManager = remember { UserManager.getInstance() }
    
    // Observe AI usage stats with cache-free updates
    val aiUsageStats by aiUsageTracker.usageStats.collectAsState()
    
    // Observe user data for subscription status
    val userData by userManager.userData.collectAsState()
    
    // Get subscription manager for real-time pro status
    val subscriptionManager = userManager.getSubscriptionManager()
    val isProFromSubscription by subscriptionManager?.isPro?.collectAsState() ?: remember { mutableStateOf(false) }
    
    // Determine if user is pro from multiple sources
    val isProUser = userData?.subscriptionStatus == "pro" || isProFromSubscription
    
    // Check if user is in rewarded window (24h unlimited access after ads)
    val isInRewardWindow = aiUsageStats.isRewardWindowActive
    
    // Calculate usage statistics
    val dailyLimit = 5 // Free daily limit
    val usedCount = aiUsageStats.dailyActionCount
    val remainingCount = maxOf(0, dailyLimit - usedCount)
    val progress = if (dailyLimit > 0) usedCount.toFloat() / dailyLimit.toFloat() else 0f
    val isLimitReached = usedCount >= dailyLimit && !isProUser && !isInRewardWindow
    
    // Force refresh usage stats when card is displayed to ensure cache-free data
    LaunchedEffect(Unit) {
        aiUsageTracker.forceCompleteRefresh()
    }
    
    // Also refresh when card becomes visible
    LaunchedEffect(aiUsageStats) {
        // This will trigger whenever the usage stats change
        // ensuring real-time updates without cache
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isProUser) {
                Color(0xFFFFFFFF) // White background for pro users
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "AI Usage",
                        tint = if (isProUser) Color(0xFF4CAF50) else Color(0xFF46BB23),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = when {
                            isProUser -> "Premium Plan"
                            isInRewardWindow -> "Reward Active"
                            else -> "Free Plan"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isProUser -> Color(0xFF2E7D32) // Green for premium
                            isInRewardWindow -> Color(0xFF2E7D32) // Green for reward (matching premium)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                
                if (!isProUser) {
                    Text(
                        text = if (isInRewardWindow) "Unlimited" else "$usedCount/$dailyLimit",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            isInRewardWindow -> Color(0xFF2E7D32) // Green for unlimited (matching reward active)
                            isLimitReached -> Color(0xFFD32F2F)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            if (isProUser) {
                // Pro user content
                Text(
                    text = "✨ You are a Premium User",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Thanks for subscribing! Enjoy unlimited AI features.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF388E3C)
                )
            } else if (isInRewardWindow) {
                // Rewarded window content
                Text(
                    text = "🎉 Enjoying 24h unlimited access!",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2E7D32) // Green to match reward active text
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                // Show remaining time
                val remainingMs = aiUsageStats.rewardWindowTimeRemaining()
                val remainingHours = remainingMs / (1000 * 60 * 60)
                val remainingMinutes = (remainingMs % (1000 * 60 * 60)) / (1000 * 60)
                
                Text(
                    text = "Time remaining: ${remainingHours}h ${remainingMinutes}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF388E3C) // Lighter green for remaining time
                )
            } else {
                // Free user content
                if (isLimitReached) {
                    Text(
                        text = "You've used your today's free credits",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Text(
                        text = "$remainingCount credits left",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Progress bar (only show for regular free users, not during reward window)
                if (!isInRewardWindow) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE0E0E0))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .background(
                                    color = when {
                                        isLimitReached -> Color(0xFFD32F2F)
                                        progress > 0.8f -> Color(0xFFFF9800)
                                        else -> Color(0xFF4CAF50)
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Animated Upgrade button (only show for regular free users)
                    AnimatedUpgradeButton(
                        onClick = onUpgradeClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )
                }
                
                if (isLimitReached) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Daily credits reset at midnight",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedUpgradeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Create infinite animation for glow effect
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    
    // Animate the glow intensity
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    Button(
        onClick = onClick,
        modifier = modifier
            .drawBehind {
                // Draw glow effect behind the button
                val glowRadius = 4.dp.toPx()
                val glowColor = SubscriptionGreen.copy(alpha = glowAlpha)
                val cornerRadiusPx = 8.dp.toPx()
                
                drawRoundRect(
                    color = glowColor,
                    topLeft = androidx.compose.ui.geometry.Offset(-glowRadius, -glowRadius),
                    size = androidx.compose.ui.geometry.Size(
                        size.width + 2 * glowRadius,
                        size.height + 2 * glowRadius
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        cornerRadiusPx + glowRadius
                    )
                )
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = SubscriptionGreen
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {

            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Upgrade to Premium",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
        }
    }
}