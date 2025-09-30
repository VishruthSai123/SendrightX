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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.vishruth.key1.app.LocalNavController
import com.vishruth.key1.app.Routes

/**
 * SetupScreen that redirects to the new four-screen setup flow
 */
@Composable
fun SetupScreen() {
    val navController = LocalNavController.current
    
    LaunchedEffect(Unit) {
        navController.navigate(Routes.Setup.EnableIme) {
            popUpTo(Routes.Setup.Screen) { inclusive = true }
        }
    }
}