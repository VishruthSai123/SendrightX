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

package com.vishruth.key1.lib.ads

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.vishruth.key1.user.UserManager
import com.vishruth.sendright.lib.network.NetworkUtils
import kotlinx.coroutines.flow.firstOrNull

/**
 * Manager class for handling rewarded advertisements.
 * Provides methods for loading and displaying rewarded ads that users can watch to earn rewards.
 */
class RewardedAdManager(private val context: Context) {
    private var rewardedAd: RewardedAd? = null
    private var adUnitId: String = TEST_REWARDED_AD_UNIT_ID
    private var rewardCallback: ((Boolean) -> Unit)? = null
    // Use a single coroutine scope for this manager to prevent resource leaks
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    // Store the application context to ensure we're using the right context
    private val applicationContext = context.applicationContext
    
    // Flag to track if we've already tried fallback to test ads
    private var hasTriedTestAdsFallback = false
    
    companion object {
        private const val TAG = "RewardedAdManager"
        // Test ad unit ID for rewarded ads - replace with your own for production
        const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917" // Updated to official test ID
        
        // Production ad unit ID for rewarded ads
        const val PROD_REWARDED_AD_UNIT_ID = "ca-app-pub-1496070957048863/4659258018"
        
        // Flag to enable test ads (set to true for debugging) - SWITCHING TO TRUE FOR SAFETY
        const val USE_TEST_ADS = true
        
        // Maximum time to wait for AdMob SDK initialization (in milliseconds)
        private const val MAX_INITIALIZATION_WAIT_TIME = 3000L // Reduced from 5000L for faster response
        private const val INITIALIZATION_CHECK_INTERVAL = 200L // Increased from 100L
    }
    
    /**
     * Gets the ad unit ID being used for loading rewarded ads.
     *
     * @return The ad unit ID
     */
    fun getAdUnitId(): String = adUnitId
    
    /**
     * Sets the ad unit ID to use for loading rewarded ads.
     *
     * @param adUnitId The ad unit ID to use
     */
    fun setAdUnitId(adUnitId: String) {
        this.adUnitId = adUnitId
    }
    
    /**
     * Waits for AdMob SDK to be initialized with a timeout.
     *
     * @return true if initialized within timeout, false otherwise
     */
    private suspend fun waitForInitialization(): Boolean {
        // Ensure AdMob SDK is initialized
        AdManager.ensureInitialized(applicationContext)
        
        // Only log if not already initialized to reduce log spam
        if (!AdManager.isInitialized()) {
            Log.d(TAG, "Waiting for AdMob SDK initialization")
        }
        
        // Wait for initialization with timeout using the AdManager's method
        return AdManager.waitForInitialization(MAX_INITIALIZATION_WAIT_TIME)
    }
    
    /**
     * Loads a rewarded ad asynchronously.
     *
     * @param callback Optional callback to be notified when the ad is loaded or fails to load
     */
    fun loadRewardedAd(callback: ((RewardedAd?) -> Unit)? = null) {
        // Run ad loading on main thread as per AdMob guidelines
        coroutineScope.launch {
            try {
                Log.d(TAG, "Starting rewarded ad load process")
                
                // Check network connectivity before attempting to load ad
                if (!NetworkUtils.isNetworkAvailable(applicationContext)) {
                    Log.e(TAG, "No internet connection available for ad loading")
                    showToast("No Internet Connection")
                    callback?.invoke(null)
                    return@launch
                }
                
                // Wait for AdMob SDK to be initialized with a shorter timeout
                if (!waitForInitialization()) {
                    Log.e(TAG, "AdMob SDK failed to initialize within timeout")
                    // Show toast with error information
                    showToast("AdMob SDK initialization timeout")
                    callback?.invoke(null)
                    return@launch
                }
                
                Log.d(TAG, "AdMob SDK is initialized, proceeding with ad request")
                
                // Check if we should use test ads (either forced or as fallback)
                val effectiveAdUnitId = if (RewardedAdManager.USE_TEST_ADS || hasTriedTestAdsFallback) {
                    Log.d(TAG, "Using test ad unit ID")
                    RewardedAdManager.TEST_REWARDED_AD_UNIT_ID
                } else {
                    Log.d(TAG, "Using configured ad unit ID: $adUnitId")
                    adUnitId
                }
                
                val adRequest = AdRequest.Builder().build()
                Log.d(TAG, "Ad request built, loading ad with unit ID: $effectiveAdUnitId")
                
                RewardedAd.load(applicationContext, effectiveAdUnitId, adRequest, object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(rewardedAd: RewardedAd) {
                        Log.d(TAG, "Rewarded ad loaded successfully")
                        
                        // Set server-side verification options if needed
                        rewardedAd.setServerSideVerificationOptions(
                            ServerSideVerificationOptions.Builder()
                                .build()
                        )
                        
                        // Store the loaded ad
                        this@RewardedAdManager.rewardedAd = rewardedAd
                        
                        callback?.invoke(rewardedAd)
                    }
                    
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        Log.e(TAG, "Rewarded ad failed to load: ${adError.message}")
                        Log.e(TAG, "Ad failed to load with code: ${adError.code}")
                        Log.e(TAG, "Ad failed to load with domain: ${adError.domain}")
                        
                        // Log specific error codes for better debugging
                        val errorMessage = when (adError.code) {
                            0 -> "Internal error occurred (Code: 0)"
                            1 -> "Invalid ad request (Code: 1)"
                            2 -> "Network error occurred (Code: 2)"
                            3 -> {
                                // Special handling for NO_FILL error
                                if (adError.message?.contains("Publisher data not found", ignoreCase = true) == true) {
                                    "No ad available - Publisher data not found (Code: 3)"
                                } else {
                                    "No ad available to serve (Code: 3)"
                                }
                            }
                            4 -> "App ID is missing (Code: 4)"
                            5 -> "App ID mismatch (Code: 5)"
                            6 -> "License error (Code: 6)"
                            7 -> "Ad loading interrupted (Code: 7)"
                            else -> "Unknown error (Code: ${adError.code})"
                        }
                        
                        // Show toast with error information
                        showToast("Ad failed to load: $errorMessage")
                        
                        // If we get a NO_FILL error and haven't tried test ads yet, try with test ads
                        if (adError.code == 3 && !hasTriedTestAdsFallback && !RewardedAdManager.USE_TEST_ADS) {
                            Log.d(TAG, "NO_FILL error detected, trying fallback to test ads")
                            hasTriedTestAdsFallback = true
                            // Retry with test ads after a short delay to prevent blocking
                            coroutineScope.launch {
                                delay(100) // Small delay to prevent blocking
                                loadRewardedAd(callback)
                            }
                            return
                        }
                        
                        this@RewardedAdManager.rewardedAd = null
                        callback?.invoke(null)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error loading rewarded ad", e)
                // Show toast with error information
                showToast("Error loading rewarded ad: ${e.message}")
                this@RewardedAdManager.rewardedAd = null
                callback?.invoke(null)
            }
        }
    }
    
    private fun showToast(message: String) {
        // This will be called from the main thread due to coroutineScope.launch
        if (context is android.app.Activity) {
            val activity = context as android.app.Activity
            activity.runOnUiThread {
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Sets the full screen content callback for the rewarded ad.
     *
     * @param callback The callback to set
     */
    fun setFullScreenContentCallback(callback: FullScreenContentCallback) {
        rewardedAd?.fullScreenContentCallback = callback
    }
    
    /**
     * Shows the rewarded ad if it's loaded.
     *
     * @param activity The activity to show the ad on
     * @param rewardCallback Callback to be notified when the user earns a reward
     * @return true if the ad was shown, false otherwise
     */
    fun showRewardedAd(activity: android.app.Activity, rewardCallback: OnUserEarnedRewardListener): Boolean {
        val ad = rewardedAd ?: return false
        
        // Check if user can use rewarded ad (once per month)
        val userManager = UserManager.getInstance()
        if (!userManager.canUseRewardedAd()) {
            showToast("You can only use one rewarded ad per month. Try again next month or upgrade to Pro for unlimited access.")
            return false
        }
        
        try {
            ad.show(activity, rewardCallback)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error showing rewarded ad", e)
            return false
        }
    }
    
    /**
     * Checks if a rewarded ad is currently loaded.
     *
     * @return true if an ad is loaded, false otherwise
     */
    fun isAdLoaded(): Boolean = rewardedAd != null
    
    /**
     * Clean up resources when the manager is no longer needed
     */
    fun destroy() {
        rewardedAd = null
        // Note: We don't cancel the coroutineScope here as it's tied to the lifecycle of this instance
    }
}