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

package com.vishruth.key1.ime.translate

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.util.Log
import kotlinx.coroutines.launch

import com.vishruth.key1.editorInstance
import com.vishruth.key1.ime.ai.AiUsageTracker
import com.vishruth.key1.ime.ai.AiUsageStats
import com.vishruth.key1.ime.input.InputEventDispatcher
import com.vishruth.key1.ime.keyboard.FlorisImeSizing
import com.vishruth.key1.ime.media.KeyboardLikeButton
import com.vishruth.key1.ime.smartbar.ActionResultPanelManager
import com.vishruth.key1.ime.smartbar.GeminiApiService
import com.vishruth.key1.ime.smartbar.MagicWandInstructions
import com.vishruth.key1.ime.text.keyboard.TextKeyData
import com.vishruth.key1.ime.theme.FlorisImeUi
import com.vishruth.key1.keyboardManager
import com.vishruth.key1.lib.ads.RewardedAdManager
import com.vishruth.key1.lib.devtools.flogDebug
import com.vishruth.key1.user.UserManager
import com.vishruth.sendright.lib.network.NetworkUtils
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

data class TranslationButton(
    val title: String
)

@Composable
fun TranslateInputLayout(
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
    
    // Preload banner ad immediately when panel opens (independent of scroll)
    LaunchedEffect(Unit) {
        if (!isProUser) {
            try {
                Log.d("TranslateInputLayout", "🚀 Starting banner ad preload for Translate layout")
                
                // Ensure AdMob SDK is initialized
                val adManager = com.vishruth.key1.lib.ads.AdManager
                if (!adManager.isInitialized()) {
                    adManager.ensureInitialized(context)
                    adManager.waitForInitialization(10000)
                }
                
                // Use the same ad unit ID as AdBannerCard
                val adUnitId = "ca-app-pub-1496070957048863/5853942656"
                
                // Preload the native ad
                val adLoader = com.google.android.gms.ads.AdLoader.Builder(context, adUnitId)
                    .forNativeAd { loadedAd ->
                        Log.d("TranslateInputLayout", "✅ Banner ad preloaded successfully")
                    }
                    .withAdListener(object : com.google.android.gms.ads.AdListener() {
                        override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                            Log.e("TranslateInputLayout", "❌ Banner ad preload failed: ${error.message}")
                        }
                    })
                    .withNativeAdOptions(
                        com.google.android.gms.ads.nativead.NativeAdOptions.Builder()
                            .setAdChoicesPlacement(com.google.android.gms.ads.nativead.NativeAdOptions.ADCHOICES_TOP_RIGHT)
                            .setRequestMultipleImages(false)
                            .setReturnUrlsForImageAssets(false)
                            .build()
                    )
                    .build()
                
                val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()
                adLoader.loadAd(adRequest)
                
            } catch (e: Exception) {
                Log.e("TranslateInputLayout", "💥 Exception during banner ad preload", e)
            }
        }
    }
    
    // Rewarded Ad Manager
    val rewardedAdManager = remember { RewardedAdManager(context) }
    var showLimitDialog by remember { mutableStateOf(false) }
    
    // Loading state for translation actions
    var loadingButton by remember { mutableStateOf<String?>(null) }
    
    // Force refresh UI when subscription status changes
    LaunchedEffect(isProUser) {
        if (isProUser && !userManager.hasProFeaturesToastBeenShown()) {
            // Only show toast once when user becomes pro and hasn't seen it before
            context.showShortToast("🎉 Pro features unlocked!")
            userManager.markProFeaturesToastAsShown()
        }
    }
    
    val translationButtons = listOf("Telugu", "Hindi", "Tamil", "English", "Multi")
    
    // Full screen layout like MediaInputLayout
    SnyggColumn(
        elementName = FlorisImeUi.Media.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight()),
    ) {
        // Header with back button
        SnyggRow(
            elementName = FlorisImeUi.MediaBottomRow.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.keyboardRowBaseHeight * 0.8f),
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
            Spacer(modifier = Modifier.weight(1f))
            // Optional: Add other header buttons here if needed
        }
        
        // Main content area
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Translation buttons section
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Section Header
                    SnyggText(
                        elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                        text = "Translation",
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                    )
                    
                    // Process buttons in rows of 2, but handle odd numbers correctly
                    for (i in translationButtons.indices step 2) {
                        if (i + 1 < translationButtons.size) {
                            // Two buttons in this row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TranslationActionButton(
                                    button = TranslationButton(title = translationButtons[i]),
                                    isLoading = loadingButton == translationButtons[i],
                                    onClick = { 
                                        scope.launch {
                                            handleTranslationAction(
                                                buttonTitle = translationButtons[i],
                                                editorInstance = editorInstance,
                                                context = context,
                                                aiUsageTracker = aiUsageTracker,
                                                isProUser = isProUser,
                                                userManager = userManager,
                                                onLoadingStart = { loadingButton = it },
                                                onLoadingEnd = { loadingButton = null },
                                                onShowLimitDialog = { showLimitDialog = true }
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                TranslationActionButton(
                                    button = TranslationButton(title = translationButtons[i + 1]),
                                    isLoading = loadingButton == translationButtons[i + 1],
                                    onClick = { 
                                        scope.launch {
                                            handleTranslationAction(
                                                buttonTitle = translationButtons[i + 1],
                                                editorInstance = editorInstance,
                                                context = context,
                                                aiUsageTracker = aiUsageTracker,
                                                isProUser = isProUser,
                                                userManager = userManager,
                                                onLoadingStart = { loadingButton = it },
                                                onLoadingEnd = { loadingButton = null },
                                                onShowLimitDialog = { showLimitDialog = true }
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            // Single button in this row - take full width without extra spacing
                            TranslationActionButton(
                                button = TranslationButton(title = translationButtons[i]),
                                isLoading = loadingButton == translationButtons[i],
                                onClick = { 
                                    scope.launch {
                                        handleTranslationAction(
                                            buttonTitle = translationButtons[i],
                                            editorInstance = editorInstance,
                                            context = context,
                                            aiUsageTracker = aiUsageTracker,
                                            isProUser = isProUser,
                                            userManager = userManager,
                                            onLoadingStart = { loadingButton = it },
                                            onLoadingEnd = { loadingButton = null },
                                            onShowLimitDialog = { showLimitDialog = true }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
            
            // AI Usage card
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
                                        val canUseRewardedAd = userManager.canUseRewardedAd()
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
            
            // Ad banner at the bottom for free users
            item {
                com.vishruth.key1.ui.components.AdBannerCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    onAdLoaded = {
                        flogDebug { "Translate Layout: Banner ad loaded successfully" }
                    },
                    onAdFailedToLoad = { error ->
                        flogDebug { "Translate Layout: Banner ad failed to load - ${error.message}" }
                    }
                )
            }
        }
    }
}

@Composable
private fun TranslationActionButton(
    button: TranslationButton,
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
                    color = Color(0xFF23C546) // Green loading color
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

suspend fun handleTranslationAction(
    buttonTitle: String,
    editorInstance: com.vishruth.key1.ime.editor.EditorInstance,
    context: android.content.Context,
    aiUsageTracker: com.vishruth.key1.ime.ai.AiUsageTracker,
    isProUser: Boolean,
    userManager: UserManager,
    onLoadingStart: (String) -> Unit,
    onLoadingEnd: () -> Unit,
    onShowLimitDialog: () -> Unit
) {
    try {
        // Check if user is pro
        if (isProUser) {
            // Pro users get unlimited access
            onLoadingStart(buttonTitle)
            try {
                handleTranslationButtonClick(
                    buttonTitle = buttonTitle,
                    editorInstance = editorInstance,
                    context = context,
                    aiUsageTracker = aiUsageTracker
                )
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Something went wrong"
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
                        context.showShortToast("Error: $errorMessage")
                    }
                }
            } finally {
                onLoadingEnd()
            }
        } else {
            // Check AI usage for free users
            val isAllowed = aiUsageTracker.canUseAiAction()
            
            if (isAllowed) {
                onLoadingStart(buttonTitle)
                try {
                    handleTranslationButtonClick(
                        buttonTitle = buttonTitle,
                        editorInstance = editorInstance,
                        context = context,
                        aiUsageTracker = aiUsageTracker
                    )
                } catch (e: Exception) {
                    val errorMessage = e.message ?: "Something went wrong"
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
                            context.showShortToast("Error: $errorMessage")
                        }
                    }
                } finally {
                    onLoadingEnd()
                }
            } else {
                // Force refresh the UI by getting the latest stats
                val updatedStats = aiUsageTracker.getUsageStats()
                val canUseAd = userManager.canUseRewardedAd()
                
                if (updatedStats.remainingActions() == 0) {
                    if (canUseAd) {
                        context.showShortToast("Daily limit reached! Watch an ad to unlock 60 minutes of unlimited AI.")
                        onShowLimitDialog()
                    } else {
                        context.showShortToast("You've reached your daily limit and used your free ad. Upgrade to Pro or wait until tomorrow!")
                    }
                }
            }
        }
    } catch (e: Exception) {
        context.showShortToast("Something went wrong. Please try again.")
        flogDebug { "Error in handleTranslationAction: ${e.message}" }
        onLoadingEnd()
    }
}

suspend fun handleTranslationButtonClick(
    buttonTitle: String,
    editorInstance: com.vishruth.key1.ime.editor.EditorInstance,
    context: android.content.Context,
    aiUsageTracker: com.vishruth.key1.ime.ai.AiUsageTracker
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
        
        // Get instruction for the translation button
        val instruction = MagicWandInstructions.getInstructionForButton(buttonTitle)
        
        // Call Gemini API
        val result = GeminiApiService.transformText(allText, instruction, context)
        
        result.onSuccess { transformedText ->
            // Validate response before showing action result panel
            if (transformedText.isBlank()) {
                context.showShortToast("🤔 Empty response received. Please try again.")
                return@onSuccess
            }
            
            // Record successful AI action only after we have a valid response
            aiUsageTracker.recordSuccessfulAiAction()
            
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
        flogDebug { "Error in handleTranslationButtonClick: ${e.message}" }
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