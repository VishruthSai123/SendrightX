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
import android.view.ViewGroup
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Manager class for handling banner advertisements.
 * Provides methods for creating and managing banner ads with proper lifecycle management.
 */
class BannerAdManager(private val context: Context) {
    private var adView: AdView? = null
    
    companion object {
        private const val TAG = "BannerAdManager"
        // Test ad unit ID for banner ads - replace with your own for production
        const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    }
    
    /**
     * Creates a banner ad view with the specified ad unit ID.
     *
     * @param adUnitId The ad unit ID to use, or null to use the test ad unit ID
     * @return The created AdView instance
     */
    fun createBannerAd(adUnitId: String? = null): AdView {
        adView = AdView(context).apply {
            setAdSize(AdSize.BANNER)
            this.adUnitId = adUnitId ?: TEST_BANNER_AD_UNIT_ID
        }
        
        // Load an ad into the AdView
        val adRequest = AdRequest.Builder().build()
        adView?.loadAd(adRequest)
        
        return adView!!
    }
    
    /**
     * Adds a banner ad to the specified view group.
     *
     * @param parent The parent view group to add the banner ad to
     * @param adUnitId The ad unit ID to use, or null to use the test ad unit ID
     * @return The created AdView instance
     */
    fun addBannerAdToView(parent: ViewGroup, adUnitId: String? = null): AdView {
        val adView = createBannerAd(adUnitId)
        parent.addView(adView)
        return adView
    }
    
    /**
     * Resumes the banner ad (should be called from the Activity's onResume method).
     */
    fun resume() {
        adView?.resume()
    }
    
    /**
     * Pauses the banner ad (should be called from the Activity's onPause method).
     */
    fun pause() {
        adView?.pause()
    }
    
    /**
     * Destroys the banner ad (should be called from the Activity's onDestroy method).
     */
    fun destroy() {
        adView?.destroy()
        adView = null
    }
}