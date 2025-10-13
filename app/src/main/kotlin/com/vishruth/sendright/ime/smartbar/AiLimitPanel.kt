/*
 * SendRight - AI-Enhanced Android Keyboard
 * Built upon FlorisBoard by The FlorisBoard Contributors
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

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vishruth.key1.app.RewardedAdActivity
import com.vishruth.key1.editorInstance
import com.vishruth.key1.ime.ai.AiUsageStats
import com.vishruth.key1.ime.ai.AiUsageTracker
import com.vishruth.key1.ime.keyboard.FlorisImeSizing
import com.vishruth.key1.ime.theme.FlorisImeTheme
import com.vishruth.key1.ime.theme.FlorisImeUi
import com.vishruth.key1.ime.media.KeyboardLikeButton
import com.vishruth.key1.ime.text.keyboard.TextKeyData
import com.vishruth.key1.keyboardManager
import com.vishruth.key1.user.UserManager
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

// Extension function to find the activity context
fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AiLimitPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showAsLayout: Boolean = false, // New parameter to show as full-screen layout
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val scope = rememberCoroutineScope()
    
    // AI Usage Tracking
    val aiUsageTracker = remember { AiUsageTracker.getInstance() }
    
    // User Manager for subscription status
    val userManager = remember { UserManager.getInstance() }
    val userData by userManager.userData.collectAsState()
    val canUseRewardedAd = userManager.canUseRewardedAd()
    
    // Enhanced subscription observation for immediate updates
    val subscriptionManager = remember { userManager.getSubscriptionManager() }
    var isProFromSubscriptionManager by remember { mutableStateOf(false) }
    
    // Observe subscription manager's isPro StateFlow if available
    LaunchedEffect(subscriptionManager) {
        subscriptionManager?.isPro?.collect { isPro ->
            isProFromSubscriptionManager = isPro
        }
    }
    
    // Check if user is pro from multiple sources for immediate updates
    var isProUser by remember { mutableStateOf(false) }
    
    // Update pro status from all available sources
    LaunchedEffect(userData, isProFromSubscriptionManager) {
        isProUser = userData?.subscriptionStatus == "pro" || 
                   isProFromSubscriptionManager
    }
    
    // IMMEDIATE cache-free check on panel open - no continuous battery-draining loops
    LaunchedEffect(Unit) {
        try {
            // Complete cache-free refresh when panel opens
            aiUsageTracker.forceCompleteRefresh()
            val currentStats = aiUsageTracker.getUsageStats()
            
            // Auto-dismiss immediately if user actually has available actions (shouldn't show limit panel)
            if (currentStats.remainingActions() > 0 && !isProUser) {
                Log.d("AiLimitPanel", "Cache-free check: User has available actions (${currentStats.remainingActions()}), dismissing limit panel immediately")
                onDismiss()
                return@LaunchedEffect
            }
            
            // Also dismiss immediately if user is pro
            if (isProUser) {
                Log.d("AiLimitPanel", "Cache-free check: User is pro, dismissing limit panel immediately")
                onDismiss()
                return@LaunchedEffect
            }
            
            Log.d("AiLimitPanel", "Cache-free check: Limit panel properly shown - user has 0 remaining actions and is not pro")
        } catch (e: Exception) {
            Log.e("AiLimitPanel", "Error in cache-free check on panel open", e)
        }
    }
    
    if (showAsLayout) {
        // Layout version with header and back button, same height as other layouts
        SnyggColumn(
            elementName = FlorisImeUi.Media.elementName,
            modifier = modifier
        ) {
            // Header with back button
            SnyggRow(
                elementName = FlorisImeUi.MediaBottomRow.elementName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FlorisImeSizing.keyboardRowBaseHeight * 0.8f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KeyboardLikeButton(
                    elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                    inputEventDispatcher = keyboardManager.inputEventDispatcher,
                    keyData = TextKeyData.IME_UI_MODE_TEXT,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back to keyboard"
                    )
                }
                
                SnyggText(
                    elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                    text = "AI Usage Limit",
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )
                
                Spacer(modifier = Modifier.weight(1f))
            }
            
            // Content area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
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
                    text = if (isProUser) "Pro Account" else "AI Limit Reached"
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SnyggText(
                elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                text = if (isProUser) {
                    "You have unlimited access to AI features with your Pro subscription!"
                } else {
                    "You've used all ${AiUsageStats.DAILY_LIMIT} free AI actions for today."
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (!isProUser) {
                SnyggText(
                    elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                    text = if (canUseRewardedAd) {
                        val durationText = if (AiUsageStats.REWARD_WINDOW_DURATION_MS == 60 * 1000L) {
                            "1 minute" // Testing mode
                        } else {
                            "24 hours" // Production mode
                        }
                        "Watch a short ad to unlock $durationText of unlimited AI actions!"
                    } else {
                        "Oops,You've used monthly Unlimited AD Reward ,Go Pro for unlimited access at just the price of a pani puri..💚😎 "
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
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
                        .height(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        SnyggText(
                            elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                            text = "Later"
                        )
                    }
                }
                
                if (!isProUser) {
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    SnyggButton(
                        elementName = FlorisImeUi.SmartbarActionTile.elementName,
                        onClick = {
                            if (canUseRewardedAd) {
                                // Start the RewardedAdActivity to handle ad loading and display
                                try {
                                    val intent = Intent(context, RewardedAdActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                    onDismiss()
                                } catch (e: Exception) {
                                    scope.launch {
                                        context.showShortToast("Error starting ad activity: ${e.message}")
                                    }
                                }
                            } else {
                                // User has used their monthly ad - navigate to Go Pro
                                try {
                                    val intent = Intent(context, com.vishruth.key1.app.FlorisAppActivity::class.java).apply {
                                        data = Uri.parse("ui://florisboard/subscription")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    }
                                    context.startActivity(intent)
                                    onDismiss()
                                } catch (e: Exception) {
                                    scope.launch {
                                        context.showShortToast("Error opening subscription screen: ${e.message}")
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (canUseRewardedAd) {
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
                            } else {
                                SnyggText(
                                    elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                                    text = "Go Pro"
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    } else {
        // Original panel version
        SnyggBox(
            elementName = FlorisImeUi.SmartbarActionsOverflow.elementName,
            modifier = modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.keyboardUiHeight())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
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
                        text = if (isProUser) "Pro Account" else "AI Limit Reached"
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                SnyggText(
                    elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                    text = if (isProUser) {
                        "You have unlimited access to AI features with your Pro subscription!"
                    } else {
                        "You've used all ${AiUsageStats.DAILY_LIMIT} free AI actions for today."
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (!isProUser) {
                    SnyggText(
                        elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                        text = if (canUseRewardedAd) {
                            val durationText = if (AiUsageStats.REWARD_WINDOW_DURATION_MS == 60 * 1000L) {
                                "1 minute" // Testing mode
                            } else {
                                "24 hours" // Production mode
                            }
                            "Watch a short ad to unlock $durationText of unlimited AI actions!"
                        } else {
                            "Oops,You've used monthly Unlimited AD Reward ,Go Pro for unlimited access at just the price of a pani puri..💚😎"
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
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
                            .height(50.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            SnyggText(
                                elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                                text = "Later"
                            )
                        }
                    }
                    
                    if (!isProUser) {
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        SnyggButton(
                            elementName = FlorisImeUi.SmartbarActionTile.elementName,
                            onClick = {
                                if (canUseRewardedAd) {
                                    // Start the RewardedAdActivity to handle ad loading and display
                                    try {
                                        val intent = Intent(context, RewardedAdActivity::class.java)
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                        onDismiss()
                                    } catch (e: Exception) {
                                        scope.launch {
                                            context.showShortToast("Error starting ad activity: ${e.message}")
                                        }
                                    }
                                } else {
                                    // User has used their monthly ad - navigate to Go Pro
                                    try {
                                        val intent = Intent(context, com.vishruth.key1.app.FlorisAppActivity::class.java).apply {
                                            data = Uri.parse("ui://florisboard/subscription")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        }
                                        context.startActivity(intent)
                                        onDismiss()
                                    } catch (e: Exception) {
                                        scope.launch {
                                            context.showShortToast("Error opening subscription screen: ${e.message}")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (canUseRewardedAd) {
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
                                } else {
                                    SnyggText(
                                        elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                                        text = "Go Pro"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}