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

import android.content.Context
import com.vishruth.key1.BuildConfig
import com.vishruth.key1.lib.devtools.flogDebug

/**
 * Debug helper for testing in-app review functionality
 * Only available in debug builds
 */
object InAppReviewDebugHelper {
    
    /**
     * Logs current review statistics
     */
    fun logReviewStats(context: Context) {
        if (!BuildConfig.DEBUG) return
        
        val reviewManager = InAppReviewManager.getInstance(context)
        val stats = reviewManager.getReviewStats()
        
        flogDebug { 
            """
            |InAppReview Debug Stats:
            |  Total Actions: ${stats.totalActions}
            |  Daily Actions: ${stats.dailyActions}
            |  Last Action Date: ${stats.lastActionDate}
            |  Review Requested: ${stats.reviewRequested}
            |  Review Completed: ${stats.reviewCompleted}
            |  Decline Count: ${stats.declineCount}
            """.trimMargin()
        }
    }
    
    /**
     * Simulates multiple successful actions for testing
     */
    fun simulateActions(context: Context, count: Int = 3) {
        if (!BuildConfig.DEBUG) return
        
        val reviewManager = InAppReviewManager.getInstance(context)
        repeat(count) {
            reviewManager.recordSuccessfulAction()
        }
        flogDebug { "InAppReview Debug: Simulated $count actions" }
        logReviewStats(context)
    }
    
    /**
     * Forces review trigger for testing
     */
    fun forceReviewTrigger(context: Context) {
        if (!BuildConfig.DEBUG) return
        
        val reviewManager = InAppReviewManager.getInstance(context)
        reviewManager.forceReviewTrigger()
        flogDebug { "InAppReview Debug: Forced review trigger" }
    }
    
    /**
     * Resets all review data for testing
     */
    fun resetReviewData(context: Context) {
        if (!BuildConfig.DEBUG) return
        
        val reviewManager = InAppReviewManager.getInstance(context)
        reviewManager.resetReviewData()
        flogDebug { "InAppReview Debug: Reset all review data" }
        logReviewStats(context)
    }
    
    /**
     * Tests the complete review flow
     */
    fun testReviewFlow(context: Context) {
        if (!BuildConfig.DEBUG) return
        
        flogDebug { "InAppReview Debug: Starting test flow" }
        
        // Reset data
        resetReviewData(context)
        
        // Simulate enough actions to trigger review
        simulateActions(context, 5) // More than minimum to ensure trigger
        
        logReviewStats(context)
    }
}