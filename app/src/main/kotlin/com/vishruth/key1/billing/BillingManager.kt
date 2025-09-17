/*
 * Copyright (C) 2025 SendRight 3.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Simple BillingManager for SendRight Pro subscriptions
 */
class BillingManager(private val context: Context) : PurchasesUpdatedListener {
    
    companion object {
        private const val TAG = "BillingManager"
        const val PRODUCT_ID_PRO_MONTHLY = "sendright.pro.89"
        const val BASE_PLAN_ID = "sendright-pro"
        
        enum class ConnectionState {
            DISCONNECTED,
            CONNECTING, 
            CONNECTED,
            ERROR
        }
    }
    
    private lateinit var billingClient: BillingClient
    private val billingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // StateFlows for UI
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()
    
    private val _purchaseUpdates = MutableSharedFlow<Result<Purchase>>()
    val purchaseUpdates: SharedFlow<Result<Purchase>> = _purchaseUpdates.asSharedFlow()
    
    init {
        setupBillingClient()
    }
    
    /**
     * Setup Google Play Billing client
     */
    fun setupBillingClient() {
        Log.d(TAG, "Setting up billing client")
        _connectionState.value = ConnectionState.CONNECTING
        
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        
        connectToBillingService()
    }
    
    private fun connectToBillingService() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected successfully")
                    _connectionState.value = ConnectionState.CONNECTED
                    
                    // Query available products
                    queryProducts()
                    
                    // Check for existing purchases as per reference Step 4
                    checkForExistingPurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    _connectionState.value = ConnectionState.ERROR
                }
            }
            
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        })
    }
    
    /**
     * Query available subscription products
     * Based on reference: Step 1 - Querying for Subscription Products
     */
    private fun queryProducts() {
        Log.d(TAG, "Querying subscription products...")
        Log.d(TAG, "Looking for product ID: $PRODUCT_ID_PRO_MONTHLY")
        
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_PRO_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        
        billingClient.queryProductDetailsAsync(params) { billingResult: BillingResult, productDetailsResult: QueryProductDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val products = productDetailsResult.productDetailsList ?: emptyList()
                _products.value = products
                Log.d(TAG, "Products queried successfully: ${products.size} products")
                
                if (products.isEmpty()) {
                    Log.w(TAG, "⚠️ NO PRODUCTS FOUND! Make sure 'sendright.pro.89' exists in Google Play Console")
                    Log.w(TAG, "⚠️ Product should be configured as SUBSCRIPTION with base plan 'sendright-pro'")
                } else {
                    // Log product details for debugging
                    products.forEach { product ->
                        Log.d(TAG, "✅ Product found: ${product.productId}")
                        Log.d(TAG, "  Product type: ${product.productType}")
                        Log.d(TAG, "  Title: ${product.title}")
                        Log.d(TAG, "  Description: ${product.description}")
                        
                        product.subscriptionOfferDetails?.forEach { offer ->
                            Log.d(TAG, "  Offer token: ${offer.offerToken}")
                            Log.d(TAG, "  Base plan ID: ${offer.basePlanId}")
                            offer.pricingPhases.pricingPhaseList.forEach { phase ->
                                Log.d(TAG, "    Price: ${phase.formattedPrice}")
                                Log.d(TAG, "    Period: ${phase.billingPeriod}")
                                
                                // Detect and warn about fake test products
                                if (phase.formattedPrice.contains("5min") || 
                                    phase.formattedPrice.contains("week") && phase.billingPeriod.contains("P1M")) {
                                    Log.e(TAG, "🚫 FAKE PRODUCT DETECTED!")
                                    Log.e(TAG, "   This appears to be Google Play test data, not your real product")
                                    Log.e(TAG, "   Expected: Monthly subscription with ₹89 price")
                                    Log.e(TAG, "   Actual: ${phase.formattedPrice} / ${phase.billingPeriod}")
                                    Log.e(TAG, "💡 SOLUTION: Configure product 'sendright.pro.89' in Google Play Console")
                                }
                            }
                        }
                    }
                }
                
                // Show products in UI now that they're loaded
                showProducts(products)
            } else {
                Log.e(TAG, "❌ Product query failed: ${billingResult.debugMessage}")
                Log.e(TAG, "❌ Response code: ${billingResult.responseCode}")
                _products.value = emptyList()
                
                // Common error explanations
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                        Log.e(TAG, "💡 Google Play Billing is not available on this device")
                    }
                    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {
                        Log.e(TAG, "💡 Product 'sendright.pro.89' not found in Play Console")
                    }
                    BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                        Log.e(TAG, "💡 Developer error - check app signing and Play Console configuration")
                    }
                }
            }
        }
    }
    
    /**
     * Process and display retrieved products
     * Based on reference: showing products in UI
     */
    private fun showProducts(productDetailsList: List<ProductDetails>) {
        Log.d(TAG, "Showing ${productDetailsList.size} products to user")
        // Products are now available in _products StateFlow for UI consumption
        // The subscription screen will automatically update when this flow changes
    }
    
    /**
     * Launch purchase flow for a product
     * Based on reference: Step 2 - Launching the Purchase Flow with an Offer
     */
    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails, offerIndex: Int = 0): Result<Unit> {
        return try {
            if (!billingClient.isReady) {
                return Result.failure(Exception("Billing client not ready"))
            }
            
            Log.d(TAG, "Starting purchase flow for product: ${productDetails.productId}")
            
            // Get the offer token from the specified offer (or first available)
            val offerToken = productDetails.subscriptionOfferDetails
                ?.getOrNull(offerIndex)
                ?.offerToken
            
            if (offerToken == null) {
                Log.e(TAG, "No offer token available for product ${productDetails.productId}")
                return Result.failure(Exception("No subscription offers available"))
            }
            
            Log.d(TAG, "Using offer token: $offerToken")
            
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build()
            )
            
            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()
                
            val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
            
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase flow launched successfully")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Purchase flow failed: ${billingResult.debugMessage}")
                Result.failure(Exception("Purchase flow failed: ${billingResult.debugMessage}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching purchase flow", e)
            Result.failure(e)
        }
    }
    
    /**
     * Handle purchase updates from Google Play
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                billingScope.launch {
                    handlePurchase(purchase)
                }
            }
        } else {
            Log.e(TAG, "Purchase update failed: ${billingResult.debugMessage}")
            billingScope.launch {
                _purchaseUpdates.emit(Result.failure(Exception("Purchase failed: ${billingResult.debugMessage}")))
            }
        }
    }
    
    /**
     * Handle individual purchase
     * Based on reference: Step 3 - Verifying and Acknowledging the Purchase
     */
    private suspend fun handlePurchase(purchase: Purchase) {
        try {
            Log.d(TAG, "Handling purchase: ${purchase.products}")
            Log.d(TAG, "Purchase state: ${purchase.purchaseState}")
            Log.d(TAG, "Purchase acknowledged: ${purchase.isAcknowledged}")
            
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                // Verify and acknowledge the purchase
                verifyPurchase(purchase)
                
                _purchaseUpdates.emit(Result.success(purchase))
                Log.d(TAG, "Purchase handled successfully: ${purchase.products}")
            } else {
                Log.w(TAG, "Purchase not in purchased state: ${purchase.purchaseState}")
                _purchaseUpdates.emit(Result.failure(Exception("Purchase not completed")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling purchase", e)
            _purchaseUpdates.emit(Result.failure(e))
        }
    }
    
    /**
     * Verify and acknowledge purchase
     * Based on reference: verifyPurchase method
     */
    private fun verifyPurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            Log.d(TAG, "Acknowledging purchase: ${purchase.purchaseToken}")
            
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            
            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Purchase acknowledged successfully")
                    // Subscription is active and acknowledged - grant entitlement to the user
                    grantPremiumAccess()
                } else {
                    Log.e(TAG, "Failed to acknowledge purchase: ${billingResult.debugMessage}")
                }
            }
        } else if (purchase.isAcknowledged) {
            Log.d(TAG, "Purchase already acknowledged, granting access")
            // Already acknowledged, just grant access
            grantPremiumAccess()
        }
    }
    
    /**
     * Grant premium access to the user
     */
    private fun grantPremiumAccess() {
        Log.d(TAG, "Granting premium access to user")
        // This will be handled by the SubscriptionManager when it observes purchase updates
        // The subscription manager will update the user's subscription status
    }
    
    /**
     * Check for existing purchases and handle any unacknowledged ones
     * Based on reference: Step 4 - Handling Pending Purchases in onResume
     * Should be called when the app resumes or billing client connects
     */
    fun checkForExistingPurchases() {
        if (!billingClient.isReady) {
            Log.w(TAG, "Billing client not ready, skipping purchase check")
            return
        }
        
        Log.d(TAG, "Checking for existing purchases...")
        
        billingScope.launch {
            try {
                val params = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
                
                val result = billingClient.queryPurchasesAsync(params)
                
                if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Found ${result.purchasesList.size} existing purchases")
                    
                    for (purchase in result.purchasesList) {
                        Log.d(TAG, "Existing purchase: ${purchase.products}, state: ${purchase.purchaseState}, acknowledged: ${purchase.isAcknowledged}")
                        
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                            Log.d(TAG, "Found unacknowledged purchase, processing...")
                            verifyPurchase(purchase)
                        } else if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && purchase.isAcknowledged) {
                            Log.d(TAG, "Found acknowledged purchase, granting access...")
                            grantPremiumAccess()
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to query existing purchases: ${result.billingResult.debugMessage}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking existing purchases", e)
            }
        }
    }
    
    /**
     * Check if user has an active subscription
     */
    suspend fun hasActiveSubscription(): Boolean {
        return try {
            if (!billingClient.isReady) {
                false
            } else {
                val params = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
                
                val result = billingClient.queryPurchasesAsync(params)
                result.purchasesList.any { purchase ->
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    purchase.products.contains(PRODUCT_ID_PRO_MONTHLY) &&
                    purchase.isAcknowledged
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking subscription status", e)
            false
        }
    }
    
    /**
     * Get product details by ID
     */
    fun getProductDetails(productId: String): ProductDetails? {
        return _products.value.find { it.productId == productId }
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        billingScope.cancel()
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
    }
}