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

package com.vishruth.key1.app.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vishruth.key1.R
import com.vishruth.key1.app.LocalNavController
import com.vishruth.key1.app.Routes
import com.vishruth.key1.app.FlorisPreferenceStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PersonalizationIntroScreen() {
    val navController = LocalNavController.current
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    var currentText by remember { mutableStateOf("") }
    var currentIndex by remember { mutableIntStateOf(0) }
    var showButton by remember { mutableStateOf(false) }
    
    val fullText = "Our latest update introduces powerful new personalization features — now you can configure your own preferences, and every AI action and chat response will instantly adapt to your style.\n\nNo more repeating what you want — just set it once, and let the AI do the rest.\n\nTap \"Configure\" below to experience it now."
    
    // Typewriter effect - faster typing
    LaunchedEffect(Unit) {
        while (currentIndex < fullText.length) {
            delay(15) // Faster typing speed (15ms per character)
            currentIndex++
            currentText = fullText.substring(0, currentIndex)
        }
        // Show button after text completion with a small delay
        delay(500)
        showButton = true
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background image
        Image(
            painter = painterResource(R.drawable.onestepawaybg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp)) // Reduced from 60dp to 8dp
            
            // "You're One Step Away...!" heading in green stroked white box
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.95f))
                    .border(
                        width = 2.dp,
                        color = Color(0xFF4AA60D),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "You're one step away...!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4AA60D),
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp)) // Reduced from 40dp to 16dp
            
            // Main content with typewriter effect
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.92f))
                    .padding(24.dp)
            ) {
                Text(
                    text = currentText,
                    fontSize = 18.sp, // Larger font size
                    lineHeight = 26.sp, // Adjusted line height
                    fontWeight = FontWeight.SemiBold, // Bolder text
                    color = Color(0xFF1F2937),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Buttons with fade in animation
            AnimatedVisibility(
                visible = showButton,
                enter = fadeIn(animationSpec = tween(800))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Configure button
                    Button(
                        onClick = {
                            // Mark personalization intro as completed and navigate to configuration
                            scope.launch {
                                prefs.internal.isPersonalizationIntroCompleted.set(true)
                                navController.navigate(Routes.Onboarding.ContextConfiguration)
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
                            text = "Configure",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Skip button
                    OutlinedButton(
                        onClick = {
                            // Mark personalization intro as completed and navigate to home
                            scope.launch {
                                prefs.internal.isPersonalizationIntroCompleted.set(true)
                                navController.navigate(Routes.Settings.Home) {
                                    popUpTo(Routes.Onboarding.PersonalizationIntro) { inclusive = true }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF4AA60D)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Skip",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4AA60D)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // SendRight Team signature (only show when button is visible)
            AnimatedVisibility(
                visible = showButton,
                enter = fadeIn(animationSpec = tween(800, delayMillis = 300))
            ) {
                Text(
                    text = "-Sendright Team.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6B7280),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}