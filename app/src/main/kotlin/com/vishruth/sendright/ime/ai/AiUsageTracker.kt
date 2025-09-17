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

import android.content.Context
import android.util.Log
import com.vishruth.key1.ime.ai.AiUsageStats.Companion.DAILY_LIMIT
import com.vishruth.key1.ime.ai.AiUsageStats.Companion.REWARD_WINDOW_DURATION_MS
import com.vishruth.key1.user.UserManager
import dev.patrickgold.jetpref.datastore.runtime.initAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

/**
 * Singleton class for tracking AI usage and managing reward windows.
 * This class handles all logic for tracking daily AI action counts,
 * managing reward windows, and providing usage statistics.
 */
class AiUsageTracker private constructor() {
    private val prefs by AiUsagePreferenceStore
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    // State flow for emitting usage stats updates
    private val _usageStats = MutableStateFlow(AiUsageStats.empty())
    val usageStats: StateFlow<AiUsageStats> = _usageStats.asStateFlow()
    
    companion object {
        private const val TAG = "AiUsageTracker"
        private var instance: AiUsageTracker? = null
        
        /**
         * Get the singleton instance of AiUsageTracker
         */
        @Synchronized
        fun getInstance(): AiUsageTracker {
            if (instance == null) {
                instance = AiUsageTracker()
            }
            return instance!!
        }
    }
    
    /**
     * Initialize the AI usage tracker with the application context.
     * This should be called during application startup.
     *
     * @param context The application context
     */
    suspend fun initialize(context: Context) {
        try {
            // Initialize the JetPref datastore asynchronously
            AiUsagePreferenceStore.initAndroid(
                context = context,
                datastoreName = AiUsagePreferenceModel.NAME
            )
            
            Log.d(TAG, "AI Usage Tracker initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AI Usage Tracker", e)
        }
    }
    
    /**
     * Load initial usage stats after initialization.
     * This should be called after the datastore is ready.
     */
    fun loadInitialUsageStats() {
        coroutineScope.launch {
            try {
                loadUsageStats()
                Log.d(TAG, "AI Usage Stats loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading initial usage stats", e)
            }
        }
    }
    
    /**
     * Record an AI action and check if it's allowed.
     * This method should be called before performing any AI operation.
     *
     * @return true if the action is allowed, false if the limit has been reached
     */
    suspend fun recordAiAction(): Boolean {
        // First, check and update reward window status
        checkAndEndExpiredRewardWindow()
        
        // Get current stats
        val currentStats = _usageStats.value
        
        // If we're in a reward window, allow unlimited actions
        if (currentStats.isRewardWindowActive) {
            Log.d(TAG, "AI action allowed - in reward window")
            return true
        }
        
        // Check if we've reached the daily limit
        if (currentStats.dailyActionCount >= DAILY_LIMIT) {
            // Check if user has pro subscription
            val userManager = UserManager.getInstance()
            val userData = userManager.userData.value
            
            // If user is pro, allow unlimited actions
            if (userData?.subscriptionStatus == "pro") {
                Log.d(TAG, "AI action allowed - pro user")
                return true
            }
            
            Log.d(TAG, "AI action denied - daily limit reached")
            return false
        }
        
        // Increment the daily action count
        val newCount = currentStats.dailyActionCount + 1
        prefs.dailyActionCount.set(newCount)
        prefs.lastActionDay.set(System.currentTimeMillis())
        
        // Update the state flow
        loadUsageStats()
        
        Log.d(TAG, "AI action recorded - count: $newCount")
        return true
    }
    
    /**
     * Start a reward window (60 minutes of unlimited AI actions).
     * This should be called after the user successfully watches a rewarded ad.
     */
    suspend fun startRewardWindow() {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + REWARD_WINDOW_DURATION_MS
        
        prefs.isRewardWindowActive.set(true)
        prefs.rewardWindowStartTime.set(startTime)
        
        // Update the state flow
        loadUsageStats()
        
        Log.d(TAG, "Reward window started - will end at: $endTime")
        
        // Schedule automatic end of reward window after 60 minutes
        coroutineScope.launch {
            kotlinx.coroutines.delay(REWARD_WINDOW_DURATION_MS)
            // Refresh stats before ending to ensure we have the latest data
            loadUsageStats()
            endRewardWindow()
        }
    }
    
    /**
     * End the current reward window.
     * This can be called manually or automatically after 60 minutes.
     */
    suspend fun endRewardWindow() {
        prefs.isRewardWindowActive.set(false)
        prefs.rewardWindowStartTime.set(0L)
        
        // Update the state flow
        loadUsageStats()
        
        Log.d(TAG, "Reward window ended")
    }
    
    /**
     * Get the current usage statistics.
     *
     * @return Current AI usage statistics
     */
    suspend fun getUsageStats(): AiUsageStats {
        loadUsageStats()
        return _usageStats.value
    }
    
    /**
     * Reset all AI usage data (for testing/debugging purposes).
     */
    suspend fun resetAllData() {
        prefs.dailyActionCount.set(0)
        prefs.lastActionDay.set(0L)
        prefs.isRewardWindowActive.set(false)
        prefs.rewardWindowStartTime.set(0L)
        
        // Update the state flow
        loadUsageStats()
        
        Log.d(TAG, "All AI usage data reset")
    }
    
    /**
     * Load current usage statistics from the datastore and update the state flow.
     */
    private suspend fun loadUsageStats() {
        try {
            val dailyActionCount = prefs.dailyActionCount.get()
            val lastActionDay = prefs.lastActionDay.get()
            val isRewardWindowActive = prefs.isRewardWindowActive.get()
            val rewardWindowStartTime = prefs.rewardWindowStartTime.get()
            
            // Reset daily count if it's a new day
            val resetDailyCount = shouldResetDailyCount(lastActionDay)
            if (resetDailyCount) {
                prefs.dailyActionCount.set(0)
            }
            
            // Calculate reward window end time
            val rewardWindowEndTime = if (isRewardWindowActive && rewardWindowStartTime > 0) {
                rewardWindowStartTime + REWARD_WINDOW_DURATION_MS
            } else {
                0L
            }
            
            // Check if reward window has expired
            val currentTime = System.currentTimeMillis()
            val isRewardWindowStillActive = isRewardWindowActive && currentTime < rewardWindowEndTime
            
            // If reward window has expired, update the preference
            if (isRewardWindowActive && !isRewardWindowStillActive) {
                prefs.isRewardWindowActive.set(false)
            }
            
            val stats = AiUsageStats(
                dailyActionCount = if (resetDailyCount) 0 else dailyActionCount,
                lastActionTimestamp = lastActionDay,
                isRewardWindowActive = isRewardWindowStillActive,
                rewardWindowStartTime = if (isRewardWindowStillActive) rewardWindowStartTime else 0L,
                rewardWindowEndTime = if (isRewardWindowStillActive) rewardWindowEndTime else 0L
            )
            
            _usageStats.value = stats
        } catch (e: Exception) {
            Log.e(TAG, "Error loading usage stats", e)
            _usageStats.value = AiUsageStats.empty()
        }
    }
    
    /**
     * Check if the daily count should be reset (new day).
     *
     * @param lastActionDay Timestamp of the last action
     * @return true if the count should be reset, false otherwise
     */
    private fun shouldResetDailyCount(lastActionDay: Long): Boolean {
        if (lastActionDay == 0L) return false
        
        val lastActionCalendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = lastActionDay
        }
        
        val currentCalendar = Calendar.getInstance(TimeZone.getDefault())
        
        // Compare year, month, and day to determine if it's a new day
        return lastActionCalendar.get(Calendar.YEAR) != currentCalendar.get(Calendar.YEAR) ||
               lastActionCalendar.get(Calendar.MONTH) != currentCalendar.get(Calendar.MONTH) ||
               lastActionCalendar.get(Calendar.DAY_OF_MONTH) != currentCalendar.get(Calendar.DAY_OF_MONTH)
    }
    
    /**
     * Check if the reward window has expired and end it if necessary.
     */
    private suspend fun checkAndEndExpiredRewardWindow() {
        // Force refresh the stats to get the latest data
        loadUsageStats()
        val currentStats = _usageStats.value
        if (currentStats.isRewardWindowActive && currentStats.isRewardWindowExpired()) {
            endRewardWindow()
        }
    }
}