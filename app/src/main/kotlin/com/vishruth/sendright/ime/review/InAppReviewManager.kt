/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package com.vishruth.sendright.ime.review

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.vishruth.key1.BuildConfig
import com.vishruth.key1.lib.devtools.flogDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

/**
 * Manages Google Play In-App Review following best practices
 * 
 * Key Features:
 * - Triggers immediately after first 3 successful actions in same day
 * - Works for both free and pro users  
 * - Shows only once per user lifecycle
 * - Handles all edge cases and error scenarios
 * - Follows Google Play Review API guidelines
 */
class InAppReviewManager(private val context: Context) {
    
    private val reviewManager: ReviewManager = ReviewManagerFactory.create(context)
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "sendright_review_prefs"
        
        // Keys for SharedPreferences
        private const val KEY_REVIEW_REQUESTED = "review_requested"
        private const val KEY_REVIEW_COMPLETED = "review_completed" 
        private const val KEY_LAST_ACTION_DATE = "last_action_date"
        private const val KEY_DAILY_ACTION_COUNT = "daily_action_count"
        private const val KEY_TOTAL_ACTIONS = "total_actions"
        private const val KEY_REVIEW_DECLINED_COUNT = "review_declined_count"
        private const val KEY_LAST_REVIEW_ATTEMPT = "last_review_attempt"
        
        // Configuration constants
        private const val REQUIRED_DAILY_ACTIONS = 3
        private const val MAX_DECLINE_COUNT = 2
        private const val MIN_DAYS_BETWEEN_ATTEMPTS = 7
        
        private var instance: InAppReviewManager? = null
        
        fun getInstance(context: Context): InAppReviewManager {
            return instance ?: synchronized(this) {
                instance ?: InAppReviewManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * Records a successful action (AI text transformation accepted)
     * This should be called every time user accepts an AI transformation
     */
    fun recordSuccessfulAction() {
        val today = getCurrentDateString()
        val lastActionDate = prefs.getString(KEY_LAST_ACTION_DATE, "")
        
        if (today == lastActionDate) {
            // Same day - increment count
            val currentCount = prefs.getInt(KEY_DAILY_ACTION_COUNT, 0)
            val newCount = currentCount + 1
            
            prefs.edit()
                .putInt(KEY_DAILY_ACTION_COUNT, newCount)
                .putInt(KEY_TOTAL_ACTIONS, prefs.getInt(KEY_TOTAL_ACTIONS, 0) + 1)
                .apply()
                
            flogDebug { "InAppReview: Daily actions: $newCount" }
            
            // Check if we should trigger review
            if (newCount == REQUIRED_DAILY_ACTIONS) {
                checkAndTriggerReview()
            }
        } else {
            // New day - reset count
            prefs.edit()
                .putString(KEY_LAST_ACTION_DATE, today)
                .putInt(KEY_DAILY_ACTION_COUNT, 1)
                .putInt(KEY_TOTAL_ACTIONS, prefs.getInt(KEY_TOTAL_ACTIONS, 0) + 1)
                .apply()
                
            flogDebug { "InAppReview: New day, daily actions: 1" }
        }
    }
    
    /**
     * Checks all conditions and triggers review if appropriate
     */
    private fun checkAndTriggerReview() {
        CoroutineScope(Dispatchers.Main).launch {
            if (shouldShowReview()) {
                flogDebug { "InAppReview: All conditions met, attempting to show review" }
                requestReview()
            } else {
                flogDebug { "InAppReview: Conditions not met for showing review" }
            }
        }
    }
    
    /**
     * Determines if review should be shown based on all conditions
     */
    private fun shouldShowReview(): Boolean {
        // 1. Check if review was already completed
        if (prefs.getBoolean(KEY_REVIEW_COMPLETED, false)) {
            flogDebug { "InAppReview: Review already completed" }
            return false
        }
        
        // 2. Check if review was already requested (and presumably shown)
        if (prefs.getBoolean(KEY_REVIEW_REQUESTED, false)) {
            flogDebug { "InAppReview: Review already requested" }
            return false
        }
        
        // 3. Check if user declined too many times
        val declineCount = prefs.getInt(KEY_REVIEW_DECLINED_COUNT, 0)
        if (declineCount >= MAX_DECLINE_COUNT) {
            flogDebug { "InAppReview: User declined too many times ($declineCount)" }
            return false
        }
        
        // 4. Check minimum time between attempts
        val lastAttempt = prefs.getLong(KEY_LAST_REVIEW_ATTEMPT, 0)
        val daysSinceLastAttempt = (System.currentTimeMillis() - lastAttempt) / (24 * 60 * 60 * 1000)
        if (lastAttempt > 0 && daysSinceLastAttempt < MIN_DAYS_BETWEEN_ATTEMPTS) {
            flogDebug { "InAppReview: Too soon since last attempt ($daysSinceLastAttempt days)" }
            return false
        }
        
        // 5. Check daily actions requirement
        val dailyActions = prefs.getInt(KEY_DAILY_ACTION_COUNT, 0)
        if (dailyActions < REQUIRED_DAILY_ACTIONS) {
            flogDebug { "InAppReview: Not enough daily actions ($dailyActions < $REQUIRED_DAILY_ACTIONS)" }
            return false
        }
        
        flogDebug { "InAppReview: All conditions passed - should show review" }
        return true
    }
    
    /**
     * Requests and shows the in-app review
     */
    private suspend fun requestReview() {
        try {
            // Mark as requested immediately to prevent multiple attempts
            prefs.edit()
                .putBoolean(KEY_REVIEW_REQUESTED, true)
                .putLong(KEY_LAST_REVIEW_ATTEMPT, System.currentTimeMillis())
                .apply()
            
            val reviewInfo = requestReviewInfo()
            if (reviewInfo != null) {
                launchReviewFlow(reviewInfo)
            } else {
                handleReviewFailure("Failed to get ReviewInfo")
            }
            
        } catch (e: Exception) {
            handleReviewFailure("Exception during review request: ${e.message}")
        }
    }
    
    /**
     * Requests ReviewInfo from Google Play
     */
    private suspend fun requestReviewInfo(): ReviewInfo? = withContext(Dispatchers.IO) {
        return@withContext suspendCancellableCoroutine { continuation ->
            val request = reviewManager.requestReviewFlow()
            
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    flogDebug { "InAppReview: Successfully got ReviewInfo" }
                    continuation.resume(reviewInfo)
                } else {
                    flogDebug { "InAppReview: Failed to get ReviewInfo: ${task.exception?.message}" }
                    continuation.resume(null)
                }
            }
            
            request.addOnFailureListener { exception ->
                flogDebug { "InAppReview: RequestReviewFlow failed: ${exception.message}" }
                continuation.resume(null)
            }
        }
    }
    
    /**
     * Launches the review flow
     */
    private suspend fun launchReviewFlow(reviewInfo: ReviewInfo) {
        // First try to get Activity context
        val activity = getActivityFromContext()
        
        if (activity != null) {
            // We have an Activity context - use the standard approach
            withContext(Dispatchers.Main) {
                val launchTask = reviewManager.launchReviewFlow(activity, reviewInfo)
                
                launchTask.addOnCompleteListener { 
                    flogDebug { "InAppReview: Review flow completed successfully" }
                    markReviewCompleted()
                }
                
                launchTask.addOnFailureListener { exception ->
                    flogDebug { "InAppReview: Review flow failed: ${exception.message}" }
                    handleReviewFailure("Review flow failed: ${exception.message}")
                }
            }
        } else {
            // No Activity context - this is common for keyboard services
            // According to Google Play docs, the review flow should still work
            // We'll create a transparent activity to host the review
            flogDebug { "InAppReview: No Activity context, creating review activity" }
            createReviewActivity(reviewInfo)
        }
    }

    /**
     * Creates a transparent activity to host the in-app review flow
     * This is needed when we don't have direct Activity context (e.g., from keyboard service)
     */
    private fun createReviewActivity(reviewInfo: ReviewInfo) {
        try {
            val intent = Intent(context, InAppReviewActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(InAppReviewActivity.EXTRA_REVIEW_INFO, reviewInfo)
            }
            context.startActivity(intent)
            flogDebug { "InAppReview: Started review activity" }
        } catch (e: Exception) {
            flogDebug { "InAppReview: Failed to start review activity: ${e.message}" }
            // Only as last resort, fall back to Play Store
            openPlayStoreAsFallback()
        }
    }

    /**
     * Last resort fallback method to open Play Store for review
     * Only used when in-app review completely fails
     */
    private fun openPlayStoreAsFallback() {
        try {
            val packageName = context.packageName
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            markReviewCompleted()
            flogDebug { "InAppReview: Opened Play Store as last resort fallback" }
        } catch (e: Exception) {
            flogDebug { "InAppReview: Failed to open Play Store: ${e.message}" }
            handleReviewFailure("Play Store fallback failed: ${e.message}")
        }
    }
    
    /**
     * Extension function to find the activity context
     */
    private fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    /**
     * Tries to get Activity from Context
     */
    private fun getActivityFromContext(): Activity? {
        val activity = context.findActivity()
        if (activity != null) {
            flogDebug { "InAppReview: Found Activity context: ${activity::class.simpleName}" }
            return activity
        } else {
            flogDebug { "InAppReview: No Activity context found, will try fallback approach" }
            return null
        }
    }
    
    /**
     * Marks review as completed (successful)
     */
    private fun markReviewCompleted() {
        prefs.edit()
            .putBoolean(KEY_REVIEW_COMPLETED, true)
            .apply()
        flogDebug { "InAppReview: Marked as completed" }
    }
    
    /**
     * Handles review failure scenarios
     */
    private fun handleReviewFailure(reason: String) {
        flogDebug { "InAppReview: Handling failure - $reason" }
        
        // Increment decline count for rate limiting
        val currentDeclines = prefs.getInt(KEY_REVIEW_DECLINED_COUNT, 0)
        prefs.edit()
            .putInt(KEY_REVIEW_DECLINED_COUNT, currentDeclines + 1)
            .putBoolean(KEY_REVIEW_REQUESTED, false) // Allow retry later
            .apply()
    }
    
    /**
     * Gets current date as string (YYYY-MM-DD)
     */
    private fun getCurrentDateString(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return formatter.format(Date())
    }
    
    /**
     * Manual trigger for testing (should be removed in production)
     */
    fun triggerReviewForTesting() {
        if (BuildConfig.DEBUG) {
            flogDebug { "InAppReview: Manual trigger for testing" }
            CoroutineScope(Dispatchers.Main).launch {
                requestReview()
            }
        }
    }

    /**
     * Force trigger review bypassing all conditions (for testing)
     */
    fun forceReviewTrigger() {
        if (BuildConfig.DEBUG) {
            flogDebug { "InAppReview: Force triggering review for testing" }
            CoroutineScope(Dispatchers.Main).launch {
                // Temporarily bypass all conditions
                val originalRequested = prefs.getBoolean(KEY_REVIEW_REQUESTED, false)
                val originalCompleted = prefs.getBoolean(KEY_REVIEW_COMPLETED, false)
                
                // Reset flags temporarily
                prefs.edit()
                    .putBoolean(KEY_REVIEW_REQUESTED, false)
                    .putBoolean(KEY_REVIEW_COMPLETED, false)
                    .putInt(KEY_DAILY_ACTION_COUNT, REQUIRED_DAILY_ACTIONS)
                    .apply()
                    
                requestReview()
                
                // Restore original flags if review fails
                prefs.edit()
                    .putBoolean(KEY_REVIEW_REQUESTED, originalRequested)
                    .putBoolean(KEY_REVIEW_COMPLETED, originalCompleted)
                    .apply()
            }
        }
    }
    
    /**
     * Resets all review data (for testing purposes)
     */
    fun resetReviewData() {
        if (BuildConfig.DEBUG) {
            prefs.edit().clear().apply()
            flogDebug { "InAppReview: Reset all review data" }
        }
    }
    
    /**
     * Gets current review statistics (for debugging)
     */
    fun getReviewStats(): ReviewStats {
        return ReviewStats(
            totalActions = prefs.getInt(KEY_TOTAL_ACTIONS, 0),
            dailyActions = prefs.getInt(KEY_DAILY_ACTION_COUNT, 0),
            lastActionDate = prefs.getString(KEY_LAST_ACTION_DATE, ""),
            reviewRequested = prefs.getBoolean(KEY_REVIEW_REQUESTED, false),
            reviewCompleted = prefs.getBoolean(KEY_REVIEW_COMPLETED, false),
            declineCount = prefs.getInt(KEY_REVIEW_DECLINED_COUNT, 0)
        )
    }

    /**
     * Called by InAppReviewActivity when review is completed successfully
     */
    fun markReviewCompletedFromActivity() {
        markReviewCompleted()
    }

    /**
     * Called by InAppReviewActivity when review fails
     */
    fun handleReviewFailureFromActivity(reason: String) {
        handleReviewFailure(reason)
    }
}

/**
 * Data class for review statistics
 */
data class ReviewStats(
    val totalActions: Int,
    val dailyActions: Int,
    val lastActionDate: String?,
    val reviewRequested: Boolean,
    val reviewCompleted: Boolean,
    val declineCount: Int
)