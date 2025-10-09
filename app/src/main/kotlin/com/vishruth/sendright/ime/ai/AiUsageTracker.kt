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
     * Load initial usage stats after initialization and perform any necessary resets.
     * This should be called after the datastore is ready.
     * This method ensures daily counts are properly reset even if user hasn't interacted since yesterday.
     */
    fun loadInitialUsageStats() {
        coroutineScope.launch {
            try {
                // Load stats which will automatically handle daily reset if needed
                loadUsageStats()
                
                // Log current state for debugging
                val currentStats = _usageStats.value
                Log.d(TAG, "AI Usage Stats loaded successfully")
                Log.d(TAG, "Current state: dailyCount=${currentStats.dailyActionCount}, " +
                          "rewardActive=${currentStats.isRewardWindowActive}, " +
                          "lastAction=${currentStats.lastActionTimestamp}")
                
                // If we're in a reward window, log remaining time
                if (currentStats.isRewardWindowActive) {
                    val remainingMs = currentStats.rewardWindowTimeRemaining()
                    val remainingHours = remainingMs / (1000 * 60 * 60)
                    val remainingMinutes = (remainingMs % (1000 * 60 * 60)) / (1000 * 60)
                    Log.d(TAG, "Reward window active: ${remainingHours}h ${remainingMinutes}m remaining")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading initial usage stats", e)
            }
        }
    }
    
    /**
     * Check if the user has pro subscription from multiple sources.
     *
     * @return true if the user is a pro user, false otherwise
     */
    fun isProUser(): Boolean {
        val userManager = UserManager.getInstance()
        val userData = userManager.userData.value
        val subscriptionManager = userManager.getSubscriptionManager()
        
        return userData?.subscriptionStatus == "pro" || 
               subscriptionManager?.isPro?.value == true
    }
    
    /**
     * Check if an AI action is allowed without recording it.
     * This method should be called before performing any AI operation to check limits.
     * ALWAYS refreshes stats from datastore to avoid cache issues.
     *
     * @return true if the action is allowed, false if the limit has been reached
     */
    suspend fun canUseAiAction(): Boolean {
        // ALWAYS refresh stats first to avoid cache issues with midnight reset
        loadUsageStats()
        
        // First, check and update reward window status
        checkAndEndExpiredRewardWindow()
        
        // Get current stats (freshly loaded)
        val currentStats = _usageStats.value
        
        // If we're in a reward window, allow unlimited actions
        if (currentStats.isRewardWindowActive) {
            Log.d(TAG, "AI action allowed - in reward window")
            return true
        }
        
        // If user is pro, allow unlimited actions
        if (isProUser()) {
            Log.d(TAG, "AI action allowed - pro user (unlimited access)")
            return true
        }
        
        // Check if we've reached the daily limit
        if (currentStats.dailyActionCount >= DAILY_LIMIT) {
            Log.d(TAG, "AI action denied - daily limit reached for free user (${currentStats.dailyActionCount}/${DAILY_LIMIT})")
            return false
        }
        
        Log.d(TAG, "AI action allowed - under daily limit (${currentStats.dailyActionCount}/${DAILY_LIMIT})")
        return true
    }
    
    /**
     * Record a successful AI action (only call this after a successful API response).
     * This method should be called only when an AI operation completes successfully.
     * ALWAYS refreshes stats to ensure accurate counting.
     */
    suspend fun recordSuccessfulAiAction() {
        // ALWAYS refresh stats first to get the latest data (handles daily resets)
        loadUsageStats()
        
        // Get current stats after refresh
        val currentStats = _usageStats.value
        
        // Don't count actions for pro users or during reward windows
        if (isProUser() || currentStats.isRewardWindowActive) {
            Log.d(TAG, "AI action not counted - pro user or reward window active")
            return
        }
        
        // Increment the daily action count
        val newCount = currentStats.dailyActionCount + 1
        prefs.dailyActionCount.set(newCount)
        prefs.lastActionDay.set(System.currentTimeMillis())
        
        // Force refresh the state flow immediately
        loadUsageStats()
        
        Log.d(TAG, "Successful AI action recorded - count: $newCount")
    }
    
    /**
     * Record an AI action and check if it's allowed.
     * This method should be called before performing any AI operation.
     * 
     * @deprecated Use canUseAiAction() to check limits and recordSuccessfulAiAction() to count successful actions
     * @return true if the action is allowed, false if the limit has been reached
     */
    @Deprecated("Use canUseAiAction() and recordSuccessfulAiAction() separately for better UX")
    suspend fun recordAiAction(): Boolean {
        return canUseAiAction()
    }
    
    /**
     * Start a reward window (24 hours of unlimited AI actions).
     * This should be called after the user successfully watches a rewarded ad.
     */
    suspend fun startRewardWindow() {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + REWARD_WINDOW_DURATION_MS
        
        prefs.isRewardWindowActive.set(true)
        prefs.rewardWindowStartTime.set(startTime)
        
        // Update the state flow
        loadUsageStats()
        
        val durationHours = REWARD_WINDOW_DURATION_MS / (1000 * 60 * 60)
        Log.d(TAG, "Reward window started - ${durationHours}h duration, ending at: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(endTime))}")
        
        // Schedule automatic end of reward window after duration
        coroutineScope.launch {
            kotlinx.coroutines.delay(REWARD_WINDOW_DURATION_MS)
            // Refresh stats before ending to ensure we have the latest data
            loadUsageStats()
            endRewardWindow()
        }
    }
    
    /**
     * End the current reward window.
     * This can be called manually or automatically after the duration expires.
     */
    suspend fun endRewardWindow() {
        prefs.isRewardWindowActive.set(false)
        prefs.rewardWindowStartTime.set(0L)
        
        // Update the state flow (this will automatically handle daily reset if needed)
        loadUsageStats()
        
        Log.d(TAG, "Reward window ended")
    }
    
    /**
     * Get the current usage statistics.
     * ALWAYS forces a fresh load from datastore to avoid cache issues.
     *
     * @return Current AI usage statistics (fresh from datastore)
     */
    suspend fun getUsageStats(): AiUsageStats {
        // ALWAYS force refresh to ensure no cache issues
        loadUsageStats()
        return _usageStats.value
    }
    
    /**
     * Force refresh usage stats immediately.
     * Call this when you need to ensure the UI shows the most current data.
     */
    suspend fun forceRefreshUsageStats() {
        Log.d(TAG, "Force refreshing usage stats to avoid cache issues")
        loadUsageStats()
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
            
            // Reset daily count if it's a new day - always reset regardless of reward window status
            // This ensures that midnight reset works correctly for all users regardless of timezone
            // and guarantees that users get fresh 5 AI actions every day at their local midnight
            val shouldResetToday = shouldResetDailyCount(lastActionDay)
            var actuallyReset = false
            
            if (shouldResetToday) {
                Log.d(TAG, "Resetting daily action count from $dailyActionCount to 0 (new day detected)")
                prefs.dailyActionCount.set(0)
                actuallyReset = true
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
                Log.d(TAG, "Reward window expired, deactivating")
                prefs.isRewardWindowActive.set(false)
            }
            
            val stats = AiUsageStats(
                dailyActionCount = if (actuallyReset) 0 else dailyActionCount,
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
        if (lastActionDay == 0L) {
            Log.d(TAG, "No previous action day recorded, no reset needed")
            return false
        }
        
        val lastActionCalendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = lastActionDay
        }
        
        val currentCalendar = Calendar.getInstance(TimeZone.getDefault())
        
        // Compare year, month, and day to determine if it's a new day
        val shouldReset = lastActionCalendar.get(Calendar.YEAR) != currentCalendar.get(Calendar.YEAR) ||
               lastActionCalendar.get(Calendar.MONTH) != currentCalendar.get(Calendar.MONTH) ||
               lastActionCalendar.get(Calendar.DAY_OF_MONTH) != currentCalendar.get(Calendar.DAY_OF_MONTH)
        
        if (shouldReset) {
            val lastDay = "${lastActionCalendar.get(Calendar.YEAR)}-${lastActionCalendar.get(Calendar.MONTH) + 1}-${lastActionCalendar.get(Calendar.DAY_OF_MONTH)}"
            val currentDay = "${currentCalendar.get(Calendar.YEAR)}-${currentCalendar.get(Calendar.MONTH) + 1}-${currentCalendar.get(Calendar.DAY_OF_MONTH)}"
            Log.d(TAG, "Daily reset triggered: $lastDay -> $currentDay")
        }
        
        return shouldReset
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
    
    /**
     * Check if daily usage has been reset (new day detected).
     * This can be used by UI components to detect when limits have been refreshed.
     *
     * @return true if daily usage was just reset, false otherwise
     */
    suspend fun hasUsageBeenReset(): Boolean {
        val oldStats = _usageStats.value
        loadUsageStats() // Force refresh
        val newStats = _usageStats.value
        
        // If daily count went from non-zero to zero, usage was reset
        val wasReset = oldStats.dailyActionCount > 0 && newStats.dailyActionCount == 0
        
        if (wasReset) {
            Log.d(TAG, "Daily usage reset detected: ${oldStats.dailyActionCount} -> ${newStats.dailyActionCount}")
        }
        
        return wasReset
    }
}