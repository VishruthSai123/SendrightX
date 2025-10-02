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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

import com.vishruth.key1.BuildConfig
import com.vishruth.key1.editorInstance
import com.vishruth.key1.ime.ImeUiMode
import com.vishruth.key1.ime.keyboard.FlorisImeSizing
import com.vishruth.key1.ime.media.KeyboardLikeButton
import com.vishruth.key1.ime.text.keyboard.TextKeyData
import com.vishruth.key1.ime.theme.FlorisImeTheme
import com.vishruth.key1.ime.theme.FlorisImeUi
import com.vishruth.key1.keyboardManager
import com.vishruth.key1.lib.devtools.flogDebug
import com.vishruth.key1.R
import com.vishruth.key1.user.UserManager
import com.vishruth.sendright.lib.network.NetworkUtils
import com.vishruth.sendright.ime.review.InAppReviewManager
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * Full-screen Action Result Layout that uses the exact same UI as ActionResultPanel
 * but with a header and back arrow
 */
@Composable
fun ActionResultInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val scope = rememberCoroutineScope()
    
    // Get the current action result state from ActionResultPanelManager
    val actionResultManager = ActionResultPanelManager.getCurrentInstance()
    val resultState = actionResultManager?.currentState?.collectAsState()?.value
    
    // If no result state, show empty state
    if (resultState == null) {
        // Full screen layout like other input layouts
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
            }
            
            // Empty state content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                SnyggText(
                    elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                    text = "No action result available",
                )
            }
        }
        return
    }
    
    // Full screen layout like other input layouts
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
        }
        
        // Use the exact same ActionResultPanel UI but without the outer container
        SnyggBox(
            elementName = FlorisImeUi.SmartbarActionsOverflow.elementName,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            ActionResultPanel(
                originalText = resultState.originalText,
                transformedText = resultState.transformedText,
                actionTitle = resultState.actionTitle,
                instruction = resultState.instruction,
                canUndo = resultState.canUndo,
                canRedo = resultState.canRedo,
                isLoading = resultState.isLoading,
                onAccept = {
                    actionResultManager?.acceptResult()
                    keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                },
                onReject = {
                    actionResultManager?.rejectResult()
                    keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                },
                onRegenerate = {
                    actionResultManager?.regenerateResult()
                },
                onUndo = {
                    actionResultManager?.undoResult()
                },
                onRedo = {
                    actionResultManager?.redoResult()
                },
                onFlag = {
                    actionResultManager?.flagResult()
                    scope.launch {
                        context.showShortToast("Content flagged for review")
                    }
                }
            )
        }
    }
}