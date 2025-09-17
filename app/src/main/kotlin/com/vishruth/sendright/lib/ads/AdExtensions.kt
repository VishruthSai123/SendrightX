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

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.interstitial.InterstitialAd

/**
 * Extension functions for easier ad integration.
 */

/**
 * Adds a banner ad to the specified view group.
 *
 * @param parent The parent view group to add the banner ad to
 * @param adUnitId The ad unit ID to use, or null to use the test ad unit ID
 * @return The created AdView instance
 */
fun Context.addBannerAd(parent: ViewGroup, adUnitId: String? = null): AdView {
    val bannerAdManager = BannerAdManager(this)
    return bannerAdManager.addBannerAdToView(parent, adUnitId)
}

/**
 * Adds a banner ad to the specified view.
 *
 * @param view The view to add the banner ad to (must be a ViewGroup)
 * @param adUnitId The ad unit ID to use, or null to use the test ad unit ID
 * @return The created AdView instance, or null if the view is not a ViewGroup
 */
fun View.addBannerAd(adUnitId: String? = null): AdView? {
    if (this is ViewGroup) {
        val bannerAdManager = BannerAdManager(this.context)
        return bannerAdManager.addBannerAdToView(this, adUnitId)
    }
    return null
}

/**
 * Loads an interstitial ad.
 *
 * @param adUnitId The ad unit ID to use, or null to use the test ad unit ID
 * @param callback Optional callback to be notified when the ad is loaded or fails to load
 * @return The InterstitialAdManager instance
 */
fun Context.loadInterstitialAd(adUnitId: String? = null, callback: ((InterstitialAd?) -> Unit)? = null): InterstitialAdManager {
    val interstitialAdManager = InterstitialAdManager(this)
    adUnitId?.let { interstitialAdManager.setAdUnitId(it) }
    interstitialAdManager.loadInterstitialAd(callback)
    return interstitialAdManager
}

/**
 * Shows an interstitial ad.
 *
 * @param interstitialAdManager The InterstitialAdManager instance
 * @param onAdClosed Optional callback to be notified when the ad is closed
 * @return true if the ad was shown, false otherwise
 */
fun Activity.showInterstitialAd(interstitialAdManager: InterstitialAdManager, onAdClosed: (() -> Unit)? = null): Boolean {
    return interstitialAdManager.showInterstitialAd(onAdClosed)
}

/**
 * Loads and shows an interstitial ad.
 *
 * @param adUnitId The ad unit ID to use, or null to use the test ad unit ID
 * @param onAdClosed Optional callback to be notified when the ad is closed
 */
fun Fragment.loadAndShowInterstitialAd(adUnitId: String? = null, onAdClosed: (() -> Unit)? = null) {
    val interstitialAdManager = InterstitialAdManager(requireContext())
    adUnitId?.let { interstitialAdManager.setAdUnitId(it) }
    
    interstitialAdManager.loadInterstitialAd { interstitialAd ->
        if (interstitialAd != null) {
            interstitialAdManager.showInterstitialAd(onAdClosed)
        }
    }
}