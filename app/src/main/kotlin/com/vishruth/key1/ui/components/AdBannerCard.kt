/*
 * Copyright (C) 2025 SendRight 4.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import android.content.res.Configuration
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
import com.vishruth.key1.app.FlorisPreferenceStore
import com.vishruth.key1.lib.util.TimeUtils.javaLocalTime
import dev.patrickgold.jetpref.datastore.model.observeAsState
import com.vishruth.key1.app.FlorisPreferenceStore
import com.vishruth.key1.ime.theme.ThemeMode
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.model.LocalTime

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
    
    // Get subscription status from multiple sources
    val userData by userManager.userData.collectAsState()
    val subscriptionManager = remember { userManager.getSubscriptionManager() }
    val isProFromSubscriptionManager = remember { mutableStateOf(false) }
    
    // Observe subscription manager's isPro StateFlow if available
    LaunchedEffect(subscriptionManager) {
        subscriptionManager?.isPro?.collect { isPro ->
            isProFromSubscriptionManager.value = isPro
        }
    }
    
    // Determine if user is pro from multiple sources (matching the pattern used in TextInputLayout)
    val isPro = userData?.subscriptionStatus == "pro" || 
                userData?.subscriptionStatus == "premium" ||  // Also check for "premium" status
                isProFromSubscriptionManager.value
    
    // Get keyboard theme preferences
    val prefs by FlorisPreferenceStore
    val themeMode by prefs.theme.mode.observeAsState()
    val sunriseTime by prefs.theme.sunriseTime.observeAsState()
    val sunsetTime by prefs.theme.sunsetTime.observeAsState()
    
    // Determine if current keyboard theme is "day" (light) or "night" (dark)
    val isKeyboardDayTheme = remember(themeMode, sunriseTime, sunsetTime) {
        when (themeMode) {
            com.vishruth.key1.ime.theme.ThemeMode.ALWAYS_DAY -> true
            com.vishruth.key1.ime.theme.ThemeMode.ALWAYS_NIGHT -> false
            com.vishruth.key1.ime.theme.ThemeMode.FOLLOW_SYSTEM -> {
                val uiMode = context.resources.configuration.uiMode
                (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) != android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            com.vishruth.key1.ime.theme.ThemeMode.FOLLOW_TIME -> {
                val current = java.time.LocalTime.now()
                val sunrise = sunriseTime.javaLocalTime
                val sunset = sunsetTime.javaLocalTime
                current in sunrise..sunset
            }
        }
    }
    
    // Keyboard theme-based background colors
    val cardBackgroundColor = if (isKeyboardDayTheme) {
        Color.White // Pure white for day theme
    } else {
        Color(0xFF1E1E1E) // Light black for night theme
    }
    
    // Don't show ads for pro users
    if (isPro) {
        Log.d("AdBannerCard", "🚫 Hiding ad banner - user is pro (subscriptionStatus: ${userData?.subscriptionStatus}, subscriptionManagerPro: ${isProFromSubscriptionManager.value})")
        return
    }
    
    // Log when showing ads for non-pro users
    Log.d("AdBannerCard", "📢 Showing ad banner - user is not pro (subscriptionStatus: ${userData?.subscriptionStatus}, subscriptionManagerPro: ${isProFromSubscriptionManager.value})")
    
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
    
    // Show the card only when successfully loaded (hide during loading and error)
    AnimatedVisibility(
        visible = isAdLoaded && !hasAdError,
        enter = fadeIn(animationSpec = tween(600)) + slideInVertically(
            animationSpec = tween(600),
            initialOffsetY = { it / 4 }
        )
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardBackgroundColor // Keyboard theme-aware background
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header Area with Ad label only
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ad Label (left side)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text(
                            text = "Ad",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Native Ad content
                AndroidView(
                    factory = { context ->
                        NativeAdView(context).apply {
                                // Create main vertical layout for native ad
                                val mainLayout = android.widget.LinearLayout(context).apply {
                                    orientation = android.widget.LinearLayout.VERTICAL
                                    setPadding(0, 0, 0, 0) // Remove padding as it's handled by Card
                                }
                                    
                                    // Media Area - takes up majority of the card
                                    if (nativeAd!!.mediaContent != null) {
                                        val mediaContent = nativeAd!!.mediaContent!!
                                        
                                        try {
                                            // Calculate optimal media dimensions
                                            val displayMetrics = context.resources.displayMetrics
                                            val screenWidth = displayMetrics.widthPixels
                                            val cardPadding = 64 // Account for card padding/margins
                                            val availableWidth = screenWidth - cardPadding
                                            
                                            // Use 16:9 aspect ratio for consistent media area
                                            val mediaHeight = (availableWidth * 0.5625).toInt().coerceIn(
                                                180, // Minimum height for good visibility
                                                (displayMetrics.heightPixels * 0.25).toInt() // Max 25% of screen
                                            )
                                            
                                            // Create media container with rounded corners
                                            val mediaContainer = android.widget.FrameLayout(context).apply {
                                                layoutParams = android.widget.LinearLayout.LayoutParams(
                                                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                                    mediaHeight
                                                ).apply {
                                                    bottomMargin = 16
                                                }
                                                // Add rounded corner background
                                                val drawable = android.graphics.drawable.GradientDrawable()
                                                drawable.cornerRadius = 12f
                                                drawable.setColor(android.graphics.Color.parseColor("#F8F9FA"))
                                                background = drawable
                                                clipToOutline = true
                                            }
                                            
                                            val mediaView = MediaView(context).apply {
                                                layoutParams = android.widget.FrameLayout.LayoutParams(
                                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                                )
                                                // Style media view with rounded corners
                                                val mediaDrawable = android.graphics.drawable.GradientDrawable()
                                                mediaDrawable.cornerRadius = 12f
                                                mediaDrawable.setColor(android.graphics.Color.TRANSPARENT)
                                                background = mediaDrawable
                                                clipToOutline = true
                                            }
                                            
                                            mediaContainer.addView(mediaView)
                                            
                                            // Add legal disclaimer overlay at bottom of media
                                            val disclaimerView = android.widget.TextView(context).apply {
                                                text = "Investments in securities market are subject to market risks; read the related documents carefully before investing."
                                                textSize = 8f
                                                setTextColor(android.graphics.Color.parseColor("#888888"))
                                                maxLines = 2
                                                ellipsize = android.text.TextUtils.TruncateAt.END
                                                setPadding(8, 4, 8, 4)
                                                val disclaimerBackground = android.graphics.drawable.GradientDrawable()
                                                disclaimerBackground.setColor(android.graphics.Color.parseColor("#E0FFFFFF"))
                                                disclaimerBackground.cornerRadius = 6f
                                                background = disclaimerBackground
                                                layoutParams = android.widget.FrameLayout.LayoutParams(
                                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                                                ).apply {
                                                    gravity = android.view.Gravity.BOTTOM
                                                    setMargins(8, 0, 8, 8)
                                                }
                                            }
                                            
                                            mediaContainer.addView(disclaimerView)
                                            mainLayout.addView(mediaContainer)
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
                                    
                                    // Text + CTA Area (bottom section)
                                    val bottomContainer = android.widget.LinearLayout(context).apply {
                                        orientation = android.widget.LinearLayout.VERTICAL
                                        layoutParams = android.widget.LinearLayout.LayoutParams(
                                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                        )
                                        setPadding(0, 8, 0, 0)
                                    }
                                    
                                    // App Title + Description (full width)
                                    val textContainer = android.widget.LinearLayout(context).apply {
                                        orientation = android.widget.LinearLayout.VERTICAL
                                        layoutParams = android.widget.LinearLayout.LayoutParams(
                                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                        )
                                    }
                                    
                                    // Add headline (App Title)
                                    nativeAd!!.headline?.let { headline ->
                                        val headlineView = android.widget.TextView(context).apply {
                                            text = headline
                                            textSize = 16f
                                            setTextColor(android.graphics.Color.parseColor("#1F1F1F")) // Theme-aware text color
                                            maxLines = 1
                                            ellipsize = android.text.TextUtils.TruncateAt.END
                                            setTypeface(null, android.graphics.Typeface.BOLD)
                                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                            ).apply {
                                                bottomMargin = 4
                                            }
                                        }
                                        textContainer.addView(headlineView)
                                        setHeadlineView(headlineView)
                                    }
                                    
                                    // Add body text (Description)
                                    nativeAd!!.body?.let { body ->
                                        val bodyView = android.widget.TextView(context).apply {
                                            text = body
                                            textSize = 14f
                                            setTextColor(android.graphics.Color.parseColor("#666666"))
                                            maxLines = 2
                                            ellipsize = android.text.TextUtils.TruncateAt.END
                                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                            ).apply {
                                                bottomMargin = 8
                                            }
                                        }
                                        textContainer.addView(bodyView)
                                        setBodyView(bodyView)
                                    }
                                    
                                    bottomContainer.addView(textContainer)
                                    
                                    // CTA Text (positioned at bottom right)
                                    nativeAd!!.callToAction?.let { cta ->
                                        val ctaContainer = android.widget.LinearLayout(context).apply {
                                            orientation = android.widget.LinearLayout.HORIZONTAL
                                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                            )
                                            gravity = android.view.Gravity.END
                                        }
                                        
                                        val ctaView = android.widget.TextView(context).apply {
                                            text = "$cta "
                                            textSize = 14f
                                            setTextColor(android.graphics.Color.parseColor("#007AFF")) // Light blue color
                                            isAllCaps = false
                                            setTypeface(null, android.graphics.Typeface.BOLD) // Bold text
                                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                            )
                                            // Remove any background
                                            background = null
                                            setPadding(0, 8, 0, 0)
                                        }
                                        
                                        // Bold arrow as separate TextView for emphasis
                                        val arrowView = android.widget.TextView(context).apply {
                                            text = "→"
                                            textSize = 16f // Slightly larger for emphasis
                                            setTextColor(android.graphics.Color.parseColor("#007AFF"))
                                            setTypeface(null, android.graphics.Typeface.BOLD) // Bold arrow
                                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                            )
                                            background = null
                                            setPadding(0, 8, 0, 0)
                                        }
                                        
                                        ctaContainer.addView(ctaView)
                                        ctaContainer.addView(arrowView)
                                        bottomContainer.addView(ctaContainer)
                                        setCallToActionView(ctaContainer)
                                    }
                                    mainLayout.addView(bottomContainer)
                                    addView(mainLayout)
                                    setNativeAd(nativeAd!!)
                                }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
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