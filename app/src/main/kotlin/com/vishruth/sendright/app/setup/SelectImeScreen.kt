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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.vishruth.key1.app.LocalNavController
import com.vishruth.key1.app.Routes
import com.vishruth.key1.lib.util.InputMethodUtils
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.stringRes

@Composable
fun SelectImeScreen() {
    val context = LocalContext.current
    val navController = LocalNavController.current
    
    val isFlorisBoardEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
    val isFlorisBoardSelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)

    LaunchedEffect(isFlorisBoardSelected) {
        if (isFlorisBoardSelected) {
            if (AndroidVersion.ATLEAST_API33_T) {
                navController.navigate(Routes.Setup.NotificationPermission) {
                    popUpTo(Routes.Setup.SelectIme) { inclusive = true }
                }
            } else {
                navController.navigate(Routes.Setup.StartCustomization) {
                    popUpTo(Routes.Setup.SelectIme) { inclusive = true }
                }
            }
        }
    }

    LaunchedEffect(isFlorisBoardEnabled) {
        if (!isFlorisBoardEnabled) {
            navController.navigate(Routes.Setup.EnableIme) {
                popUpTo(Routes.Setup.SelectIme) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background image
        Image(
            painter = painterResource(R.drawable.setupbgimage),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Bottom frame
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(Color.White.copy(alpha = 0.95f))
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Step 2",
                fontSize = 18.sp,
                color = Color(0xFF6B7280),
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Choose SendRight as your active keyboard",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFF6B7280),
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Button(
                onClick = {
                    InputMethodUtils.showImePicker(context)
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
                    text = stringRes(R.string.setup__select_ime__switch_keyboard_btn),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}