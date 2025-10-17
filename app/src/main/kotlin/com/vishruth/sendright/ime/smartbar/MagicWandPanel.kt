/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vishruth.key1.ime.smartbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.LinearLayout
import android.widget.TextView

import com.vishruth.key1.editorInstance
import com.vishruth.key1.ime.ai.AiUsageTracker
import com.vishruth.key1.ime.ai.AiUsageStats
import com.vishruth.key1.ime.keyboard.FlorisImeSizing
import com.vishruth.key1.ime.theme.FlorisImeTheme
import com.vishruth.key1.ime.theme.FlorisImeUi
import com.vishruth.key1.keyboardManager
import com.vishruth.key1.lib.ads.RewardedAdManager
import com.vishruth.key1.lib.devtools.flogDebug
import com.vishruth.key1.user.UserManager
import com.vishruth.key1.app.settings.aiworkspace.AIWorkspaceManager
import com.vishruth.sendright.lib.network.NetworkUtils
import com.vishruth.key1.ime.smartbar.GeminiApiService
import com.vishruth.key1.ime.smartbar.ActionResultPanelManager
import com.vishruth.key1.ime.smartbar.MagicWandInstructions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * Formats time remaining in milliseconds to a human-readable string
 * @param timeMs Time in milliseconds
 * @return Formatted string like "23 hours 45 minutes" or "45 minutes" or "30 seconds"
 */
private fun formatTimeRemaining(timeMs: Long): String {
    if (timeMs <= 0) return "0 seconds"
    
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return when {
        hours > 0 -> {
            if (minutes > 0) {
                "${hours} hour${if (hours != 1L) "s" else ""} ${minutes} minute${if (minutes != 1L) "s" else ""}"
            } else {
                "${hours} hour${if (hours != 1L) "s" else ""}"
            }
        }
        minutes > 0 -> {
            "${minutes} minute${if (minutes != 1L) "s" else ""}"
        }
        else -> {
            "${seconds} second${if (seconds != 1L) "s" else ""}"
        }
    }
}

/**
 * Formats time remaining for compact display (for shorter text areas)
 * @param timeMs Time in milliseconds
 * @return Formatted string like "23h 45m" or "45m" or "30s"
 */
private fun formatTimeRemainingCompact(timeMs: Long): String {
    if (timeMs <= 0) return "0s"
    
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return when {
        hours > 0 -> {
            if (minutes > 0) {
                "${hours}h ${minutes}m"
            } else {
                "${hours}h"
            }
        }
        minutes > 0 -> {
            "${minutes}m"
        }
        else -> {
            "${seconds}s"
        }
    }
}

/**
 * Formats time remaining for reward track card display
 * Shows only hours left (e.g., "23 hrs left") if >1 hour, otherwise minutes (e.g., "45 min left")
 */
private fun formatRewardTrackTime(timeMs: Long): String {
    if (timeMs <= 0) return "0 min left"
    val totalMinutes = timeMs / (60 * 1000)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours} hr${if (hours != 1L) "s" else ""} left"
        else -> "${minutes} min left"
    }
}

data class MagicWandSection(
    val title: String,
    val buttons: List<String>
)

data class MagicWandButton(
    val title: String
)

@Composable
fun MagicWandPanel(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val scope = rememberCoroutineScope()
    
    // User Manager for subscription status - initialize first
    val userManager = remember { UserManager.getInstance() }
    val userData by userManager.userData.collectAsState()
    val subscriptionManager = userManager.getSubscriptionManager()
    val isPro by subscriptionManager?.isPro?.collectAsState() ?: remember { mutableStateOf(false) }
    
    // Real-time pro status - initialize dynamically with keys to prevent caching issues
    var isProUser by remember(isPro, userData?.subscriptionStatus) { 
        mutableStateOf(
            // Initialize with best available info immediately, keys ensure reset on changes
            isPro || userData?.subscriptionStatus in listOf("pro", "premium")
        ) 
    }
    
    // IMMEDIATE Pro status update when collectAsState values change - no waiting for background refresh
    LaunchedEffect(isPro, userData?.subscriptionStatus) {
        val immediateProStatus = isPro || userData?.subscriptionStatus in listOf("pro", "premium")
        isProUser = immediateProStatus
    }
    
    // Force refresh pro status on every panel open with immediate fallback handling
    LaunchedEffect(Unit) {
        try {
            // Force subscription status refresh
            subscriptionManager?.forceRefreshSubscriptionStatus()
            delay(200) // Allow refresh to complete
            
            // Get fresh status from all sources
            val freshSubscriptionStatus = subscriptionManager?.isPro?.value ?: false
            val freshUserDataStatus = userData?.subscriptionStatus in listOf("pro", "premium")
            val newProStatus = freshSubscriptionStatus || freshUserDataStatus
            
            // Update Pro status
            isProUser = newProStatus
            
            // If user is NOT pro, immediately refresh usage stats to show current limits
            if (!newProStatus) {
                try {
                    AiUsageTracker.getInstance().forceCompleteRefresh()
                } catch (e: Exception) {
                    // Silent fail for usage tracking
                }
            }
        } catch (e: Exception) {
            // Fallback - ensure we default to non-pro if checks fail
            val fallbackProStatus = isPro || userData?.subscriptionStatus in listOf("pro", "premium")
            isProUser = fallbackProStatus
            
            // If fallback shows non-pro, refresh usage stats
            if (!fallbackProStatus) {
                try {
                    AiUsageTracker.getInstance().forceCompleteRefresh()
                } catch (e: Exception) {
                    // Silent fail
                }
            }
        }
    }
    
    // Dynamic AI Usage Stats - initialize with keys to prevent caching on subscription changes
    var aiUsageStats by remember(isProUser) { 
        mutableStateOf(
            if (isProUser) {
                // Pro user - no limits
                AiUsageStats.empty() 
            } else {
                // Free user - will be updated by LaunchedEffect, but start with empty to avoid null
                AiUsageStats.empty()
            }
        )
    }
    
    // Update usage stats whenever Pro status changes
    LaunchedEffect(isProUser) {
        if (!isProUser) {
            // User is not pro - collect real usage stats
            try {
                val aiUsageTrackerInstance = AiUsageTracker.getInstance()
                aiUsageTrackerInstance.forceCompleteRefresh()
                // Start collecting live usage stats
                launch {
                    aiUsageTrackerInstance.usageStats.collect { stats ->
                        aiUsageStats = stats
                    }
                }
            } catch (e: Exception) {
                // Fallback to empty stats
                aiUsageStats = AiUsageStats.empty()
            }
        } else {
            // User is pro - use empty stats (no limits)
            aiUsageStats = AiUsageStats.empty()
        }
    }
    
    // AI Usage Tracking - always available
    val aiUsageTracker = remember { AiUsageTracker.getInstance() }
    
    // AI Workspace Manager for dynamic actions
    val aiWorkspaceManager = remember { AIWorkspaceManager.getInstance(context) }
    
    // Usage tracking for dynamic section ordering
    val usageTracker = remember { MagicWandUsageTracker.getInstance(context) }
    val orderedSectionTitles by usageTracker.orderedSections.collectAsState()
    
    // Separate effect for section ordering
    LaunchedEffect(Unit) {
        usageTracker.refreshOrderedSections()
    }
    
    // Usage tracking only for confirmed non-pro users - reactive to status changes
    LaunchedEffect(isProUser) {
        if (!isProUser) {
            delay(100)
            try {
                aiUsageTracker.forceCompleteRefresh()
            } catch (e: Exception) {
                // Silent fail for usage tracking
            }
        }
    }
    
    // State management - reactive to Pro status changes
    val rewardedAdManager = remember(isProUser) { 
        if (!isProUser) RewardedAdManager(context) else null 
    }
    var showLimitDialog by remember { mutableStateOf(false) }
    var loadingButton by remember { mutableStateOf<String?>(null) }
    
    // Pro user welcome - stable
    LaunchedEffect(isProUser) {
        if (isProUser && !userManager.hasProFeaturesToastBeenShown()) {
            delay(200)
            context.showShortToast("🎉 Pro features unlocked!")
            userManager.markProFeaturesToastAsShown()
        }
    }
    
    // Create AI Workspace section dynamically - refreshed for each panel open
    val aiWorkspaceButtons = remember(aiWorkspaceManager, isProUser) {
        val buttons = mutableListOf<String>()
        
        // Get all available actions fresh each time
        val customActions = aiWorkspaceManager.getEnabledCustomActions()
        val popularActions = aiWorkspaceManager.enabledPopularActions
        
        // Add all custom actions first (prioritized)
        customActions.forEach { action ->
            buttons.add(action.title)
        }
        
        // Add all popular actions
        popularActions.forEach { action ->
            buttons.add(action.title)
        }
        
        // Fallback if no actions available
        if (buttons.isEmpty()) {
            buttons.add("Humanise") // Default fallback
        }
        
        // Add "Create AI" button as the last button
        buttons.add("Create AI")
        
        buttons
    }

    // Define all sections (order will be dynamically managed by usage tracker)
    val allSections = mapOf(
        "AI Workspace" to MagicWandSection(
            title = "AI Workspace",
            buttons = aiWorkspaceButtons
        ),
        "Enhance" to MagicWandSection(
            title = "Enhance",
            buttons = listOf("Rewrite", "Grammar")
        ),
        "Tone Changer" to MagicWandSection(
            title = "Tone Changer", 
            buttons = listOf("Casual", "Friendly", "Professional", "Flirty", "Anger", "Happy")
        ),
        "Advanced" to MagicWandSection(
            title = "Advanced",
            buttons = listOf("Summarise", "Letter", "Optimise", "Formal", "Post Ready")
        ),
        "Study" to MagicWandSection(
            title = "Study",
            buttons = listOf("Explain", "Equation", "Solution")
        ),
        "Others" to MagicWandSection(
            title = "Others",
            buttons = listOf("Emojie", "Realistic")
        )
    )
    
    // Create ordered sections based on usage
    val magicWandSections = remember(orderedSectionTitles, aiWorkspaceButtons) {
        val defaultOrder = listOf("AI Workspace", "Enhance", "Tone Changer", "Advanced", "Study", "Others")
        val sectionOrder = if (orderedSectionTitles.isNotEmpty()) orderedSectionTitles else defaultOrder
        
        sectionOrder.mapNotNull { title ->
            allSections[title]
        }
    }

    // Ad preloading for non-pro users only
    if (!isProUser) {
        com.vishruth.key1.ui.components.AdPreloader(
            panelName = "MagicWand"
        )
    }
    
    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionsOverflow.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight())
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
            .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Get updated subscription status and capabilities
            val canUseRewardedAd = userManager.canUseRewardedAd()
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(magicWandSections) { section ->
                    MagicWandSectionItem(
                        section = section,
                        loadingButton = loadingButton,
                        onButtonClick = { buttonTitle ->
                            scope.launch {
                                // Record section usage for dynamic ordering
                                usageTracker.recordSectionUsage(section.title)
                                
                                // Special handling for "Create AI" button
                                if (buttonTitle == "Create AI") {
                                    // Navigate to AI Workspace management screen
                                    try {
                                        val intent = android.content.Intent(context, com.vishruth.key1.app.FlorisAppActivity::class.java).apply {
                                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                            data = android.net.Uri.parse("ui://florisboard/settings/ai-workspace")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        context.showShortToast("Unable to open AI Workspace settings")
                                    }
                                    return@launch
                                }
                                
                                // IMMEDIATELY show loading state for instant user feedback
                                loadingButton = buttonTitle
                                
                                // Always refresh and check pro status in real-time (cache-free)
                                var isCurrentlyPro = false
                                try {
                                    // Force fresh subscription check
                                    subscriptionManager?.forceRefreshSubscriptionStatus()
                                    delay(100) // Small delay for refresh
                                    
                                    // Get real-time status from all sources
                                    val liveSubscriptionStatus = subscriptionManager?.isPro?.value ?: false
                                    val liveUserDataStatus = userData?.subscriptionStatus in listOf("pro", "premium")
                                    isCurrentlyPro = liveSubscriptionStatus || liveUserDataStatus
                                } catch (e: Exception) {
                                    // Fallback check
                                    isCurrentlyPro = isProUser
                                }
                                
                                if (isCurrentlyPro) {
                                    try {
                                        handleMagicWandButtonClick(
                                            buttonTitle = buttonTitle,
                                            editorInstance = editorInstance,
                                            context = context,
                                            aiUsageTracker = null
                                        )
                                    } catch (e: Exception) {
                                        context.showShortToast("Error: ${e.message ?: "Something went wrong"}")
                                    } finally {
                                        loadingButton = null
                                    }
                                } else {
                                    try {
                                        aiUsageTracker.forceCompleteRefresh()
                                        val isAllowed = aiUsageTracker.canUseAiAction()
                                        
                                        if (isAllowed) {
                                            try {
                                                handleMagicWandButtonClick(
                                                    buttonTitle = buttonTitle,
                                                    editorInstance = editorInstance,
                                                    context = context,
                                                    aiUsageTracker = aiUsageTracker
                                                )
                                            } catch (e: Exception) {
                                                context.showShortToast("Error: ${e.message ?: "Something went wrong"}")
                                            } finally {
                                                loadingButton = null
                                            }
                                        } else {
                                            // Stop loading since we're showing limit dialog instead
                                            loadingButton = null
                                            val canUseAd = userManager.canUseRewardedAd()
                                            if (canUseAd && rewardedAdManager != null) {
                                                context.showShortToast("Daily limit reached! Watch an ad to unlock 60 minutes of unlimited AI.")
                                                showLimitDialog = true
                                            } else {
                                                context.showShortToast("You've reached your daily limit. Upgrade to Pro or wait until tomorrow!")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        loadingButton = null // Stop loading on error
                                        context.showShortToast("Error checking limits. Please try again.")
                                    }
                                }
                            }
                        },
                    )
                }
                
                // AI Usage card at the bottom with green background
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .background(
                                color = Color(0xFF23C546),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        // Immediately reactive to Pro status changes
                        if (!isProUser) {
                            // Show usage info for free users with live stats
                            Column {
                                if (aiUsageStats.isRewardWindowActive) {
                                    // Reward window active
                                    Text(
                                        text = "Unlimited AI Mode",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Enjoy unlimited AI actions for the next ${formatRewardTrackTime(aiUsageStats.rewardWindowTimeRemaining())}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                } else {
                                    // Normal usage
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "AI Usage: ${aiUsageStats.dailyActionCount} / ${AiUsageStats.DAILY_LIMIT}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (aiUsageStats.dailyActionCount >= AiUsageStats.DAILY_LIMIT - 2) {
                                            // Show ad option when user is close to limit
                                            Text(
                                                text = if (canUseRewardedAd) "Watch Ad" else "Ad Used This Month",
                                                color = if (canUseRewardedAd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                    
                                    // Progress bar
                                    LinearProgressIndicator(
                                        progress = { (aiUsageStats.dailyActionCount.toFloat() / AiUsageStats.DAILY_LIMIT).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        color = if (aiUsageStats.dailyActionCount >= AiUsageStats.DAILY_LIMIT) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                                }
                            }
                        } else {
                            // Show usage info for pro users
                            Column {
                                Text(
                                    text = "Pro User",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Enjoy unlimited AI actions with no restrictions",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                
                // Ad banner - only for free users
                if (!isProUser) {
                    item {
                        com.vishruth.key1.ui.components.AdBannerCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            onAdLoaded = {
                                flogDebug { "Magic Wand Panel: Banner ad loaded successfully" }
                            },
                            onAdFailedToLoad = { error ->
                                flogDebug { "Magic Wand Panel: Banner ad failed to load - ${error.message}" }
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Limit dialog for free users only - reactive to Pro status changes
    if (!isProUser && showLimitDialog && rewardedAdManager != null) {
        AiLimitDialog(
            usageStats = aiUsageStats,
            onWatchAd = {
                showLimitDialog = false
                scope.launch {
                    // Handle rewarded ad watching
                    try {
                        // Implementation would go here
                        context.showShortToast("Watching ad...")
                    } catch (e: Exception) {
                        context.showShortToast("Ad not available. Please try again later.")
                    }
                }
            },
            onDismiss = {
                showLimitDialog = false
            }
        )
    }
    
    // Auto-dismiss limit dialog if user becomes Pro
    LaunchedEffect(isProUser) {
        if (isProUser && showLimitDialog) {
            showLimitDialog = false
        }
    }
}

@Composable
private fun AiUsageStatsHeader(
    usageStats: AiUsageStats,
    onWatchAdClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        if (usageStats.isRewardWindowActive) {
            // Reward window active - show timer text only (remove the extend button)
            // This avoids SpaceBetween arrangement issues
            
            // Real-time timer state
            var displayMinutes by remember { mutableStateOf(0) }
            var displaySeconds by remember { mutableStateOf(0) }
            
            // Initialize timer values
            LaunchedEffect(usageStats.isRewardWindowActive) {
                val timeRemaining = usageStats.rewardWindowTimeRemaining()
                displayMinutes = (timeRemaining / 60000).toInt()
                displaySeconds = ((timeRemaining % 60000) / 1000).toInt()
            }
            
            // Update timer every second
            LaunchedEffect(usageStats.isRewardWindowActive) {
                if (usageStats.isRewardWindowActive) {
                    while (true) {
                        delay(1000) // Update every second
                        val updatedTimeRemaining = usageStats.rewardWindowTimeRemaining()
                        displayMinutes = (updatedTimeRemaining / 60000).toInt()
                        displaySeconds = ((updatedTimeRemaining % 60000) / 1000).toInt()
                        
                        // Break if reward window is no longer active
                        if (!usageStats.isRewardWindowActive || updatedTimeRemaining <= 0) {
                            break
                        }
                    }
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Timer",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                SnyggText(
                    elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                    text = "Unlimited AI - ${displayMinutes}:${String.format("%02d", displaySeconds)}",
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // Regular usage stats
            val remaining = usageStats.remainingActions()
            
            // Always show the usage text
            SnyggText(
                elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                text = "AI Actions: $remaining/${AiUsageStats.DAILY_LIMIT}"
            )
            
            // Show button only when all actions are used up (remaining == 0)
            // Button will be placed below the progress bar now
        }
        
        // Progress bar for usage - always shown
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val progress = if (usageStats.isRewardWindowActive) {
                1f // Full bar when in reward window
            } else {
                1f - (usageStats.remainingActions().toFloat() / AiUsageStats.DAILY_LIMIT)
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(
                        if (usageStats.isRewardWindowActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
            )
        }
        
        // Show button only when all actions are used up (remaining == 0)
        // This is now placed below the progress bar
        val remaining = usageStats.remainingActions()
        if (remaining == 0 && !usageStats.isRewardWindowActive) {
            Spacer(modifier = Modifier.height(4.dp))
            SnyggButton(
                elementName = FlorisImeUi.SmartbarActionTile.elementName,
                onClick = onWatchAdClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)  // Consistent height with other buttons
                    .clip(RoundedCornerShape(6.dp))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Watch Ad",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        SnyggText(
                            elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                            text = "Watch Ad"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiLimitDialog(
    usageStats: AiUsageStats,
    onWatchAd: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // User Manager for real-time subscription status
    val userManager = remember { UserManager.getInstance() }
    val userData by userManager.userData.collectAsState()
    
    // Enhanced subscription observation for immediate updates
    val subscriptionManager = remember { userManager.getSubscriptionManager() }
    var isProFromSubscriptionManager by remember(subscriptionManager) { mutableStateOf(false) }
    
    // Observe subscription manager's isPro StateFlow if available
    LaunchedEffect(subscriptionManager) {
        subscriptionManager?.isPro?.collect { isPro ->
            isProFromSubscriptionManager = isPro
        }
    }
    
    // Check if user is pro from multiple sources for immediate updates
    var isProUser by remember(userData?.subscriptionStatus, isProFromSubscriptionManager) { mutableStateOf(false) }
    
    // Update pro status from all available sources
    LaunchedEffect(userData, isProFromSubscriptionManager) {
        isProUser = userData?.subscriptionStatus == "pro" || isProFromSubscriptionManager
    }
    
    // Auto-dismiss dialog if user becomes Pro (same logic as AiLimitPanel)
    LaunchedEffect(isProUser) {
        if (isProUser) {
            onDismiss()
        }
    }
    
    // IMMEDIATE cache-free check on dialog open - no continuous battery-draining loops
    LaunchedEffect(Unit) {
        try {
            // Complete cache-free refresh when dialog opens
            val aiUsageTracker = AiUsageTracker.getInstance()
            aiUsageTracker.forceCompleteRefresh()
            val currentStats = aiUsageTracker.getUsageStats()
            
            // Auto-dismiss immediately if user actually has available actions (shouldn't show limit dialog)
            if (currentStats.remainingActions() > 0 && !isProUser) {
                onDismiss()
                return@LaunchedEffect
            }
            
            // Also dismiss immediately if user is pro
            if (isProUser) {
                onDismiss()
                return@LaunchedEffect
            }
        } catch (e: Exception) {
            // If refresh fails, continue showing dialog
        }
    }
    
    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionsOverflow.elementName,
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                SnyggText(
                    elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                    text = "AI Limit Reached"
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SnyggText(
                elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                text = "You've used all ${AiUsageStats.DAILY_LIMIT} free AI actions for today.",
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SnyggText(
                elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                text = "Watch a short ad to unlock 60 minutes of unlimited AI actions!",
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SnyggButton(
                    elementName = FlorisImeUi.SmartbarActionTile.elementName,
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    SnyggText(
                        elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                        text = "Later"
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                SnyggButton(
                    elementName = FlorisImeUi.SmartbarActionTile.elementName,
                    onClick = onWatchAd,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Watch Ad",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        SnyggText(
                            elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                            text = "Watch Ad"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MagicWandSectionItem(
    section: MagicWandSection,
    loadingButton: String?,
    onButtonClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header - Always visible, no expand/collapse functionality
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SnyggText(
                elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                text = section.title,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Content - Always shown for all sections
        if (section.title == "AI Workspace") {
            // AI Workspace: Display all buttons horizontally in a single scrollable row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(section.buttons) { buttonTitle ->
                    AIWorkspaceButton(
                        button = MagicWandButton(title = buttonTitle),
                        isLoading = loadingButton == buttonTitle,
                        onClick = { onButtonClick(buttonTitle) },
                        modifier = Modifier.fillMaxWidth() // Full width for complete text visibility
                    )
                }
            }
        } else {
            // Other sections: Keep the existing 2-button-per-row layout
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Process buttons in rows of 2, but handle odd numbers correctly
                for (i in section.buttons.indices step 2) {
                    if (i + 1 < section.buttons.size) {
                        // Two buttons in this row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MagicWandButton(
                                button = MagicWandButton(title = section.buttons[i]),
                                isLoading = loadingButton == section.buttons[i],
                                onClick = { onButtonClick(section.buttons[i]) },
                                modifier = Modifier.weight(1f)
                            )
                            MagicWandButton(
                                button = MagicWandButton(title = section.buttons[i + 1]),
                                isLoading = loadingButton == section.buttons[i + 1],
                                onClick = { onButtonClick(section.buttons[i + 1]) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        // Single button in this row - take full width without extra spacing
                        MagicWandButton(
                            button = MagicWandButton(title = section.buttons[i]),
                            isLoading = loadingButton == section.buttons[i],
                            onClick = { onButtonClick(section.buttons[i]) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MagicWandButton(
    button: MagicWandButton,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SnyggButton(
        elementName = FlorisImeUi.SmartbarActionTile.elementName,
        onClick = if (isLoading) { {} } else onClick, // Disable click when loading
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF23C546) // Green loading color as requested
                )
            } else {
                SnyggText(
                    elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                    text = button.title
                )
            }
        }
    }
}

@Composable
private fun AIWorkspaceButton(
    button: MagicWandButton,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SnyggButton(
        elementName = FlorisImeUi.SmartbarActionTile.elementName,
        onClick = onClick,
        modifier = modifier
            .height(60.dp) // Original height restored
            .clip(RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), // Slightly smaller loading indicator
                    strokeWidth = 2.dp,
                    color = Color(0xFF23C546) // Green loading color as requested
                )
            } else {
                SnyggText(
                    elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                    text = button.title
                )
            }
        }
    }
}

suspend fun handleMagicWandButtonClick(
    buttonTitle: String,
    editorInstance: com.vishruth.key1.ime.editor.EditorInstance,
    context: android.content.Context,
    aiUsageTracker: com.vishruth.key1.ime.ai.AiUsageTracker?
) {
    try {
        // Get all text from the input field
        val activeContent = editorInstance.activeContent
        val allText = buildString {
            append(activeContent.textBeforeSelection)
            append(activeContent.selectedText)
            append(activeContent.textAfterSelection)
        }
        
        flogDebug { "$buttonTitle - All text: '$allText'" }
        
        if (allText.isBlank()) {
            context.showShortToast("Please type some text first")
            return
        }
        
        // Check network connectivity before making API call
        if (!NetworkUtils.checkNetworkAndShowToast(context)) {
            return
        }
        
        // Check if this is an AI Workspace action
        val aiWorkspaceManager = AIWorkspaceManager.getInstance(context)
        val allAIActions = aiWorkspaceManager.getAllEnabledActions()
        val aiAction = allAIActions.find { it.title == buttonTitle }
        
        // Get instruction for the button
        val contextManager = com.vishruth.key1.app.settings.context.ContextManager.getInstance(context)
        val instruction = when {
            aiAction != null -> {
                // Build enhanced prompt for AI Workspace actions with context options
                if (aiAction.includePersonalDetails || aiAction.includeDateTime) {
                    buildEnhancedPrompt(aiAction, contextManager)
                } else {
                    aiAction.prompt // Use basic custom prompt
                }
            }
            else -> {
                // Enhanced handling for standard AI Actions and Chat
                val baseInstruction = MagicWandInstructions.getInstructionForButton(buttonTitle)
                
                // Check if context is configured for Advanced Actions and Chat (built-in when configured)
                if (contextManager.isContextConfigured() && 
                    (isAdvancedSectionAction(buttonTitle) || buttonTitle == "Chat")) {
                    buildEnhancedStandardPrompt(baseInstruction, contextManager)
                } else {
                    baseInstruction // Use basic standard instruction
                }
            }
        }
        
        // Call Gemini API
        val result = GeminiApiService.transformText(allText, instruction, context)
        
        result.onSuccess { transformedText ->
            // Validate response before showing action result panel
            if (transformedText.isBlank()) {
                context.showShortToast("🤔 Empty response received. Please try again.")
                return@onSuccess
            }
            
            // Record successful AI action only for non-pro users
            aiUsageTracker?.let {
                it.recordSuccessfulAiAction()
                it.forceRefreshUsageStats()
            }
            
            // Get or create the ActionResultPanelManager instance
            val actionResultManager = ActionResultPanelManager.getCurrentInstance() 
                ?: ActionResultPanelManager.getInstance(editorInstance, context)
            
            // Show the action result panel with the response
            actionResultManager.showActionResult(
                originalText = allText,
                transformedText = transformedText,
                actionTitle = buttonTitle,
                instruction = instruction
            )
            
            // Get keyboard manager and show action result panel
            val keyboardManager = context.keyboardManager().value
            keyboardManager.showActionResultPanel()
            
        }.onFailure { error ->
            val errorMessage = error.message ?: "Something went wrong"
            // Show more specific error messages for better user experience
            when {
                errorMessage.contains("timeout") || errorMessage.contains("slow") -> {
                    context.showShortToast("⏱️ Service timeout. Trying backup server...")
                }
                errorMessage.contains("All API keys failed") -> {
                    context.showShortToast("🔄 Switching to backup server...")
                }
                errorMessage.contains("network") || errorMessage.contains("connection") -> {
                    context.showShortToast("📶 Please check your internet connection")
                }
                else -> {
                    context.showShortToast(errorMessage)
                }
            }
        }
        
    } catch (e: Exception) {
        context.showShortToast("Something went wrong. Please try again.")
        flogDebug { "Error in handleMagicWandButtonClick: ${e.message}" }
    }
}

private fun buildEnhancedPrompt(
    aiAction: com.vishruth.key1.app.settings.aiworkspace.AIAction, 
    contextManager: com.vishruth.key1.app.settings.context.ContextManager
): String {
    val basePrompt = aiAction.prompt
    val enhancements = mutableListOf<String>()
    
    // Always include action title and description with clear user perspective
    enhancements.add("🎯 USER'S CUSTOM ACTION:\n• User named this action: \"${aiAction.title}\"\n• User's description: \"${aiAction.description}\"\n• Important: All references (like 'my coach', 'my boss', 'my friend') refer to the USER'S relationships, not yours. When user asks to write emails or messages, you're helping the USER communicate with THEIR contacts.")
    
    if (aiAction.includePersonalDetails) {
        val contextInstruction = contextManager.generateContextInstruction()
        enhancements.add(contextInstruction)
    } else {
        // Even without personal details, include response length preference
        // Get fresh personal details to ensure we have the latest response length setting
        val personalDetails = contextManager.getFreshPersonalDetails()
        val responseLengthInstruction = when (personalDetails.responseLength.lowercase()) {
            "short" -> "7. RESPONSE LENGTH: Keep responses concise and to-the-point. Provide brief, clear answers without unnecessary elaboration."
            "medium" -> "7. RESPONSE LENGTH: Provide balanced responses with adequate detail. Include necessary explanations while maintaining clarity."
            "lengthy" -> "7. RESPONSE LENGTH: Provide comprehensive, detailed responses. Include thorough explanations, examples, and additional context when helpful."
            else -> "7. RESPONSE LENGTH: Provide balanced responses with adequate detail. Include necessary explanations while maintaining clarity."
        }
        enhancements.add(responseLengthInstruction)
    }
    
    if (aiAction.includeDateTime) {
        val currentDateTime = java.time.LocalDateTime.now()
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
        val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
        enhancements.add("📅 CURRENT CONTEXT:\n• Today is: ${currentDateTime.format(dateFormatter)}\n• Current time: ${currentDateTime.format(timeFormatter)}")
    }
    
    return enhancements.joinToString("\n\n") + "\n\n" + basePrompt
}

/**
 * Check if a button title belongs to the Advanced section
 */
private fun isAdvancedSectionAction(buttonTitle: String): Boolean {
    val advancedSectionButtons = listOf("Summarise", "Letter", "Optimise", "Formal", "Post Ready")
    return buttonTitle in advancedSectionButtons
}

/**
 * Build enhanced prompt for standard AI Actions and Chat with personal details
 */
private fun buildEnhancedStandardPrompt(
    baseInstruction: String,
    contextManager: com.vishruth.key1.app.settings.context.ContextManager
): String {
    // Get contextual intelligence (already includes date/time)
    val contextInstruction = contextManager.generateContextInstruction()
    
    return contextInstruction + "\n\n🎯 PRIMARY TASK:\n" + baseInstruction
}

