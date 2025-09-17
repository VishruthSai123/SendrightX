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

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.vishruth.key1.R
import com.vishruth.key1.lib.ads.AdManager
import com.vishruth.key1.lib.ads.BannerAdManager
import com.vishruth.key1.lib.ads.InterstitialAdManager

/**
 * Test activity for verifying ad implementation.
 * This activity can be used to test banner and interstitial ads.
 */
class AdTestActivity : ComponentActivity() {
    private lateinit var bannerAdManager: BannerAdManager
    private lateinit var interstitialAdManager: InterstitialAdManager
    private lateinit var bannerContainer: FrameLayout
    
    companion object {
        private const val TAG = "AdTestActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ad_test)
        
        // Initialize the banner and interstitial ad managers
        bannerAdManager = BannerAdManager(this)
        interstitialAdManager = InterstitialAdManager(this)
        
        // Find views
        bannerContainer = findViewById(R.id.banner_container)
        val loadInterstitialButton: Button = findViewById(R.id.load_interstitial_button)
        val showInterstitialButton: Button = findViewById(R.id.show_interstitial_button)
        
        // Add banner ad to the container
        bannerAdManager.addBannerAdToView(bannerContainer)
        
        // Set up interstitial ad buttons
        loadInterstitialButton.setOnClickListener {
            loadInterstitialAd()
        }
        
        showInterstitialButton.setOnClickListener {
            showInterstitialAd()
        }
        
        // Initialize AdMob SDK if not already initialized
        if (!AdManager.isInitialized()) {
            AdManager.initialize(this)
        }
    }
    
    private fun loadInterstitialAd() {
        interstitialAdManager.loadInterstitialAd { interstitialAd ->
            if (interstitialAd != null) {
                Log.d(TAG, "Interstitial ad loaded successfully")
                Toast.makeText(this, "Interstitial ad loaded", Toast.LENGTH_SHORT).show()
            } else {
                Log.d(TAG, "Failed to load interstitial ad")
                Toast.makeText(this, "Failed to load interstitial ad", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showInterstitialAd() {
        val shown = interstitialAdManager.showInterstitialAd {
            Log.d(TAG, "Interstitial ad closed")
            Toast.makeText(this, "Interstitial ad closed", Toast.LENGTH_SHORT).show()
        }
        
        if (!shown) {
            Toast.makeText(this, "Interstitial ad not loaded yet", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        bannerAdManager.resume()
    }
    
    override fun onPause() {
        super.onPause()
        bannerAdManager.pause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bannerAdManager.destroy()
    }
}