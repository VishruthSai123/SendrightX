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

/**
 * Data class representing AI usage statistics.
 * This class holds all the current usage information for display in the UI.
 */
data class AiUsageStats(
    val dailyActionCount: Int,
    val lastActionTimestamp: Long,
    val isRewardWindowActive: Boolean,
    val rewardWindowStartTime: Long,
    val rewardWindowEndTime: Long
) {
    companion object {
        // Daily limit for AI actions
        const val DAILY_LIMIT = 5
        
        // Duration of reward window in milliseconds (60 minutes)
        const val REWARD_WINDOW_DURATION_MS = 60 * 60 * 1000L
        
        // Create default/empty stats
        fun empty() = AiUsageStats(
            dailyActionCount = 0,
            lastActionTimestamp = 0L,
            isRewardWindowActive = false,
            rewardWindowStartTime = 0L,
            rewardWindowEndTime = 0L
        )
    }
    
    /**
     * Calculate remaining actions for the day
     */
    fun remainingActions(): Int {
        return if (isRewardWindowActive) {
            // Unlimited during reward window
            -1
        } else {
            // Regular daily limit calculation
            maxOf(0, DAILY_LIMIT - dailyActionCount)
        }
    }
    
    /**
     * Calculate remaining time in reward window in milliseconds
     */
    fun rewardWindowTimeRemaining(): Long {
        return if (isRewardWindowActive && rewardWindowEndTime > 0) {
            val remaining = rewardWindowEndTime - System.currentTimeMillis()
            maxOf(0L, remaining)
        } else {
            0L
        }
    }
    
    /**
     * Check if the reward window has expired
     */
    fun isRewardWindowExpired(): Boolean {
        return isRewardWindowActive && System.currentTimeMillis() >= rewardWindowEndTime
    }
}