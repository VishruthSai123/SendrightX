/*
 * SendRight - AI-Enhanced Android Keyboard
 * Built upon FlorisBoard by The FlorisBoard Contributors
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

package com.vishruth.key1.ime.ailimit

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vishruth.key1.ime.ImeUiMode
import com.vishruth.key1.ime.keyboard.FlorisImeSizing
import com.vishruth.key1.ime.smartbar.AiLimitPanel
import com.vishruth.key1.keyboardManager

@Composable
fun AiLimitInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    
    // Show AiLimitPanel as layout with same height as translation layout
    AiLimitPanel(
        onDismiss = {
            // Go back to previous mode (typically TEXT mode)
            keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
        },
        showAsLayout = true,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight())
    )
}