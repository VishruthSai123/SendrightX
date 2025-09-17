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
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Manager class for handling interstitial advertisements.
 * Provides methods for loading and displaying full-screen interstitial ads.
 */
class InterstitialAdManager(private val context: Context) {
    private var interstitialAd: InterstitialAd? = null
    private var adUnitId: String = TEST_INTERSTITIAL_AD_UNIT_ID
    private var adLoadCallback: ((InterstitialAd?) -> Unit)? = null
    
    companion object {
        private const val TAG = "InterstitialAdManager"
        // Test ad unit ID for interstitial ads - replace with your own for production
        const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    }
    
    /**
     * Sets the ad unit ID to use for loading interstitial ads.
     *
     * @param adUnitId The ad unit ID to use
     */
    fun setAdUnitId(adUnitId: String) {
        this.adUnitId = adUnitId
    }
    
    /**
     * Loads an interstitial ad asynchronously.
     *
     * @param callback Optional callback to be notified when the ad is loaded or fails to load
     */
    fun loadInterstitialAd(callback: ((InterstitialAd?) -> Unit)? = null) {
        adLoadCallback = callback
        
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(context, adUnitId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d(TAG, "Interstitial ad loaded")
                this@InterstitialAdManager.interstitialAd = interstitialAd
                
                // Set the full screen content callback
                interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Interstitial ad dismissed")
                        this@InterstitialAdManager.interstitialAd = null
                    }
                    
                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.e(TAG, "Interstitial ad failed to show: ${adError.message}")
                        this@InterstitialAdManager.interstitialAd = null
                    }
                    
                    override fun onAdShowedFullScreenContent() {
                        Log.d(TAG, "Interstitial ad showed")
                    }
                }
                
                adLoadCallback?.invoke(interstitialAd)
            }
            
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e(TAG, "Interstitial ad failed to load: ${adError.message}")
                interstitialAd = null
                adLoadCallback?.invoke(null)
            }
        })
    }
    
    /**
     * Shows the interstitial ad if it's loaded.
     *
     * @param onAdClosed Optional callback to be notified when the ad is closed
     * @return true if the ad was shown, false otherwise
     */
    fun showInterstitialAd(onAdClosed: (() -> Unit)? = null): Boolean {
        val ad = interstitialAd ?: return false
        
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial ad dismissed")
                this@InterstitialAdManager.interstitialAd = null
                onAdClosed?.invoke()
            }
            
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Interstitial ad failed to show: ${adError.message}")
                this@InterstitialAdManager.interstitialAd = null
                onAdClosed?.invoke()
            }
            
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial ad showed")
            }
        }
        
        ad.show(context as android.app.Activity)
        return true
    }
    
    /**
     * Checks if an interstitial ad is currently loaded.
     *
     * @return true if an ad is loaded, false otherwise
     */
    fun isAdLoaded(): Boolean = interstitialAd != null
}