/*
 * Copyright (C) 2025 SendRight 3.0
 * License    // Collect available products from billing manager
    val products by billingManager?.products?.collectAsState() 
        ?: re                    Icon(
                        imageVector = if (isUserPro) Icons.Default.CheckCircle else Icons.Default.Star,
                        contentDescription = "Pro Status",
                        modifier = Modifier.size(60.dp),
                        tint = if (isUserPro) MaterialTheme.colorScheme.onPrimary 
                              else MaterialTheme.colorScheme.onPrimaryContainer { mutableStateOf(emptyList()) }
    
    // Debug logging
    LaunchedEffect(products, isDiscountActive, isDiscountOfferAvailable) {
        android.util.Log.d("SubscriptionScreen", "Products loaded: ${products.size}")
        products.forEach { product ->
            android.util.Log.d("SubscriptionScreen", "Available product: ${product.productId}")
            product.subscriptionOfferDetails?.forEachIndexed { index, offer ->
                android.util.Log.d("SubscriptionScreen", "  Offer $index: basePlanId=${offer.basePlanId}, price=${offer.pricingPhases.pricingPhaseList.firstOrNull()?.formattedPrice}")
            }
        }
        android.util.Log.d("SubscriptionScreen", "Looking for product: ${com.vishruth.key1.billing.BillingManager.PRODUCT_ID_PRO_MONTHLY}")
        android.util.Log.d("SubscriptionScreen", "User is first-time subscriber: $isFirstTimeUser")
        android.util.Log.d("SubscriptionScreen", "User is pro: $isUserPro")
        android.util.Log.d("SubscriptionScreen", "Discount active: $isDiscountActive")
        android.util.Log.d("SubscriptionScreen", "Discount offer available: $isDiscountOfferAvailable")
        
        if (isDiscountActive && isDiscountOfferAvailable) {
            android.util.Log.d("SubscriptionScreen", "🎁 SHOWING DISCOUNT: 50% off promotional card and animation will be displayed")
        } else if (isFirstTimeUser && isUserPro) {
            android.util.Log.d("SubscriptionScreen", "User is first-time but already pro - no discount needed")
        } else if (!isFirstTimeUser) {
            android.util.Log.d("SubscriptionScreen", "User is not first-time subscriber - no discount")
        } else {
            android.util.Log.d("SubscriptionScreen", "Discount not available - offer not found in Google Play")
        }
    }
    
    // Get the monthly subscription product
    val monthlyProduct = products.find { it.productId == com.vishruth.key1.billing.BillingManager.PRODUCT_ID_PRO_MONTHLY }r the Apache License, Version 2.0
 */

package com.vishruth.key1.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color
import com.android.billingclient.api.ProductDetails
import com.vishruth.key1.user.UserManager
import com.vishruth.key1.ime.ai.AiUsageTracker
import com.vishruth.key1.ime.ai.AiUsageStats
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userManager = remember { UserManager.getInstance() }
    
    // Get billing and subscription managers
    val billingManager = userManager.getBillingManager()
    val subscriptionManager = userManager.getSubscriptionManager()
    
    // Get AI usage tracker for real-time usage stats
    val aiUsageTracker = remember { AiUsageTracker.getInstance() }
    val aiUsageStats by aiUsageTracker.usageStats.collectAsState()
    
    // Observe UserManager userData for subscription status
    val userData by userManager.userData.collectAsState()
    
    // Collect state from managers with safe defaults
    val isPro by subscriptionManager?.isPro?.collectAsState() 
        ?: remember { mutableStateOf(false) }
    
    // Use AI usage stats from AiUsageTracker for real-time updates
    val aiActionsUsed = aiUsageStats.dailyActionCount
    val remainingActions = aiUsageStats.remainingActions()
    
    // Determine if user is actually pro based on both sources
    val isUserPro = userData?.subscriptionStatus == "pro" || isPro
    
    // Check if user is eligible for first-time discount
    val isFirstTimeUser = remember { userManager.isFirstTimeSubscriber() }
    val isDiscountActive = isFirstTimeUser && !isUserPro
    
    // Show success message when user becomes pro
    LaunchedEffect(isUserPro) {
        if (isUserPro) {
            Toast.makeText(
                context,
                "🎉 Welcome to SendRight Premium! Unlimited AI features unlocked.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // Collect products from billing manager
    val products by billingManager?.products?.collectAsState() 
        ?: remember { mutableStateOf(emptyList()) }
    
    // Get the monthly subscription product
    val monthlyProduct = products.find { it.productId == com.vishruth.key1.billing.BillingManager.PRODUCT_ID_PRO_MONTHLY }
    
    // Validate if this is a real product or Google Play test data
    fun isRealProduct(product: ProductDetails?): Boolean {
        if (product == null) return false
        
        val firstOffer = product.subscriptionOfferDetails?.firstOrNull()
        val firstPhase = firstOffer?.pricingPhases?.pricingPhaseList?.firstOrNull()
        val price = firstPhase?.formattedPrice
        val period = firstPhase?.billingPeriod
        
        // Detect common Google Play test data patterns
        val isFakeProduct = when {
            price?.contains("5min") == true -> {
                android.util.Log.w("SubscriptionScreen", "❌ FAKE PRODUCT DETECTED: Price contains '5min' - $price")
                true
            }
            price?.contains("week") == true && period?.contains("P1M") == true -> {
                android.util.Log.w("SubscriptionScreen", "❌ FAKE PRODUCT DETECTED: Weekly price but monthly period - $price")
                true
            }
            product.title.contains("Test") || product.title.contains("test") -> {
                android.util.Log.w("SubscriptionScreen", "❌ FAKE PRODUCT DETECTED: Test in title - ${product.title}")
                true
            }
            product.description.contains("Test") || product.description.contains("test") -> {
                android.util.Log.w("SubscriptionScreen", "❌ FAKE PRODUCT DETECTED: Test in description - ${product.description}")
                true
            }
            else -> false
        }
        
        if (isFakeProduct) {
            android.util.Log.e("SubscriptionScreen", "🚫 REJECTING FAKE PRODUCT:")
            android.util.Log.e("SubscriptionScreen", "   Product ID: ${product.productId}")
            android.util.Log.e("SubscriptionScreen", "   Title: ${product.title}")
            android.util.Log.e("SubscriptionScreen", "   Price: $price")
            android.util.Log.e("SubscriptionScreen", "   Period: $period")
            android.util.Log.e("SubscriptionScreen", "💡 This means Google Play Console product 'sendright.pro.89' is not properly configured")
            return false
        }
        
        android.util.Log.d("SubscriptionScreen", "✅ REAL PRODUCT VALIDATED:")
        android.util.Log.d("SubscriptionScreen", "   Product ID: ${product.productId}")
        android.util.Log.d("SubscriptionScreen", "   Title: ${product.title}")
        android.util.Log.d("SubscriptionScreen", "   Price: $price")
        android.util.Log.d("SubscriptionScreen", "   Period: $period")
        return true
    }
    
    // Only use the product if it's real, not fake test data
    val validatedProduct = if (isRealProduct(monthlyProduct)) monthlyProduct else null
    
    // Find the appropriate offer based on user eligibility
    val (selectedOffer, isDiscountOfferAvailable) = remember(validatedProduct, isDiscountActive) {
        if (validatedProduct == null) return@remember Pair(null, false)
        
        if (isDiscountActive) {
            // Look for the discount offer with base plan ID "sendright-pro-45"
            val discountOffer = billingManager?.findOfferByBasePlanId(validatedProduct, "sendright-pro-45")
            if (discountOffer != null) {
                val discountPrice = discountOffer.pricingPhases.pricingPhaseList.firstOrNull()?.formattedPrice
                Log.d("SubscriptionScreen", "Found discount offer: sendright-pro-45 with price: $discountPrice")
                
                // Validate that the discount price indicates a real discount (₹45 or similar)
                val isPriceDiscounted = discountPrice?.let { price ->
                    price.contains("45") || price.contains("₹45") || price.contains("45.00") ||
                    price.contains("44") || price.contains("46") // Allow slight variations
                } ?: false
                
                if (isPriceDiscounted) {
                    Log.d("SubscriptionScreen", "✅ Valid discount price detected: $discountPrice")
                    Pair(discountOffer, true)
                } else {
                    Log.w("SubscriptionScreen", "❌ Discount offer found but price doesn't match expected discount: $discountPrice")
                    // Fallback to regular offer since discount isn't actually applied
                    Pair(validatedProduct.subscriptionOfferDetails?.firstOrNull(), false)
                }
            } else {
                Log.w("SubscriptionScreen", "Discount offer 'sendright-pro-45' not found, using regular offer")
                // Fallback to first offer
                Pair(validatedProduct.subscriptionOfferDetails?.firstOrNull(), false)
            }
        } else {
            // Use regular offer (first one)
            Pair(validatedProduct.subscriptionOfferDetails?.firstOrNull(), false)
        }
    }
    
    // Extract price from the selected offer
    val subscriptionPrice = selectedOffer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
    
    // For animation, get both regular and discount prices if discount offer is available
    val (originalPriceForAnimation, discountPrice) = remember(validatedProduct, isDiscountOfferAvailable) {
        if (isDiscountOfferAvailable && validatedProduct != null) {
            val regularOffer = validatedProduct.subscriptionOfferDetails?.firstOrNull()
            val regularPrice = regularOffer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
            val discountOfferPrice = selectedOffer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
            Pair(regularPrice, discountOfferPrice)
        } else {
            Pair(null, null)
        }
    }
    
    // Check if we have a real, validated product loaded
    val isProductAvailable = validatedProduct != null && subscriptionPrice != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Premium",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main title
            Text(
                text = if (isUserPro) "Welcome to SendRight Premium!" else "Upgrade to SendRight Premium",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (isUserPro) "You have unlimited AI features" else "Unlock unlimited AI features and remove ads",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Current status card
            StatusCard(
                isPro = isUserPro, 
                aiUsageStats = aiUsageStats,
                subscriptionManager = subscriptionManager
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Discount promotional card for first-time users (only if actual discount offer is available)
            if (isDiscountActive && isDiscountOfferAvailable) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎁",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "50% OFF for first time users!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Limited time offer - Save ₹45 today",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Subscribe/Status button - moved here to be below the plan card
            if (!isUserPro) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isProductAvailable) {
                            // Show real subscription button with actual product
                            Button(
                                onClick = {
                                    if (billingManager != null && validatedProduct != null) {
                                        scope.launch {
                                            try {
                                                // Launch the purchase flow with the correct offer
                                                val offerBasePlanId = if (isDiscountActive && isDiscountOfferAvailable) {
                                                    "sendright-pro-45"
                                                } else {
                                                    null // Use default offer
                                                }
                                                
                                                val result = billingManager.launchPurchaseFlowWithOffer(
                                                    context as ComponentActivity, 
                                                    validatedProduct,
                                                    offerBasePlanId
                                                )
                                                
                                                // DO NOT show success immediately - wait for actual purchase completion
                                                if (result.isFailure) {
                                                    Toast.makeText(
                                                        context, 
                                                        "Failed to launch purchase: ${result.exceptionOrNull()?.message}", 
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    Log.d("SubscriptionScreen", "Purchase flow launched successfully")
                                                }
                                                
                                                // The success will be handled by BillingManager.grantPremiumAccess()
                                                // after purchase is acknowledged
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    context, 
                                                    "Purchase failed: ${e.message}", 
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Billing service not ready", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    // Animated price display for discount
                                    if (isDiscountActive && isDiscountOfferAvailable && originalPriceForAnimation != null && discountPrice != null) {
                                        AnimatedPriceDisplay(
                                            originalPrice = originalPriceForAnimation,
                                            discountPrice = discountPrice
                                        )
                                    } else {
                                        Text(
                                            text = "Subscribe for $subscriptionPrice",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            // Show loading or error state when product isn't available
                            Button(
                                onClick = { },
                                enabled = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.outline
                                )
                            ) {
                                val errorMessage = when {
                                    products.isEmpty() -> "Loading subscription..."
                                    monthlyProduct == null -> "Product not found in Play Store"
                                    validatedProduct == null -> "Invalid test product detected"
                                    else -> "Product not available"
                                }
                                
                                Text(
                                    text = errorMessage,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Cancel anytime • Secure payment via Google Play",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Pro user status card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "You're a Premium subscriber!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enjoy unlimited AI features and ad-free experience",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Features section
            Text(
                text = "Premium Features",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Feature cards
            val features = listOf(
                FeatureData(
                    icon = Icons.Default.AutoAwesome,
                    title = "Unlimited AI Actions",
                    description = "Use AI features without daily limits",
                    isHighlight = true
                ),
                FeatureData(
                    icon = Icons.Default.Block,
                    title = "Ad-Free Experience", 
                    description = "No interruptions while typing"
                ),
                FeatureData(
                    icon = Icons.Default.Speed,
                    title = "Priority Processing",
                    description = "Faster AI responses and processing"
                ),
                FeatureData(
                    icon = Icons.Default.Support,
                    title = "Premium Support",
                    description = "Priority customer support"
                ),
                FeatureData(
                    icon = Icons.Default.Update,
                    title = "Early Access",
                    description = "Get new features before everyone else"
                )
            )
            
            features.forEach { feature ->
                FeatureCard(feature = feature)
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusCard(
    isPro: Boolean, 
    aiUsageStats: com.vishruth.key1.ime.ai.AiUsageStats,
    subscriptionManager: com.vishruth.key1.billing.SubscriptionManager?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPro) MaterialTheme.colorScheme.primaryContainer 
                           else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isPro) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = null,
                tint = if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isPro) "SendRight Premium - Active" else "Free Plan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            if (!isPro) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Show reward window status if active
                if (aiUsageStats.isRewardWindowActive) {
                    val remainingTime = aiUsageStats.rewardWindowTimeRemaining()
                    val hours = remainingTime / (1000 * 60 * 60)
                    val minutes = (remainingTime % (1000 * 60 * 60)) / (1000 * 60)
                    
                    Text(
                        text = "🎉 Unlimited AI actions active!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Time remaining: ${hours}h ${minutes}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Show current AI usage stats for free users
                    val dailyUsed = aiUsageStats.dailyActionCount
                    val dailyLimit = AiUsageStats.DAILY_LIMIT
                    val remaining = aiUsageStats.remainingActions()
                    
                    Text(
                        text = "$dailyUsed/$dailyLimit AI actions used today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (remaining > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$remaining actions remaining",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Daily limit reached - Watch ad for unlimited access",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // Progress indicator for free users  
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { dailyUsed.toFloat() / dailyLimit },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (remaining > 0) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

data class FeatureData(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val isHighlight: Boolean = false
)

@Composable
private fun FeatureCard(feature: FeatureData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (feature.isHighlight) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (feature.isHighlight) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = feature.icon,
                        contentDescription = null,
                        tint = if (feature.isHighlight) MaterialTheme.colorScheme.onPrimary 
                               else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AnimatedPriceDisplay(
    originalPrice: String,
    discountPrice: String
) {
    var showOriginal by remember { mutableStateOf(true) }
    var showStrikethrough by remember { mutableStateOf(false) }
    var showDiscountPrice by remember { mutableStateOf(false) }
    
    // Animation sequence
    LaunchedEffect(Unit) {
        delay(1000) // Show original price for 1 second
        showStrikethrough = true // Strike through original price
        delay(500) // Wait 0.5 seconds
        showDiscountPrice = true // Show discount price
        delay(500) // Wait 0.5 seconds 
        showOriginal = false // Hide original price
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Subscribe for ",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        
        // Original price with strikethrough animation
        AnimatedVisibility(
            visible = showOriginal,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = originalPrice,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textDecoration = if (showStrikethrough) TextDecoration.LineThrough else TextDecoration.None,
                color = if (showStrikethrough) Color.Red else MaterialTheme.colorScheme.onPrimary
            )
        }
        
        // Discount price animation
        AnimatedVisibility(
            visible = showDiscountPrice,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(500)
            ) + fadeIn(animationSpec = tween(500))
        ) {
            Row {
                if (showOriginal) {
                    Text(
                        text = " → ",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text(
                    text = discountPrice,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Green
                )
            }
        }
    }
}
