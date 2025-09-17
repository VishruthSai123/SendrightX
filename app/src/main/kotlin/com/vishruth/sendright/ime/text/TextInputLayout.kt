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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import com.vishruth.key1.R
import com.vishruth.key1.app.FlorisPreferenceStore
import com.vishruth.key1.ime.smartbar.AiLimitPanel
import com.vishruth.key1.ime.ai.AiUsageTracker
import com.vishruth.key1.ime.smartbar.IncognitoDisplayMode
import com.vishruth.key1.ime.smartbar.InlineSuggestionsStyleCache
import com.vishruth.key1.ime.smartbar.MagicWandPanel
import com.vishruth.key1.ime.smartbar.Smartbar
import com.vishruth.key1.ime.smartbar.quickaction.QuickActionsOverflowPanel
import com.vishruth.key1.ime.text.keyboard.TextKeyboardLayout
import com.vishruth.key1.ime.theme.FlorisImeUi
import com.vishruth.key1.keyboardManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
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
            } else if (state.isMagicWandPanelVisible) {
                // Check if AI limit is reached
                val aiUsageTracker = remember { AiUsageTracker.getInstance() }
                val usageStats by aiUsageTracker.usageStats.collectAsState()
                // Check if limit is reached without blocking
                val isLimitReached = usageStats.remainingActions() == 0 && !usageStats.isRewardWindowActive
                
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