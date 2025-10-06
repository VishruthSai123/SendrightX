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

package com.vishruth.key1.app.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SmartButton
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vishruth.key1.R
import com.vishruth.key1.app.LocalNavController
import com.vishruth.key1.app.Routes
import com.vishruth.key1.clipboardManager
import com.vishruth.key1.lib.compose.FlorisScreen
import com.vishruth.key1.lib.util.InputMethodUtils
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.Preference
import org.florisboard.lib.android.stringRes
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.stringRes

@Composable
fun HomeScreen() = FlorisScreen {
    title = stringRes(R.string.settings__home__title)
    navigationIconVisible = false
    previewFieldVisible = true

    val navController = LocalNavController.current
    val context = LocalContext.current

    // Add Premium button to the top app bar actions
    actions {
        IconButton(
            onClick = { navController.navigate(Routes.Subscription.Screen) },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.premium),
                contentDescription = "Premium",
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    content {
        val isCollapsed by prefs.internal.homeIsBetaToolboxCollapsed.observeAsState()

        val isFlorisBoardEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
        val isFlorisBoardSelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)
        if (!isFlorisBoardEnabled) {
            FlorisErrorCard(
                modifier = Modifier.padding(8.dp),
                showIcon = false,
                text = stringRes(R.string.settings__home__ime_not_enabled),
                onClick = { InputMethodUtils.showImeEnablerActivity(context) },
            )
        } else if (!isFlorisBoardSelected) {
            FlorisWarningCard(
                modifier = Modifier.padding(8.dp),
                showIcon = false,
                text = stringRes(R.string.settings__home__ime_not_selected),
                onClick = { InputMethodUtils.showImePicker(context) },
            )
        }

        /*Card(modifier = Modifier.padding(8.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Welcome to the 0.4 alpha series!",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.weight(1.0f))
                    IconButton(onClick = { this@content.prefs.internal.homeIsBetaToolboxCollapsed.set(!isCollapsed) }) {
                        Icon(
                            painter = painterResource(if (isCollapsed) {
                                R.drawable.ic_keyboard_arrow_down
                            } else {
                                R.drawable.ic_keyboard_arrow_up
                            }),
                            contentDescription = null,
                        )
                    }
                }
                if (!isCollapsed) {
                    Text("0.4 will be quite a big release and finally work on adding support for word suggestion and inline autocorrect within the keyboard UI, at first for Latin-based languages. Additionally general improvements and bug fixes will also be made.\n")
                    Text("Currently the alpha releases are preparations for the suggestions implementation and general improvements and bug fixes.\n")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Note that this release does not contain support for word suggestions (will show the current word plus numbers as a placeholder).", color = Color.Red)
                    Text("Please DO NOT file an issue for this. It is already more than known and a major goal for implementation in 0.4.0. Thank you!\n")
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }*/
        
        Preference(
            icon = Icons.Outlined.Workspaces,
            title = "AI Workspace",
            onClick = { navController.navigate(Routes.Settings.AIWorkspace) },
        )
        Preference(
            icon = Icons.Filled.Language,
            title = stringRes(R.string.settings__localization__title),
            onClick = { navController.navigate(Routes.Settings.Localization) },
        )
        Preference(
            icon = Icons.Outlined.Palette,
            title = stringRes(R.string.settings__theme__title),
            onClick = { navController.navigate(Routes.Settings.Theme) },
        )
        Preference(
            icon = Icons.Outlined.Keyboard,
            title = stringRes(R.string.settings__keyboard__title),
            onClick = { navController.navigate(Routes.Settings.Keyboard) },
        )
        Preference(
            icon = Icons.Outlined.SmartButton,
            title = stringRes(R.string.settings__smartbar__title),
            onClick = { navController.navigate(Routes.Settings.Smartbar) },
        )
        Preference(
            icon = Icons.Outlined.Spellcheck,
            title = stringRes(R.string.settings__typing__title),
            onClick = { navController.navigate(Routes.Settings.Typing) },
        )
        Preference(
            icon = Icons.Outlined.TouchApp,
            title = stringRes(R.string.settings__gestures__title),
            onClick = { navController.navigate(Routes.Settings.Gestures) },
        )
        Preference(
            icon = Icons.Default.RateReview,
            title = "Report & Feedback",
            onClick = { navController.navigate(Routes.Settings.ReportFeedback) },
        )
        Preference(
            icon = Icons.Outlined.Build,
            title = stringRes(R.string.settings__other__title),
            onClick = { navController.navigate(Routes.Settings.Other) },
        )
        
        // Sendright illustration at the bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.sendrightillustrator),
                contentDescription = "Sendright - Crafted With Love in India",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
        }
    }
}