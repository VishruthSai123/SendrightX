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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val buttons: List<String>,
    val isExpandable: Boolean = false,
    val isExpanded: Boolean = false
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
    
    // AI Usage Tracking
    val aiUsageTracker = remember { AiUsageTracker.getInstance() }
    val aiUsageStats by aiUsageTracker.usageStats.collectAsState()
    
    // User Manager for subscription status
    val userManager = remember { UserManager.getInstance() }
    val userData by userManager.userData.collectAsState()
    
    // Get subscription manager for real-time subscription state
    val subscriptionManager = userManager.getSubscriptionManager()
    val isPro by subscriptionManager?.isPro?.collectAsState() ?: remember { mutableStateOf(false) }
    
    // Determine pro status from multiple sources for immediate updates
    val isProUser = isPro || userData?.subscriptionStatus == "pro"
    
    // Rewarded Ad Manager
    val rewardedAdManager = remember { RewardedAdManager(context) }
    var showLimitDialog by remember { mutableStateOf(false) }
    
    // Force refresh UI when subscription status changes
    LaunchedEffect(isProUser) {
        if (isProUser) {
            // Force recomposition when user becomes pro
            context.showShortToast("🎉 Pro features unlocked!")
        }
    }
    
    // State for managing expanded sections
    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }
    
    val magicWandSections = listOf(
        MagicWandSection(
            title = "Advanced",
            buttons = listOf("Rewrite", "Summarise", "Letter", "Optimise", "Formal", "Post Ready")
        ),
        MagicWandSection(
            title = "Study",
            buttons = listOf("Explain", "Equation", "Solution")
        ),
        MagicWandSection(
            title = "Tone Changer", 
            buttons = listOf("Casual", "Friendly", "Professional", "Flirty", "Anger", "Happy")
        ),
        MagicWandSection(
            title = "Translation",
            buttons = listOf("Telugu", "Hindi", "Tamil", "English", "Multi"),
            isExpandable = true,
            isExpanded = expandedSections["Translation"] ?: false
        ),
        MagicWandSection(
            title = "Other",
            buttons = listOf("Emojie", "Chat"),
            isExpandable = true,
            isExpanded = expandedSections["Other"] ?: false
        )
    )

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
                        onButtonClick = { buttonTitle ->
                            scope.launch {
                                // Check if user is pro - use the reactive isProUser variable
                                if (isProUser) {
                                    // Pro users get unlimited access
                                    handleMagicWandButtonClick(
                                        buttonTitle = buttonTitle,
                                        editorInstance = editorInstance,
                                        context = context
                                    )
                                } else {
                                    // Check AI usage for free users
                                    val isAllowed = aiUsageTracker.recordAiAction()
                                    
                                    if (isAllowed) {
                                        handleMagicWandButtonClick(
                                            buttonTitle = buttonTitle,
                                            editorInstance = editorInstance,
                                            context = context
                                        )
                                    } else {
                                        // Force refresh the UI by getting the latest stats
                                        val updatedStats = aiUsageTracker.getUsageStats()
                                        val canUseAd = userManager.canUseRewardedAd()
                                        
                                        if (updatedStats.remainingActions() == 0) {
                                            if (canUseAd) {
                                                context.showShortToast("Daily limit reached! Watch an ad to unlock 60 minutes of unlimited AI.")
                                                showLimitDialog = true
                                            } else {
                                                context.showShortToast("You've reached your daily limit and used your free ad. Upgrade to Pro or wait until tomorrow!")
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onToggleExpand = { sectionTitle ->
                            expandedSections[sectionTitle] = !(expandedSections[sectionTitle] ?: false)
                        }
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
                        if (!isProUser) {
                            // Show usage info for free users
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
            }
        }
    }
    
    // Limit Reached Panel - will be handled by a separate panel component
    // This section is intentionally left empty as the AI limit panel will be shown instead
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
    onButtonClick: (String) -> Unit,
    onToggleExpand: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header with expand/collapse functionality
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (section.isExpandable) {
                        onToggleExpand(section.title)
                    }
                }
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SnyggText(
                elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                text = section.title,
                modifier = Modifier.weight(1f)
            )
            
            if (section.isExpandable) {
                Icon(
                    imageVector = if (section.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (section.isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // Content - Show only if not expandable or if expanded
        if (!section.isExpandable || section.isExpanded) {
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
                                onClick = { onButtonClick(section.buttons[i]) },
                                modifier = Modifier.weight(1f)
                            )
                            MagicWandButton(
                                button = MagicWandButton(title = section.buttons[i + 1]),
                                onClick = { onButtonClick(section.buttons[i + 1]) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        // Single button in this row - take full width without extra spacing
                        MagicWandButton(
                            button = MagicWandButton(title = section.buttons[i]),
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SnyggButton(
        elementName = FlorisImeUi.SmartbarActionTile.elementName,
        onClick = onClick,
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
            SnyggText(
                elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                text = button.title
            )
        }
    }
}

private suspend fun handleMagicWandButtonClick(
    buttonTitle: String,
    editorInstance: com.vishruth.key1.ime.editor.EditorInstance,
    context: android.content.Context
) {
    try {
        // Special handling for Chat button
        if (buttonTitle == "Chat") {
            // Get all text from the input field (same as other AI actions)
            val activeContent = editorInstance.activeContent
            val allText = buildString {
                append(activeContent.textBeforeSelection)
                append(activeContent.selectedText)
                append(activeContent.textAfterSelection)
            }
            
            flogDebug { "Chat - All text: '$allText'" }
            
            if (allText.isBlank()) {
                context.showShortToast("Please type some text first")
                return
            }
            
            // Show processing message
            context.showShortToast("Thinking...")
            
            // Get instruction for chat
            val instruction = MagicWandInstructions.getInstructionForButton(buttonTitle)
            
            // Call Gemini API with chat instruction
            val result = GeminiApiService.transformText(allText, instruction)
            
            result.onSuccess { responseText ->
                flogDebug { "Chat response: '$responseText'" }
                // Replace all text with chat response (same as other AI actions)
                val activeContent = editorInstance.activeContent
                val totalTextLength = activeContent.textBeforeSelection.length + 
                                     activeContent.selectedText.length + 
                                     activeContent.textAfterSelection.length
                
                // Select all text by setting selection from 0 to total length
                editorInstance.setSelection(0, totalTextLength)
                editorInstance.deleteSelectedText()
                editorInstance.commitText(responseText)
                context.showShortToast("Response received!")
            }.onFailure { error ->
                context.showShortToast("Chat error: ${error.message ?: "Something went wrong"}")
            }
            
            return
        }
        
        // Handle all other buttons with existing logic
        // Get all text from the input field
        val activeContent = editorInstance.activeContent
        val allText = buildString {
            append(activeContent.textBeforeSelection)
            append(activeContent.selectedText)
            append(activeContent.textAfterSelection)
        }
        
        if (allText.isBlank()) {
            context.showShortToast("Please type some text first")
            return
        }
        
        // Show processing message
        context.showShortToast("Processing...")
        
        // Get instruction for the button
        val instruction = MagicWandInstructions.getInstructionForButton(buttonTitle)
        
        // Call Gemini API
        val result = GeminiApiService.transformText(allText, instruction)
        
        result.onSuccess { transformedText ->
            // Replace all text with transformed text
            val activeContent = editorInstance.activeContent
            val totalTextLength = activeContent.textBeforeSelection.length + 
                                 activeContent.selectedText.length + 
                                 activeContent.textAfterSelection.length
            
            // Select all text by setting selection from 0 to total length
            editorInstance.setSelection(0, totalTextLength)
            editorInstance.deleteSelectedText()
            editorInstance.commitText(transformedText)
            context.showShortToast("Text transformed!")
        }.onFailure { error ->
            context.showShortToast(error.message ?: "Something went wrong")
        }
        
    } catch (e: Exception) {
        context.showShortToast("Something went wrong. Please try again.")
    }
}