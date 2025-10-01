/*
 * Copyright (C) 2025 SendRight 4.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.vishruth.key1.user.UserManager
import kotlinx.coroutines.delay

/**
 * Invisible component that preloads banner ads when panels are opened
 * This ensures ads are ready to display regardless of scroll position
 */
@Composable
fun AdPreloader(
    panelName: String,
    onAdPreloaded: (NativeAd) -> Unit = {},
    onPreloadFailed: (LoadAdError) -> Unit = {}
) {
    val context = LocalContext.current
    val userManager = remember { UserManager.getInstance() }
    
    // Get subscription status
    val userData by userManager.userData.collectAsState()
    val subscriptionManager = userManager.getSubscriptionManager()
    val isPro by subscriptionManager?.isPro?.collectAsState() ?: remember { mutableStateOf(false) }
    val isProUser = isPro || userData?.subscriptionStatus == "pro"
    
    // State for tracking preload attempt
    var hasPreloadAttempted by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        // Only preload for non-pro users
        if (isProUser || hasPreloadAttempted) {
            return@LaunchedEffect
        }
        
        hasPreloadAttempted = true
        
        try {
            Log.d("AdPreloader", "🚀 Starting ad preload for $panelName panel")
            
            // Small delay to ensure panel is fully initialized
            delay(100)
            
            preloadNativeAd(
                context = context,
                panelName = panelName,
                onSuccess = onAdPreloaded,
                onFailure = onPreloadFailed
            )
        } catch (e: Exception) {
            Log.e("AdPreloader", "Error during ad preload for $panelName", e)
        }
    }
}

/**
 * Preloads a native ad for the specified panel
 */
private fun preloadNativeAd(
    context: Context,
    panelName: String,
    onSuccess: (NativeAd) -> Unit,
    onFailure: (LoadAdError) -> Unit
) {
    val adUnitId = "ca-app-pub-1496070957048863/5853942656"
    
    try {
        // Ensure AdMob is initialized
        val adManager = com.vishruth.key1.lib.ads.AdManager
        if (!adManager.isInitialized()) {
            adManager.ensureInitialized(context)
        }
        
        // Create ad loader
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                Log.d("AdPreloader", "✅ Ad preloaded successfully for $panelName panel")
                onSuccess(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    super.onAdFailedToLoad(error)
                    Log.w("AdPreloader", "⚠️ Ad preload failed for $panelName: ${error.message}")
                    onFailure(error)
                }
            })
            .build()
        
        // Load the ad
        val adRequest = AdRequest.Builder().build()
        adLoader.loadAd(adRequest)
        
    } catch (e: Exception) {
        Log.e("AdPreloader", "Exception during ad preload for $panelName", e)
    }
}