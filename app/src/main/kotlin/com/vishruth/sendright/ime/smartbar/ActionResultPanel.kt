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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface

import com.vishruth.key1.BuildConfig
import com.vishruth.key1.editorInstance
import com.vishruth.key1.ime.keyboard.FlorisImeSizing
import com.vishruth.key1.ime.theme.FlorisImeTheme
import com.vishruth.key1.ime.theme.FlorisImeUi
import com.vishruth.key1.keyboardManager
import com.vishruth.key1.lib.devtools.flogDebug
import com.vishruth.key1.R
import com.vishruth.sendright.lib.network.NetworkUtils
import com.vishruth.sendright.ime.review.InAppReviewManager
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.snygg.ui.SnyggBox
import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.vishruth.key1.user.UserManager
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * Data class representing the state of an action result
 */
data class ActionResultState(
    val originalText: String = "",
    val transformedText: String = "",
    val actionTitle: String = "",
    val instruction: String = "",
    val isLoading: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoStack: List<String> = emptyList(),
    val redoStack: List<String> = emptyList()
)

/**
 * Action Result Panel that displays AI-generated responses with action buttons
 */
@Composable
fun ActionResultPanel(
    originalText: String,
    transformedText: String,
    actionTitle: String,
    instruction: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onRegenerate: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFlag: () -> Unit,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val scope = rememberCoroutineScope()
    
    // User manager to check subscription status
    val userManager = remember { UserManager.getInstance() }
    val userData by userManager.userData.collectAsState()
    val subscriptionManager = userManager.getSubscriptionManager()
    val isPro by subscriptionManager?.isPro?.collectAsState() ?: remember { mutableStateOf(false) }
    // Determine pro status from multiple sources for immediate updates
    val isProUser = isPro || userData?.subscriptionStatus == "pro"
    
    // Preload banner ad immediately when panel opens (independent of scroll)
    LaunchedEffect(Unit) {
        if (!isProUser) {
            try {
                Log.d("ActionResultPanel", "🚀 Starting banner ad preload for ActionResult panel")
                
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
                        Log.d("ActionResultPanel", "✅ Banner ad preloaded successfully")
                    }
                    .withAdListener(object : com.google.android.gms.ads.AdListener() {
                        override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                            Log.e("ActionResultPanel", "❌ Banner ad preload failed: ${error.message}")
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
                Log.e("ActionResultPanel", "💥 Exception during banner ad preload", e)
            }
        }
    }
    

    


    // Preload ads when panel opens
    com.vishruth.key1.ui.components.AdPreloader(
        panelName = "ActionResult"
    )
    
    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionsOverflow.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight())
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Title - Dynamic based on action
            item {
                val displayTitle = when (actionTitle.lowercase()) {
                    "chat" -> "AI Response"
                    "rewrite" -> "Rewritten Text"
                    "summarise" -> "Summary"
                    "translate", "telugu", "hindi", "tamil", "english", "multi" -> "Translation"
                    "explain" -> "Explanation"
                    "solution" -> "Solution"
                    "formal" -> "Formal Text"
                    "casual" -> "Casual Text"
                    "friendly" -> "Friendly Text"
                    "professional" -> "Professional Text"
                    else -> "Transformed Text"
                }
                SnyggText(
                    elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                    text = displayTitle,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Response Text Box
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFCFE9D5)) // Light green background
                        .padding(12.dp)
                ) {
                    if (isLoading) {
                        // Loading state - only show spinner
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF23C546) // Green loading color
                            )
                        }
                    } else {
                        // Response text
                        Text(
                            text = transformedText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Black,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Action Buttons (Horizontal Row)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Accept Button
                    ActionButton(
                        drawableRes = R.drawable.accept,
                        contentDescription = "Accept",
                        enabled = !isLoading,
                        onClick = onAccept,
                        modifier = Modifier.weight(1f)
                    )

                    // Reject Button
                    ActionButton(
                        drawableRes = R.drawable.reject,
                        contentDescription = "Reject",
                        enabled = !isLoading,
                        onClick = onReject,
                        modifier = Modifier.weight(1f)
                    )

                    // Regenerate Button
                    ActionButton(
                        drawableRes = R.drawable.regenerate,
                        contentDescription = "Regenerate",
                        enabled = !isLoading,
                        onClick = onRegenerate,
                        modifier = Modifier.weight(1f)
                    )

                    // Undo Button
                    ActionButton(
                        drawableRes = R.drawable.undo,
                        contentDescription = "Undo",
                        enabled = canUndo && !isLoading,
                        onClick = onUndo,
                        modifier = Modifier.weight(1f)
                    )

                    // Redo Button
                    ActionButton(
                        drawableRes = R.drawable.redo,
                        contentDescription = "Redo",
                        enabled = canRedo && !isLoading,
                        onClick = onRedo,
                        modifier = Modifier.weight(1f)
                    )

                    // Flag Button
                    ActionButton(
                        drawableRes = R.drawable.reportscreen,
                        contentDescription = "Flag/Report",
                        enabled = !isLoading,
                        onClick = onFlag,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Ad banner at the bottom for free users - enhanced native ad UI with proper width
            item {
                // Enhanced AdBannerCard with same width as action buttons and fade animation
                com.vishruth.key1.ui.components.AdBannerCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp), // Match action buttons padding
                    onAdLoaded = {
                        flogDebug { "Action Result Panel: Banner ad loaded successfully" }
                    },
                    onAdFailedToLoad = { error ->
                        flogDebug { "Action Result Panel: Banner ad failed to load - ${error.message}" }
                    }
                )
            }
        }
    }
}

/**
 * Individual action button component
 */
@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    drawableRes: Int? = null,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .size(48.dp) // Fixed size for consistent spacing
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .alpha(if (enabled) 1.0f else 0.5f), // Handle disabled state with overall alpha
        contentAlignment = Alignment.Center
    ) {
        when {
            drawableRes != null -> {
                androidx.compose.material3.Icon(
                    painter = androidx.compose.ui.res.painterResource(drawableRes),
                    contentDescription = contentDescription,
                    modifier = Modifier.size(32.dp), // Slightly larger icon
                    tint = androidx.compose.ui.graphics.Color.Unspecified // Don't tint PNG images
                )
            }
            icon != null -> {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(32.dp),
                    tint = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * Action Result Panel Manager - handles the complete workflow
 */
class ActionResultPanelManager(
    private val editorInstance: com.vishruth.key1.ime.editor.EditorInstance,
    private val context: android.content.Context
) {
    private var _state by mutableStateOf(ActionResultState())
    val state: ActionResultState get() = _state
    
    // StateFlow for the new ActionResultInputLayout
    private val _currentState = kotlinx.coroutines.flow.MutableStateFlow(ActionResultState())
    val currentState: kotlinx.coroutines.flow.StateFlow<ActionResultState> = _currentState

    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()
    
    // Flag to prevent auto-close during undo/redo operations
    private var _isPerformingUndoRedo by mutableStateOf(false)
    val isPerformingUndoRedo: Boolean get() = _isPerformingUndoRedo
    
    // Helper method to update both state properties consistently
    private fun updateState(newState: ActionResultState) {
        _state = newState
        _currentState.value = newState
    }

    companion object {
        private var instance: ActionResultPanelManager? = null
        
        fun getInstance(
            editorInstance: com.vishruth.key1.ime.editor.EditorInstance,
            context: android.content.Context
        ): ActionResultPanelManager {
            if (instance == null) {
                instance = ActionResultPanelManager(editorInstance, context)
            }
            return instance!!
        }
        
        fun getCurrentInstance(): ActionResultPanelManager? = instance
    }

    /**
     * Show the action result panel with initial data
     */
    fun showActionResult(
        originalText: String,
        transformedText: String,
        actionTitle: String,
        instruction: String
    ) {
        updateState(_state.copy(
            originalText = originalText,
            transformedText = transformedText,
            actionTitle = actionTitle,
            instruction = instruction,
            isLoading = false
        ))
        updateUndoRedoState()
    }

    /**
     * Accept the transformed text and replace the original text
     */
    suspend fun acceptText() {
        try {
            // Save current state to undo stack
            saveToUndoStack(_state.originalText)
            
            // Get CURRENT text length (in case user typed more after action was triggered)
            val activeContent = editorInstance.activeContent
            val currentTotalTextLength = activeContent.textBeforeSelection.length + 
                                        activeContent.selectedText.length + 
                                        activeContent.textAfterSelection.length
            
            // Select ALL current text (not just the original captured text)
            editorInstance.setSelection(0, currentTotalTextLength)
            
            // Delete all selected text
            editorInstance.deleteSelectedText()
            
            // Insert the transformed text
            editorInstance.commitText(_state.transformedText)
            
            context.showShortToast("Applied")
            
            // Update state
            updateState(_state.copy(originalText = _state.transformedText))
            updateUndoRedoState()
            
            // Record successful action for review tracking
            // This works for both free and pro users
            try {
                val reviewManager = InAppReviewManager.getInstance(context)
                reviewManager.recordSuccessfulAction()
                flogDebug { "Recorded successful action for in-app review" }
                
                // Log current stats for debugging
                if (BuildConfig.DEBUG) {
                    val stats = reviewManager.getReviewStats()
                    flogDebug { "InAppReview Stats: daily=${stats.dailyActions}, total=${stats.totalActions}, requested=${stats.reviewRequested}" }
                }
            } catch (e: Exception) {
                flogDebug { "Error recording action for review: ${e.message}" }
                // Don't fail the main action if review tracking fails
            }
            
        } catch (e: Exception) {
            context.showShortToast("Error applying text: ${e.message}")
            flogDebug { "Error in acceptText: ${e.message}" }
        }
    }

    /**
     * Reject the changes and go back to magic wand panel
     */
    suspend fun rejectText() {
        context.showShortToast("Rejected")
        // This will be handled by the parent component to navigate back to magic wand panel
    }

    /**
     * Regenerate the response with the same instruction (bypasses cache for fresh results)
     */
    suspend fun regenerateText() {
        if (_state.isLoading) return
        
        updateState(_state.copy(isLoading = true))
        
        try {
            // Check network connectivity
            if (!NetworkUtils.checkNetworkAndShowToast(context)) {
                updateState(_state.copy(isLoading = false))
                return
            }
            
            // Call Gemini API again with cache bypass to ensure fresh response
            val result = GeminiApiService.transformText(_state.originalText, _state.instruction, context, bypassCache = true)
            
            result.onSuccess { newTransformedText ->
                if (newTransformedText.isBlank()) {
                    // Empty response - no toast needed
                    updateState(_state.copy(isLoading = false))
                } else {
                    updateState(_state.copy(
                        transformedText = newTransformedText,
                        isLoading = false
                    ))
                    // Remove toast after response is generated
                }
            }.onFailure { error ->
                context.showShortToast("Regenerate failed")
                updateState(_state.copy(isLoading = false))
            }
            
        } catch (e: Exception) {
            context.showShortToast("Regenerate error")
            updateState(_state.copy(isLoading = false))
        }
    }

    /**
     * Undo the last text change
     */
    suspend fun undoText() {
        if (undoStack.isNotEmpty()) {
            _isPerformingUndoRedo = true
            try {
                val previousText = undoStack.removeAt(undoStack.size - 1)
                redoStack.add(_state.originalText)
                
                // Apply the previous text
                try {
                    val activeContent = editorInstance.activeContent
                    val totalTextLength = activeContent.textBeforeSelection.length + 
                                         activeContent.selectedText.length + 
                                         activeContent.textAfterSelection.length
                    
                    editorInstance.setSelection(0, totalTextLength)
                    editorInstance.deleteSelectedText()
                    editorInstance.commitText(previousText)
                    
                    updateState(_state.copy(originalText = previousText))
                    updateUndoRedoState()
                    
                } catch (e: Exception) {
                    // Undo failed - no toast needed
                }
            } finally {
                _isPerformingUndoRedo = false
            }
        }
    }

    /**
     * Redo the last undone change
     */
    suspend fun redoText() {
        if (redoStack.isNotEmpty()) {
            _isPerformingUndoRedo = true
            try {
                val nextText = redoStack.removeAt(redoStack.size - 1)
                undoStack.add(_state.originalText)
                
                // Apply the next text
                try {
                    val activeContent = editorInstance.activeContent
                    val totalTextLength = activeContent.textBeforeSelection.length + 
                                         activeContent.selectedText.length + 
                                         activeContent.textAfterSelection.length
                    
                    editorInstance.setSelection(0, totalTextLength)
                    editorInstance.deleteSelectedText()
                    editorInstance.commitText(nextText)
                    
                    updateState(_state.copy(originalText = nextText))
                    updateUndoRedoState()
                    
                } catch (e: Exception) {
                    // Redo failed - no toast needed
                }
            } finally {
                _isPerformingUndoRedo = false
            }
        }
    }

    /**
     * Flag/Report the response - opens report screen in main app
     */
    suspend fun flagResponse() {
        try {
            // Use ui:// scheme to navigate to report screen
            val reportUri = "ui://report?response_text=${java.net.URLEncoder.encode(_state.transformedText, "UTF-8")}&original_text=${java.net.URLEncoder.encode(_state.originalText, "UTF-8")}&action_title=${java.net.URLEncoder.encode(_state.actionTitle, "UTF-8")}"
            
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(reportUri)).apply {
                setPackage(context.packageName)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            try {
                context.startActivity(intent)
                // No toast needed for report
            } catch (e: Exception) {
                // Fallback to main app launch
                val fallbackIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    putExtra("action", "report")
                    putExtra("response_text", _state.transformedText)
                    putExtra("original_text", _state.originalText)
                    putExtra("action_title", _state.actionTitle)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                if (fallbackIntent != null) {
                    context.startActivity(fallbackIntent)
                } else {
                    // Report opening failed - no toast needed
                }
            }
            
        } catch (e: Exception) {
            // Report error - no toast needed
            flogDebug { "Error in flagResponse: ${e.message}" }
        }
    }

    /**
     * Save current text to undo stack
     */
    private fun saveToUndoStack(text: String) {
        undoStack.add(text)
        // Limit undo stack size to prevent memory issues
        while (undoStack.size > 10) {
            undoStack.removeAt(0)
        }
        // Clear redo stack when new action is performed
        redoStack.clear()
    }

    /**
     * Update undo/redo button states
     */
    private fun updateUndoRedoState() {
        updateState(_state.copy(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            undoStack = undoStack.toList(),
            redoStack = redoStack.toList()
        ))
    }

    /**
     * Reset the manager state
     */
    fun reset() {
        updateState(ActionResultState())
        undoStack.clear()
        redoStack.clear()
    }
    
    // Wrapper methods for ActionResultInputLayout compatibility
    fun acceptResult() {
        kotlinx.coroutines.GlobalScope.launch {
            acceptText()
        }
    }
    
    fun rejectResult() {
        kotlinx.coroutines.GlobalScope.launch {
            rejectText()
        }
    }
    
    fun regenerateResult() {
        kotlinx.coroutines.GlobalScope.launch {
            regenerateText()
        }
    }
    
    fun undoResult() {
        kotlinx.coroutines.GlobalScope.launch {
            undoText()
        }
    }
    
    fun redoResult() {
        kotlinx.coroutines.GlobalScope.launch {
            redoText()
        }
    }
    
    fun flagResult() {
        kotlinx.coroutines.GlobalScope.launch {
            flagResponse()
        }
    }
}