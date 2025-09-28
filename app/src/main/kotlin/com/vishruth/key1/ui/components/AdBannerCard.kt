/*
 * Copyright (C) 2025 SendRight 4.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.widget.ImageView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.VideoOptions
import com.vishruth.key1.user.UserManager

/**
 * Reusable banner ad card component that matches app's organic card styling
 */
@Composable
fun AdBannerCard(
    modifier: Modifier = Modifier,
    onAdLoaded: () -> Unit = {},
    onAdFailedToLoad: (LoadAdError) -> Unit = {}
) {
    val context = LocalContext.current
    val userManager = remember { UserManager.getInstance() }
    
    // Get subscription status
    val userData by userManager.userData.collectAsState()
    val isPro = userData?.subscriptionStatus == "pro"
    
    // Don't show ads for pro users
    if (isPro) {
        return
    }
    
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isAdLoaded by remember { mutableStateOf(false) }
    var hasAdError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasAttemptedLoad by remember { mutableStateOf(false) }
    
    // Always use production ad unit ID
    val actualAdUnitId = "ca-app-pub-1496070957048863/5853942656"
    
    LaunchedEffect(actualAdUnitId) {
        // Only load ad once, don't reload on recomposition/scroll
        if (hasAttemptedLoad) {
            Log.d("AdBannerCard", "⏭️ Ad already attempted for unit: $actualAdUnitId, skipping reload")
            return@LaunchedEffect
        }
        
        try {
            Log.d("AdBannerCard", "🚀 Initializing native ad for production unit: $actualAdUnitId")
            hasAttemptedLoad = true
            isLoading = true
            hasAdError = false
            errorMessage = null
            
            // Ensure AdMob SDK is initialized
            val adManager = com.vishruth.key1.lib.ads.AdManager
            if (!adManager.isInitialized()) {
                adManager.ensureInitialized(context)
                
                // Wait for initialization with timeout
                val initialized = adManager.waitForInitialization(10000) // 10 second timeout
                if (!initialized) {
                    hasAdError = true
                    isLoading = false
                    errorMessage = "AdMob initialization timeout"
                    return@LaunchedEffect
                }
            }
            
            // Initialize Native Ad Loader
            val adLoader = AdLoader.Builder(context, actualAdUnitId)
                .forNativeAd { loadedNativeAd ->
                    Log.d("AdBannerCard", "✅ Native ad loaded successfully for unit: $actualAdUnitId")
                    nativeAd = loadedNativeAd
                    isAdLoaded = true
                    hasAdError = false
                    isLoading = false
                    errorMessage = null
                    onAdLoaded()
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        super.onAdFailedToLoad(error)
                        val errorExplanation = when (error.code) {
                            3 -> "No Fill - No ads available to serve at this time"
                            0 -> "Internal Error - AdMob internal issue"
                            1 -> "Invalid Request - Check ad unit ID and configuration"
                            2 -> "Network Error - Check internet connection"
                            else -> "Unknown error code: ${error.code}"
                        }
                        
                        Log.e("AdBannerCard", "❌ Native ad failed to load for unit $actualAdUnitId")
                        Log.e("AdBannerCard", "Error: ${error.message} (Code: ${error.code})")
                        Log.e("AdBannerCard", "Explanation: $errorExplanation")
                        Log.e("AdBannerCard", "Domain: ${error.domain}, Cause: ${error.cause}")
                        
                        hasAdError = true
                        isAdLoaded = false
                        isLoading = false
                        errorMessage = if (error.code == 3) {
                            "No ads available (normal for new ad units)"
                        } else {
                            "$errorExplanation (${error.code})"
                        }
                        onAdFailedToLoad(error)
                    }
                    
                    override fun onAdClicked() {
                        super.onAdClicked()
                        Log.d("AdBannerCard", "🖱️ Ad clicked for unit: $actualAdUnitId")
                    }
                    
                    override fun onAdOpened() {
                        super.onAdOpened()
                        Log.d("AdBannerCard", "📱 Ad opened for unit: $actualAdUnitId")
                    }
                    
                    override fun onAdClosed() {
                        super.onAdClosed()
                        Log.d("AdBannerCard", "❌ Ad closed for unit: $actualAdUnitId")
                    }
                    
                    override fun onAdImpression() {
                        super.onAdImpression()
                        Log.d("AdBannerCard", "👁️ Ad impression recorded for unit: $actualAdUnitId")
                    }
                })
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .setRequestMultipleImages(false) // Disable multiple images to avoid loading issues
                        .setReturnUrlsForImageAssets(false) // Use drawable objects for better compatibility
                        .setVideoOptions(
                            com.google.android.gms.ads.VideoOptions.Builder()
                                .setStartMuted(true) // Start videos muted for better UX
                                .setClickToExpandRequested(false) // Prevent unexpected expand behavior
                                .build()
                        )
                        .build()
                )
                .build()
            
            // Load native ad request
            val adRequest = AdRequest.Builder()
                .build()
            
            Log.d("AdBannerCard", "🔄 Loading native ad for unit: $actualAdUnitId")
            adLoader.loadAd(adRequest)
            
        } catch (e: Exception) {
            Log.e("AdBannerCard", "💥 Exception while initializing ad for unit $actualAdUnitId", e)
            hasAdError = true
            isLoading = false
            errorMessage = "Exception: ${e.message}"
        }
    }
    
    // Optional: Add retry capability after a delay for failed ads
    LaunchedEffect(hasAdError) {
        if (hasAdError && errorMessage?.contains("No ads available") == true) {
            Log.d("AdBannerCard", "🔄 Ad failed with 'no fill', will retry after delay")
            // Optionally retry after 30 seconds for no-fill errors
            // Uncomment below to enable retry:
            // delay(30000)
            // hasAttemptedLoad = false
        }
    }
    
    // Cleanup when component is disposed
    DisposableEffect(actualAdUnitId) {
        onDispose {
            try {
                nativeAd?.destroy()
            } catch (e: Exception) {
                android.util.Log.e("AdBannerCard", "Error disposing native ad", e)
            }
        }
    }
    
    // Show the card only if loading or successfully loaded
    // Hide completely if there's an error (better UX than showing error messages)
    val shouldShowCard = (isLoading || isAdLoaded) && !hasAdError
    
    if (shouldShowCard) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Sponsored tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Sponsored",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Normal
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Native Ad content
                when {
                    isAdLoaded && nativeAd != null -> {
                        AndroidView(
                            factory = { context ->
                                NativeAdView(context).apply {
                                    // Create main vertical layout for native ad
                                    val mainLayout = android.widget.LinearLayout(context).apply {
                                        orientation = android.widget.LinearLayout.VERTICAL
                                        setPadding(16, 8, 16, 8)
                                    }
                                    
                                    // Add MediaView for images/videos with improved error handling
                                    if (nativeAd!!.mediaContent != null) {
                                        val mediaContent = nativeAd!!.mediaContent!!
                                        
                                        try {
                                            // Calculate safe aspect ratio with better bounds checking
                                            val displayMetrics = context.resources.displayMetrics
                                            val screenWidth = displayMetrics.widthPixels
                                            val cardPadding = 80 // Account for card padding/margins
                                            val availableWidth = screenWidth - cardPadding
                                            
                                            // Use more conservative aspect ratio calculation
                                            val aspectRatio = mediaContent.aspectRatio
                                            val mediaHeight = when {
                                                aspectRatio > 0.1f && aspectRatio < 10f -> {
                                                    // Safe aspect ratio range - calculate natural height
                                                    val naturalHeight = (availableWidth / aspectRatio).toInt()
                                                    naturalHeight.coerceIn(
                                                        120, // Reasonable minimum for visibility
                                                        (displayMetrics.heightPixels * 0.3).toInt() // Max 30% of screen
                                                    )
                                                }
                                                mediaContent.hasVideoContent() -> {
                                                    // Video content - use 16:9 aspect ratio as default
                                                    (availableWidth * 0.5625).toInt().coerceIn(
                                                        150,
                                                        (displayMetrics.heightPixels * 0.25).toInt()
                                                    )
                                                }
                                                else -> {
                                                    // Image content - use moderate height
                                                    (availableWidth * 0.6).toInt().coerceIn(
                                                        120,
                                                        (displayMetrics.heightPixels * 0.2).toInt()
                                                    )
                                                }
                                            }
                                            
                                            val mediaView = MediaView(context).apply {
                                                layoutParams = android.widget.LinearLayout.LayoutParams(
                                                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                                    mediaHeight
                                                ).apply {
                                                    bottomMargin = 12
                                                }
                                                // Add background for better loading visibility
                                                setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
                                            }
                                            
                                            mainLayout.addView(mediaView)
                                            setMediaView(mediaView)
                                        } catch (e: Exception) {
                                            // If MediaView creation fails, continue without media
                                            android.util.Log.e("AdBannerCard", "Failed to create MediaView", e)
                                        }
                                    }
                                    
                                    // Add icon if available (only if no media content)
                                    if (nativeAd!!.icon != null && nativeAd!!.mediaContent == null) {
                                        try {
                                            val iconSize = 56 // Slightly larger for better visibility
                                            val iconView = ImageView(context).apply {
                                                layoutParams = android.widget.LinearLayout.LayoutParams(iconSize, iconSize).apply {
                                                    bottomMargin = 12
                                                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                                                }
                                                scaleType = ImageView.ScaleType.CENTER_CROP
                                                // Add rounded corners with background
                                                val drawable = android.graphics.drawable.GradientDrawable()
                                                drawable.cornerRadius = 8f
                                                drawable.setColor(android.graphics.Color.parseColor("#F0F0F0"))
                                                background = drawable
                                                clipToOutline = true
                                                // Add padding for better appearance
                                                setPadding(4, 4, 4, 4)
                                            }
                                            mainLayout.addView(iconView)
                                            setIconView(iconView)
                                        } catch (e: Exception) {
                                            // If icon creation fails, continue without icon
                                            android.util.Log.e("AdBannerCard", "Failed to create icon view", e)
                                        }
                                    }
                                    
                                    // Add text content vertically
                                    val textContainer = android.widget.LinearLayout(context).apply {
                                        orientation = android.widget.LinearLayout.VERTICAL
                                        layoutParams = android.widget.LinearLayout.LayoutParams(
                                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                        ).apply {
                                            bottomMargin = 12
                                        }
                                    }
                                    
                                    // Add headline with adaptive sizing
                                    nativeAd!!.headline?.let { headline ->
                                        val headlineView = android.widget.TextView(context).apply {
                                            text = headline
                                            textSize = if (headline.length > 30) 14f else 16f // Smaller text for longer headlines
                                            setTextColor(android.graphics.Color.BLACK)
                                            maxLines = if (nativeAd!!.body != null) 1 else 2 // Less lines if body text exists
                                            ellipsize = android.text.TextUtils.TruncateAt.END
                                            setTypeface(null, android.graphics.Typeface.BOLD)
                                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                            ).apply {
                                                bottomMargin = if (nativeAd!!.body != null) 4 else 0
                                            }
                                        }
                                        textContainer.addView(headlineView)
                                        setHeadlineView(headlineView)
                                    }
                                    
                                    // Add body text if available with flexible sizing
                                    nativeAd!!.body?.let { body ->
                                        val bodyView = android.widget.TextView(context).apply {
                                            text = body
                                            textSize = 12f
                                            setTextColor(android.graphics.Color.parseColor("#666666"))
                                            maxLines = if (nativeAd!!.headline != null) 2 else 3 // More lines if no headline
                                            ellipsize = android.text.TextUtils.TruncateAt.END
                                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                            )
                                        }
                                        textContainer.addView(bodyView)
                                        setBodyView(bodyView)
                                    }
                                    
                                    mainLayout.addView(textContainer)
                                    
                                    // Add full-width call to action button under text
                                    nativeAd!!.callToAction?.let { cta ->
                                        val ctaView = android.widget.Button(context).apply {
                                            text = cta
                                            textSize = 14f // Slightly larger for full-width button
                                            setPadding(24, 16, 24, 16) // More padding for full-width design
                                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, // Full width
                                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                            ).apply {
                                                topMargin = 4 // Space between text and button
                                            }
                                            // Style the button with 45% rounded corners
                                            setBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
                                            setTextColor(android.graphics.Color.WHITE)
                                            isAllCaps = false
                                            setTypeface(null, android.graphics.Typeface.BOLD)
                                            
                                            // Calculate 45% rounded corners based on button height
                                            post {
                                                val buttonHeight = height.toFloat()
                                                val cornerRadius = buttonHeight * 0.45f // 45% of button height
                                                val drawable = android.graphics.drawable.GradientDrawable()
                                                drawable.cornerRadius = cornerRadius
                                                drawable.setColor(android.graphics.Color.parseColor("#2196F3"))
                                                background = drawable
                                            }
                                        }
                                        mainLayout.addView(ctaView)
                                        setCallToActionView(ctaView)
                                    }
                                    addView(mainLayout)
                                    setNativeAd(nativeAd!!)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    isLoading -> {
                        // Loading state
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF23C546) // Green color matching app's loader theme
                                )
                                if (com.vishruth.key1.BuildConfig.DEBUG) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Loading ad...",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }

                }
            }
        }
    }
}

/**
 * Native ad card component with custom styling (for future use)
 */
@Composable
fun NativeAdBannerCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    ctaText: String,
    deepLink: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = {
            onClick()
            handleAdClick(context, deepLink)
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Sponsored tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Sponsored",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ad image placeholder (will be replaced with actual image loading when needed)
                if (!imageUrl.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🖼️",
                            fontSize = 24.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                }
                
                // Ad content
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // CTA Button
                Button(
                    onClick = {
                        onClick()
                        handleAdClick(context, deepLink)
                    },
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = ctaText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Handle ad click - open deep link in app or external browser
 */
private fun handleAdClick(context: Context, deepLink: String?) {
    if (deepLink.isNullOrEmpty()) return
    
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
        
        // Try to open in app if it's a supported deep link
        if (deepLink.startsWith("ui://sendrightx")) {
            // Internal app deep link
            intent.setPackage(context.packageName)
        } else {
            // External link - open in browser
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        context.startActivity(intent)
        Log.d("AdBannerCard", "Opened deep link: $deepLink")
    } catch (e: Exception) {
        Log.e("AdBannerCard", "Failed to open deep link: $deepLink", e)
    }
}

/**
 * Composable preview data for native ads
 */
data class AdData(
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val ctaText: String,
    val deepLink: String?
)

// Sample ad data for testing
val sampleAdData = AdData(
    title = "Special Offer!",
    subtitle = "Get 50% off on premium features. Limited time offer.",
    imageUrl = null,
    ctaText = "Claim Now",
    deepLink = "ui://sendrightx/subscription"
)