/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package com.vishruth.key1.ime.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import com.vishruth.key1.R
import com.vishruth.key1.app.FlorisPreferenceStore
import org.florisboard.lib.android.showShortToast
import com.vishruth.key1.ime.smartbar.AiLimitPanel
import com.vishruth.key1.ime.ai.AiUsageTracker
import com.vishruth.key1.ime.smartbar.IncognitoDisplayMode
import com.vishruth.key1.ime.smartbar.InlineSuggestionsStyleCache
import com.vishruth.key1.ime.smartbar.MagicWandPanel
import com.vishruth.key1.ime.smartbar.ActionResultPanel
import com.vishruth.key1.ime.smartbar.ActionResultPanelManager
import com.vishruth.key1.ime.smartbar.Smartbar
import com.vishruth.key1.ime.smartbar.quickaction.QuickActionsOverflowPanel
import com.vishruth.key1.ime.text.keyboard.TextKeyboardLayout
import com.vishruth.key1.ime.theme.FlorisImeUi
import com.vishruth.key1.keyboardManager
import com.vishruth.key1.editorInstance
import com.vishruth.key1.user.UserManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.snygg.ui.SnyggIcon

@Composable
fun TextInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val prefs by FlorisPreferenceStore

    val state by keyboardManager.activeState.collectAsState()
    val evaluator by keyboardManager.activeEvaluator.collectAsState()

    InlineSuggestionsStyleCache()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        ) {
            Smartbar()
            if (state.isActionsOverflowVisible) {
                QuickActionsOverflowPanel()
            } else if (state.isActionResultPanelVisible) {
                ActionResultPanelWrapper()
            } else if (state.isMagicWandPanelVisible) {
                // Enhanced subscription and AI limit checking
                val aiUsageTracker = remember { AiUsageTracker.getInstance() }
                val usageStats by aiUsageTracker.usageStats.collectAsState()
                
                // Check subscription status from multiple sources
                val userManager = remember { UserManager.getInstance() }
                val userData by userManager.userData.collectAsState()
                val subscriptionManager = remember { userManager.getSubscriptionManager() }
                val isProFromSubscriptionManager = remember { mutableStateOf(false) }
                
                // Observe subscription manager's isPro StateFlow if available
                LaunchedEffect(subscriptionManager) {
                    subscriptionManager?.isPro?.collect { isPro ->
                        isProFromSubscriptionManager.value = isPro
                    }
                }
                
                // Determine if user is pro from multiple sources
                val isProUser = userData?.subscriptionStatus == "pro" || 
                               isProFromSubscriptionManager.value
                
                // Check if limit is reached for non-pro users
                val isLimitReached = !isProUser && 
                                   usageStats.remainingActions() == 0 && 
                                   !usageStats.isRewardWindowActive
                
                if (isLimitReached) {
                    AiLimitPanel(
                        onDismiss = {
                            keyboardManager.closeMagicWandPanel()
                        }
                    )
                } else {
                    MagicWandPanel()
                }
            } else {
                Box {
                    val incognitoDisplayMode by prefs.keyboard.incognitoDisplayMode.observeAsState()
                    val showIncognitoIcon = evaluator.state.isIncognitoMode &&
                        incognitoDisplayMode == IncognitoDisplayMode.DISPLAY_BEHIND_KEYBOARD
                    if (showIncognitoIcon) {
                        SnyggIcon(
                            FlorisImeUi.IncognitoModeIndicator.elementName,
                            modifier = Modifier
                                .matchParentSize()
                                .align(Alignment.Center),
                            painter = painterResource(R.drawable.ic_incognito),
                        )
                    }
                    TextKeyboardLayout(evaluator = evaluator)
                }
            }
        }
    }
}

/**
 * Wrapper composable for ActionResultPanel with state management
 */
@Composable
private fun ActionResultPanelWrapper() {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val scope = rememberCoroutineScope()

    // Create or get the action result panel manager
    val actionResultManager = remember {
        ActionResultPanelManager.getInstance(editorInstance, context)
    }

    // Get the current state
    val state = actionResultManager.state

    // Add safety check to ensure we have valid data before showing the panel
    if (state.transformedText.isEmpty() && state.originalText.isEmpty()) {
        // If no data is available, close the panel
        LaunchedEffect(Unit) {
            keyboardManager.dismissActionResultPanel()
        }
        return
    }

    ActionResultPanel(
        originalText = state.originalText,
        transformedText = state.transformedText,
        actionTitle = state.actionTitle,
        instruction = state.instruction,
        canUndo = state.canUndo,
        canRedo = state.canRedo,
        isLoading = state.isLoading,
        onAccept = {
            scope.launch {
                try {
                    actionResultManager.acceptText()
                    keyboardManager.dismissActionResultPanel()
                } catch (e: Exception) {
                    context.showShortToast("Error accepting text: ${e.message}")
                    keyboardManager.dismissActionResultPanel()
                }
            }
        },
        onReject = {
            scope.launch {
                try {
                    actionResultManager.rejectText()
                    keyboardManager.closeActionResultPanel()
                } catch (e: Exception) {
                    context.showShortToast("Error rejecting text: ${e.message}")
                    keyboardManager.closeActionResultPanel()
                }
            }
        },
        onRegenerate = {
            scope.launch {
                try {
                    actionResultManager.regenerateText()
                } catch (e: Exception) {
                    context.showShortToast("Error regenerating text: ${e.message}")
                }
            }
        },
        onUndo = {
            scope.launch {
                try {
                    actionResultManager.undoText()
                } catch (e: Exception) {
                    context.showShortToast("Error undoing: ${e.message}")
                }
            }
        },
        onRedo = {
            scope.launch {
                try {
                    actionResultManager.redoText()
                } catch (e: Exception) {
                    context.showShortToast("Error redoing: ${e.message}")
                }
            }
        },
        onFlag = {
            scope.launch {
                try {
                    actionResultManager.flagResponse()
                } catch (e: Exception) {
                    context.showShortToast("Error flagging: ${e.message}")
                }
            }
        }
    )
}