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
    LaunchedEffect(products) {
        android.util.Log.d("SubscriptionScreen", "Products loaded: ${products.size}")
        products.forEach { product ->
            android.util.Log.d("SubscriptionScreen", "Available product: ${product.productId}")
        }
        android.util.Log.d("SubscriptionScreen", "Looking for product: ${com.vishruth.key1.billing.BillingManager.PRODUCT_ID_PRO_MONTHLY}")
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
import com.android.billingclient.api.ProductDetails
import com.vishruth.key1.user.UserManager
import kotlinx.coroutines.launch

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
    
    // Observe UserManager userData for subscription status
    val userData by userManager.userData.collectAsState()
    
    // Collect state from managers with safe defaults
    val isPro by subscriptionManager?.isPro?.collectAsState() 
        ?: remember { mutableStateOf(false) }
    
    val aiActionsUsed by subscriptionManager?.aiActionsUsed?.collectAsState() 
        ?: remember { mutableStateOf(0) }
    
    val remainingActions by subscriptionManager?.remainingActions?.collectAsState()
        ?: remember { mutableStateOf(5) }
    
    // Determine if user is actually pro based on both sources
    val isUserPro = userData?.subscriptionStatus == "pro" || isPro
    
    // Force subscription status refresh when screen appears
    LaunchedEffect(Unit) {
        userManager.onAppResume()
    }
    
    // Show success message when user becomes pro
    LaunchedEffect(isUserPro) {
        if (isUserPro) {
            Toast.makeText(
                context,
                "🎉 Welcome to SendRight Pro! Unlimited AI features unlocked.",
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
    
    // Extract real price information from validated product only
    val subscriptionPrice = validatedProduct?.subscriptionOfferDetails?.firstOrNull()
        ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
    
    // Check if we have a real, validated product loaded
    val isProductAvailable = validatedProduct != null && subscriptionPrice != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "SendRight Pro",
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
            // Hero section with Pro icon
            Card(
                modifier = Modifier.size(120.dp),
                shape = RoundedCornerShape(60.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUserPro) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPro) Icons.Default.CheckCircle else Icons.Default.Star,
                        contentDescription = "Pro Status",
                        modifier = Modifier.size(60.dp),
                        tint = if (isPro) MaterialTheme.colorScheme.onPrimary 
                               else MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Main title
            Text(
                text = if (isUserPro) "Welcome to SendRight Pro!" else "Upgrade to SendRight Pro",
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
                aiActionsUsed = aiActionsUsed,
                remainingActions = remainingActions,
                subscriptionManager = subscriptionManager
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Features section
            Text(
                text = "Pro Features",
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
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Subscribe/Status button
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
                                                // Launch the purchase flow with the validated real product
                                                val result = billingManager.launchPurchaseFlow(
                                                    context as ComponentActivity, 
                                                    validatedProduct
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
                                    Text(
                                        text = "Subscribe for $subscriptionPrice",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
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
                            text = "You're a Pro subscriber!",
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
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusCard(
    isPro: Boolean, 
    aiActionsUsed: Int,
    remainingActions: Int,
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
                text = if (isPro) "SendRight Pro - Active" else "Free Plan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (!isPro) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subscriptionManager?.getSubscriptionStatusMessage() 
                        ?: "$aiActionsUsed/5 AI actions used today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                // Progress indicator for free users
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { aiActionsUsed.toFloat() / 10 },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (remainingActions > 0) MaterialTheme.colorScheme.primary 
                           else MaterialTheme.colorScheme.error
                )
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
