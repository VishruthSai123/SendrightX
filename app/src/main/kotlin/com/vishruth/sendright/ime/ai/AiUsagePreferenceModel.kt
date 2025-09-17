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

package com.vishruth.key1.ime.ai

import dev.patrickgold.jetpref.datastore.annotations.Preferences
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import dev.patrickgold.jetpref.datastore.model.PreferenceModel

/**
 * Preference model for AI usage tracking.
 * This model defines the structure of the JetPref datastore for storing AI usage data.
 */
@Preferences
abstract class AiUsagePreferenceModel : PreferenceModel() {
    companion object {
        const val NAME = "ai-usage-prefs"
    }

    // Daily AI action count
    val dailyActionCount = int(
        key = "daily_action_count",
        default = 0
    )

    // Timestamp of the last action day (for daily reset)
    val lastActionDay = long(
        key = "last_action_day",
        default = 0L
    )

    // Flag indicating if a reward window is currently active
    val isRewardWindowActive = boolean(
        key = "is_reward_window_active",
        default = false
    )

    // Start time of the current reward window
    val rewardWindowStartTime = long(
        key = "reward_window_start_time",
        default = 0L
    )
}

val AiUsagePreferenceStore = jetprefDataStoreOf(AiUsagePreferenceModel::class)