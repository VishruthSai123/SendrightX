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

package com.vishruth.key1.ime.smartbar

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vishruth.key1.app.RewardedAdActivity
import com.vishruth.key1.editorInstance
import com.vishruth.key1.ime.ai.AiUsageStats
import com.vishruth.key1.ime.ai.AiUsageTracker
import com.vishruth.key1.ime.keyboard.FlorisImeSizing
import com.vishruth.key1.ime.theme.FlorisImeTheme
import com.vishruth.key1.ime.theme.FlorisImeUi
import com.vishruth.key1.keyboardManager
import com.vishruth.key1.lib.ads.RewardedAdManager
import com.vishruth.key1.lib.util.launchActivity
import com.vishruth.key1.user.UserManager
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggText

// Extension function to find the activity context
fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AiLimitPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val scope = rememberCoroutineScope()
    
    // AI Usage Tracking
    val aiUsageTracker = remember { AiUsageTracker.getInstance() }
    
    // User Manager for subscription status
    val userManager = remember { UserManager.getInstance() }
    val userData by userManager.userData.collectAsState()
    val canUseRewardedAd = userManager.canUseRewardedAd()
    
    // Rewarded Ad Manager
    val rewardedAdManager = remember { RewardedAdManager(context) }
    
    // Check if user is pro
    val isProUser = userData?.subscriptionStatus == "pro"
    
    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionsOverflow.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                SnyggText(
                    elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                    text = if (isProUser) "Pro Account" else "AI Limit Reached"
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SnyggText(
                elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                text = if (isProUser) {
                    "You have unlimited access to AI features with your Pro subscription!"
                } else {
                    "You've used all ${AiUsageStats.DAILY_LIMIT} free AI actions for today."
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (!isProUser) {
                SnyggText(
                    elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                    text = if (canUseRewardedAd) {
                        "Watch a short ad to unlock 60 minutes of unlimited AI actions!"
                    } else {
                        "You've used your monthly ad reward. Upgrade to Pro for unlimited access!"
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SnyggButton(
                    elementName = FlorisImeUi.SmartbarActionTile.elementName,
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    SnyggText(
                        elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                        text = "Later"
                    )
                }
                
                if (!isProUser) {
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    SnyggButton(
                        elementName = FlorisImeUi.SmartbarActionTile.elementName,
                        onClick = {
                            if (canUseRewardedAd) {
                                // Launch the RewardedAdActivity to show the ad in full-screen
                                context.launchActivity(RewardedAdActivity::class) {
                                    // Use test ad unit ID when USE_TEST_ADS is true, otherwise use production
                                    val adUnitId = if (RewardedAdManager.USE_TEST_ADS) {
                                        RewardedAdManager.TEST_REWARDED_AD_UNIT_ID
                                    } else {
                                        RewardedAdManager.PROD_REWARDED_AD_UNIT_ID
                                    }
                                    it.putExtra(RewardedAdActivity.EXTRA_AD_UNIT_ID, adUnitId)
                                    // Add flag to ensure proper task management
                                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                // Dismiss the panel immediately since the ad will be shown in a new activity
                                onDismiss()
                            } else {
                                // Show subscription screen
                                // In a real implementation, you would navigate to the subscription screen
                                Toast.makeText(context, "Please upgrade to Pro for unlimited access", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)  // Consistent height with other buttons
                            .clip(RoundedCornerShape(8.dp)),
                        enabled = canUseRewardedAd
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (canUseRewardedAd) Icons.Default.PlayArrow else Icons.Default.Star,
                                contentDescription = if (canUseRewardedAd) "Watch Ad" else "Upgrade",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            SnyggText(
                                elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                                text = if (canUseRewardedAd) "Watch Ad" else "Upgrade"
                            )
                        }
                    }
                }
            }
        }
    }
}