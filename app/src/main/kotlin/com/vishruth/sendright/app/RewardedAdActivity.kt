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

package com.vishruth.key1.app

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.vishruth.key1.ime.ai.AiUsageTracker
import com.vishruth.key1.lib.ads.RewardedAdManager
import com.vishruth.key1.user.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Activity for displaying rewarded ads in full-screen mode.
 * This activity is launched when the user wants to watch a rewarded ad
 * to unlock AI features.
 */
class RewardedAdActivity : ComponentActivity() {
    companion object {
        private const val TAG = "RewardedAdActivity"
        const val EXTRA_AD_UNIT_ID = "ad_unit_id"
    }

    private lateinit var rewardedAdManager: RewardedAdManager
    private var rewardedAd: RewardedAd? = null
    private lateinit var layout: FrameLayout
    private var currentToast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "RewardedAdActivity onCreate called")
        Log.d(TAG, "Activity class: ${this.javaClass.name}")
        Log.d(TAG, "Activity package name: ${this.packageName}")
        
        // Create a simple layout
        layout = FrameLayout(this)
        layout.id = android.R.id.content
        setContentView(layout)
        
        // Get the ad unit ID from the intent
        val adUnitId = if (RewardedAdManager.USE_TEST_ADS) {
            RewardedAdManager.TEST_REWARDED_AD_UNIT_ID
        } else {
            intent.getStringExtra(EXTRA_AD_UNIT_ID) ?: RewardedAdManager.PROD_REWARDED_AD_UNIT_ID
        }
        
        Log.d(TAG, "Loading rewarded ad with unit ID: $adUnitId")
        Log.d(TAG, "Using test ads mode: ${RewardedAdManager.USE_TEST_ADS}")
        
        // Initialize the rewarded ad manager
        rewardedAdManager = RewardedAdManager(this)
        rewardedAdManager.setAdUnitId(adUnitId)
        
        // Check network connectivity before loading ad
        if (isNetworkAvailable()) {
            Log.d(TAG, "Network is available, proceeding with ad loading")
            // Load and show the rewarded ad
            loadAndShowRewardedAd()
        } else {
            Log.e(TAG, "No internet connection available")
            showToast("No internet connection. Please connect to the internet and try again.")
            // Delay finish to allow user to see the error message
            layout.postDelayed({
                finish()
            }, 3000)
        }
    }

    private fun loadAndShowRewardedAd() {
        // Show loading message immediately
        showToast("Loading ad...")
        Log.d(TAG, "Starting loadAndShowRewardedAd process")
        
        rewardedAdManager.loadRewardedAd { loadedRewardedAd ->
            if (loadedRewardedAd != null) {
                Log.d(TAG, "Rewarded ad loaded successfully in callback")
                showToast("Ad loaded successfully!")
                this.rewardedAd = loadedRewardedAd
                showRewardedAd()
            } else {
                Log.e(TAG, "Failed to load rewarded ad in callback")
                // The error toast is already shown in RewardedAdManager
                // showToast("Failed to load ad. Please try again later.")
                // Delay finish to allow user to see the error message
                layout.postDelayed({
                    finish()
                }, 3000)
            }
        }
    }

    private fun showRewardedAd() {
        Log.d(TAG, "Attempting to show rewarded ad")
        val ad = rewardedAd
        if (ad == null) {
            Log.e(TAG, "No rewarded ad available to show")
            showToast("No ad available to show")
            finish()
            return
        }
        
        Log.d(TAG, "Setting full screen content callback")
        // Set the full screen content callback before showing the ad
        rewardedAdManager.setFullScreenContentCallback(object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad dismissed")
                showToast("Ad dismissed")
                finish()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Ad failed to show: ${adError.message}")
                Log.e(TAG, "Ad failed to show with code: ${adError.code}")
                Log.e(TAG, "Ad failed to show with domain: ${adError.domain}")
                showToast("Ad failed to show: ${adError.message} (Code: ${adError.code})")
                finish()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad showed")
                // showToast("Ad is now playing...") // This might interfere with other toasts
            }
            
            override fun onAdImpression() {
                Log.d(TAG, "Rewarded ad impression recorded")
            }
            
            override fun onAdClicked() {
                Log.d(TAG, "Rewarded ad clicked")
            }
        })

        Log.d(TAG, "Calling rewardedAdManager.showRewardedAd")
        val shown = rewardedAdManager.showRewardedAd(this, OnUserEarnedRewardListener { rewardItem ->
            Log.d(TAG, "User earned reward: ${rewardItem.type} - ${rewardItem.amount}")
            showToast("Unlimited AI unlocked for 60 minutes!")
            // Automatically start the reward window
            CoroutineScope(Dispatchers.IO).launch {
                AiUsageTracker.getInstance().startRewardWindow()
                // Record rewarded ad usage
                UserManager.getInstance().recordRewardedAdUsage()
            }
        })
        
        if (!shown) {
            Log.e(TAG, "Failed to show rewarded ad")
            showToast("Failed to show ad")
            finish()
        } else {
            Log.d(TAG, "Rewarded ad show method returned true")
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    private fun showToast(message: String) {
        // Cancel any existing toast to prevent stacking
        currentToast?.cancel()
        
        // Create and show new toast
        currentToast = Toast.makeText(this, message, Toast.LENGTH_LONG)
        currentToast?.show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
        currentToast?.cancel()
        currentToast = null
        rewardedAdManager.destroy()
        rewardedAd = null
    }
}