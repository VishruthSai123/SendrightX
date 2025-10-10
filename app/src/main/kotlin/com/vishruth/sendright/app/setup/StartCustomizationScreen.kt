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

package com.vishruth.key1.app.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vishruth.key1.R
import com.vishruth.key1.app.FlorisPreferenceStore
import com.vishruth.key1.app.LocalNavController
import com.vishruth.key1.app.Routes
import com.vishruth.key1.app.components.VideoBackground
import com.vishruth.key1.lib.util.InputMethodUtils
import com.vishruth.key1.lib.util.launchUrl
import com.vishruth.key1.app.setup.NotificationPermissionState
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.stringRes

@Composable
fun StartCustomizationScreen() {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    var videoProgress by remember { mutableFloatStateOf(0f) }
    var showButton by remember { mutableStateOf(false) }
    var timerFinished by remember { mutableStateOf(false) }
    
    val isFlorisBoardEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
    val isFlorisBoardSelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)
    val hasNotificationPermission by prefs.internal.notificationPermissionState.observeAsState()

    // 1.5-second timer
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500) // 1.5 seconds
        timerFinished = true
        showButton = true
    }

    LaunchedEffect(isFlorisBoardEnabled, isFlorisBoardSelected, hasNotificationPermission) {
        when {
            !isFlorisBoardEnabled -> {
                navController.navigate(Routes.Setup.EnableIme) {
                    popUpTo(Routes.Setup.StartCustomization) { inclusive = true }
                }
            }
            !isFlorisBoardSelected -> {
                navController.navigate(Routes.Setup.SelectIme) {
                    popUpTo(Routes.Setup.StartCustomization) { inclusive = true }
                }
            }
            AndroidVersion.ATLEAST_API33_T && hasNotificationPermission == NotificationPermissionState.NOT_SET -> {
                navController.navigate(Routes.Setup.NotificationPermission) {
                    popUpTo(Routes.Setup.StartCustomization) { inclusive = true }
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background image (fallback)
        Image(
            painter = painterResource(R.drawable.setupbgimage2),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Video background overlay
        VideoBackground(
            rawVideoRes = R.raw.allset,
            shouldLoop = false,
            modifier = Modifier.fillMaxSize(),
            onProgressUpdate = { progress ->
                videoProgress = progress
            }
        )

        // Bottom frame with animated expansion
        val bottomPadding by animateDpAsState(
            targetValue = if (showButton) 24.dp else 24.dp,
            animationSpec = tween(600),
            label = "bottomPadding"
        )
        
        // Animated complete rectangular box - bottom to top
        AnimatedVisibility(
            visible = timerFinished,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(600)
            ) + fadeIn(animationSpec = tween(300, delayMillis = 200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(400)
            ) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color.White.copy(alpha = 0.95f))
                    .padding(bottomPadding)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "All Set!",
                    fontSize = 24.sp,
                    color = Color(0xFF1F2937),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Button(
                    onClick = {
                        scope.launch { 
                            prefs.internal.isImeSetUp.set(true) 
                        }
                        navController.navigate(Routes.Settings.Home) {
                            popUpTo(Routes.Setup.StartCustomization) {
                                inclusive = true
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4AA60D)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Start Typing",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
    }
}