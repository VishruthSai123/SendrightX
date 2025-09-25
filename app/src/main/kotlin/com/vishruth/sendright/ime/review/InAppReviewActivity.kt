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
import android.os.Bundle
import android.view.WindowManager
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.vishruth.key1.lib.devtools.flogDebug

/**
 * Transparent activity used to host Google Play In-App Review flow
 * when no Activity context is available (e.g., from keyboard service).
 * 
 * This activity is completely transparent and excludes itself from recents.
 */
class InAppReviewActivity : Activity() {
    
    companion object {
        const val EXTRA_REVIEW_INFO = "review_info"
    }
    
    private lateinit var reviewManager: ReviewManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make the activity transparent and exclude from recents
        setupTransparentActivity()
        
        reviewManager = ReviewManagerFactory.create(this)
        
        val reviewInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_REVIEW_INFO, ReviewInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<ReviewInfo>(EXTRA_REVIEW_INFO)
        }
        if (reviewInfo != null) {
            launchReviewFlow(reviewInfo)
        } else {
            flogDebug { "InAppReviewActivity: No ReviewInfo provided, finishing" }
            finish()
        }
    }
    
    private fun setupTransparentActivity() {
        // Make activity transparent
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.setFlags(
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        setFinishOnTouchOutside(false)
    }
    
    private fun launchReviewFlow(reviewInfo: ReviewInfo) {
        flogDebug { "InAppReviewActivity: Launching review flow" }
        
        val launchTask = reviewManager.launchReviewFlow(this, reviewInfo)
        
        launchTask.addOnCompleteListener { 
            flogDebug { "InAppReviewActivity: Review flow completed" }
            
            // Mark as completed in the manager
            val reviewManager = InAppReviewManager.getInstance(this)
            reviewManager.markReviewCompletedFromActivity()
            
            finish()
        }
        
        launchTask.addOnFailureListener { exception ->
            flogDebug { "InAppReviewActivity: Review flow failed: ${exception.message}" }
            
            // Handle failure in the manager
            val reviewManager = InAppReviewManager.getInstance(this)
            reviewManager.handleReviewFailureFromActivity("Review flow failed: ${exception.message}")
            
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        flogDebug { "InAppReviewActivity: Activity destroyed" }
    }
}