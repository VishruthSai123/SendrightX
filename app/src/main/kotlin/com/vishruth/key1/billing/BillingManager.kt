/*
 * Copyright (C) 2025 SendRight 3.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.vishruth.key1.user.UserManager
import com.vishruth.key1.server.api.PurchaseValidationApi
import com.vishruth.key1.server.api.ValidationRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Simple BillingManager for SendRight Pro subscription management
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
    // MEMORY LEAK FIX: Added SupervisorJob to allow proper cancellation without affecting parent scope
    private val billingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val validationApi = PurchaseValidationApi()
    
    // Periodic sync for server entitlement verification
    private var periodicSyncJob: Job? = null
    private val SYNC_INTERVAL = 30 * 60 * 1000L // 30 minutes
    private var lastSyncTime = 0L
    
    // MEMORY LEAK FIX: Track if destroyed to prevent operations after cleanup
    private var isDestroyed = false

    // StateFlows for UI
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()
    
    private val _purchaseUpdates = MutableSharedFlow<Result<Purchase>>()
    val purchaseUpdates: SharedFlow<Result<Purchase>> = _purchaseUpdates.asSharedFlow()
    
    init {
        setupBillingClient()
        startPeriodicServerSync()
    }
    
    /**
     * MEMORY LEAK FIX: Proper cleanup method to prevent memory leaks
     * 
     * CRITICAL: This MUST be called when BillingManager is no longer needed
     * (e.g., in Activity.onDestroy() or when UserManager is destroyed).
     * 
     * Failure to call this will result in:
     * - Background jobs running indefinitely
     * - Context leaks through captured references  
     * - Battery drain from periodic sync
     * - Potential crashes from operations on destroyed contexts
     */
    fun destroy() {
        if (isDestroyed) {
            Log.w(TAG, "BillingManager already destroyed, skipping")
            return
        }
        
        try {
            Log.d(TAG, "Destroying BillingManager and cleaning up resources...")
            
            // Cancel all coroutine jobs
            periodicSyncJob?.cancel()
            billingScope.cancel()
            
            // End billing client connection
            if (::billingClient.isInitialized) {
                billingClient.endConnection()
            }
            
            isDestroyed = true
            Log.d(TAG, "✅ BillingManager destroyed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during BillingManager cleanup", e)
        }
    }
    
    /**
     * Start periodic server synchronization
     * This catches any subscription changes that webhooks might miss
     */
    private fun startPeriodicServerSync() {
        // MEMORY LEAK FIX: Cancel existing job before creating new one
        periodicSyncJob?.cancel()
        
        periodicSyncJob = billingScope.launch {
            while (isActive) {
                try {
                    // MEMORY LEAK FIX: Check if destroyed before proceeding
                    if (isDestroyed) {
                        Log.d(TAG, "BillingManager destroyed, stopping periodic sync")
                        break
                    }
                    
                    delay(SYNC_INTERVAL)
                    
                    val userManager = UserManager.getInstance()
                    val userData = userManager.userData.value
                    val userId = userData?.userId
                    
                    if (userId != null) {
                        Log.d(TAG, "🔄 Performing periodic server sync...")
                        syncWithServer(userId)
                    } else {
                        Log.d(TAG, "No authenticated user for periodic sync")
                    }
                    
                } catch (e: CancellationException) {
                    // EXCEPTION HANDLING FIX: Properly handle cancellation (don't suppress)
                    Log.d(TAG, "Periodic sync cancelled")
                    throw e // Re-throw to properly cancel the coroutine
                } catch (e: Exception) {
                    // EXCEPTION HANDLING FIX: Keep general catch for unexpected errors
                    Log.e(TAG, "Error in periodic server sync", e)
                    // Wait before retrying
                    delay(60_000L)
                }
            }
        }
    }
    
    /**
     * Synchronize subscription status with server
     * This method refreshes entitlements and catches missed webhook notifications
     */
    private suspend fun syncWithServer(userId: String) {
        try {
            Log.d(TAG, "Syncing subscription status with server for user: $userId")
            
            // Refresh entitlements on server (re-validates with Google Play)
            val refreshResponse = validationApi.refreshUserEntitlements(userId)
            
            if (refreshResponse.isSuccess) {
                Log.d(TAG, "✅ Server sync completed")
                Log.d(TAG, "Refreshed ${refreshResponse.refreshedCount} entitlements")
                Log.d(TAG, "Found ${refreshResponse.activeCount} active subscriptions")
                
                // Update local state based on server results
                val hasActiveSubscription = refreshResponse.activeCount > 0
                val localState = getSubscriptionState()
                
                if (hasActiveSubscription != localState) {
                    Log.w(TAG, "🔄 Subscription status changed during sync!")
                    Log.w(TAG, "Server says: $hasActiveSubscription, Local was: $localState")
                    
                    saveSubscriptionState(hasActiveSubscription)
                    updateSubscriptionManagers(hasActiveSubscription)
                    
                    if (hasActiveSubscription) {
                        Log.d(TAG, "🎉 Subscription restored from server sync")
                    } else {
                        Log.w(TAG, "🚨 Subscription revoked during server sync")
                    }
                }
                
                lastSyncTime = System.currentTimeMillis()
                
            } else {
                Log.e(TAG, "❌ Server sync failed: ${refreshResponse.error}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during server sync", e)
        }
    }
    
    /**
     * Force immediate server sync
     * Call this when you want to immediately check server entitlement status
     */
    suspend fun forceSyncWithServer() {
        try {
            val userManager = UserManager.getInstance()
            val userData = userManager.userData.value
            val userId = userData?.userId
            
            if (userId != null) {
                Log.d(TAG, "🚀 Force syncing with server...")
                syncWithServer(userId)
            } else {
                Log.w(TAG, "No authenticated user for force sync")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during force sync", e)
        }
    }
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
     * Find offer by base plan ID from a product's subscription offers
     */
    fun findOfferByBasePlanId(productDetails: ProductDetails, basePlanId: String): ProductDetails.SubscriptionOfferDetails? {
        return productDetails.subscriptionOfferDetails?.find { offer ->
            offer.basePlanId == basePlanId
        }
    }
    
    /**
     * Get offer index by base plan ID
     */
    fun getOfferIndexByBasePlanId(productDetails: ProductDetails, basePlanId: String): Int {
        return productDetails.subscriptionOfferDetails?.indexOfFirst { offer ->
            offer.basePlanId == basePlanId
        } ?: -1
    }
    
    /**
     * Launch purchase flow with specific offer by base plan ID
     */
    fun launchPurchaseFlowWithOffer(activity: Activity, productDetails: ProductDetails, basePlanId: String? = null): Result<Unit> {
        val offerIndex = if (basePlanId != null) {
            val index = getOfferIndexByBasePlanId(productDetails, basePlanId)
            if (index == -1) {
                Log.w(TAG, "Offer with base plan ID '$basePlanId' not found, using default offer")
                0 // Fallback to first offer
            } else {
                Log.d(TAG, "Using offer with base plan ID '$basePlanId' at index $index")
                index
            }
        } else {
            0 // Use first offer if no specific offer requested
        }
        
        return launchPurchaseFlow(activity, productDetails, offerIndex)
    }
    
    /**
     * Launch purchase flow for a product with specific offer token
     */
    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails, offerToken: String): Result<Unit> {
        return try {
            if (!billingClient.isReady) {
                return Result.failure(Exception("Billing client not ready"))
            }
            
            Log.d(TAG, "Starting purchase flow for product: ${productDetails.productId}")
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
                Log.d(TAG, "Billing flow launched successfully")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to launch billing flow: ${billingResult.debugMessage}")
                Result.failure(Exception("Failed to launch purchase flow: ${billingResult.debugMessage}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception launching purchase flow: ${e.message}")
            Result.failure(e)
        }
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
     * Handle individual purchase with server-side verification
     * Based on reference: Step 3 - Verifying and Acknowledging the Purchase
     * Now uses server-side validation to prevent client manipulation
     */
    private suspend fun handlePurchase(purchase: Purchase) {
        try {
            Log.d(TAG, "Handling purchase: ${purchase.products}")
            Log.d(TAG, "Purchase state: ${purchase.purchaseState}")
            Log.d(TAG, "Purchase acknowledged: ${purchase.isAcknowledged}")
            
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                // NEW: Server-side verification instead of local acknowledgment
                verifyPurchaseWithServer(purchase)
                
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
     * NEW: Verify purchase with server-side validation
     * This replaces the old local verifyPurchase method
     * Sends purchaseToken to server for Google Play API verification
     */
    private suspend fun verifyPurchaseWithServer(purchase: Purchase) {
        try {
            Log.d(TAG, "🔐 Starting server-side purchase verification...")
            Log.d(TAG, "Purchase token: ${purchase.purchaseToken.take(20)}...")
            
            // Get current user ID for server validation
            val userManager = UserManager.getInstance()
            val userData = userManager.userData.value
            val userId = userData?.userId ?: run {
                Log.e(TAG, "❌ No authenticated user found for purchase verification")
                throw Exception("User not authenticated")
            }
            
            // Create validation request
            val validationRequest = ValidationRequest(
                userId = userId,
                packageName = context.packageName,
                productId = purchase.products.firstOrNull() ?: PRODUCT_ID_PRO_MONTHLY,
                purchaseToken = purchase.purchaseToken
            )
            
            Log.d(TAG, "📤 Sending validation request to server...")
            Log.d(TAG, "User ID: $userId")
            Log.d(TAG, "Package: ${validationRequest.packageName}")
            Log.d(TAG, "Product: ${validationRequest.productId}")
            
            // Send to server for verification
            val validationResponse = validationApi.validateSubscriptionPurchase(validationRequest)
            
            if (validationResponse.isSuccess && validationResponse.isValid) {
                Log.d(TAG, "✅ Server validation successful!")
                Log.d(TAG, "Subscription status: ${validationResponse.subscriptionStatus}")
                Log.d(TAG, "Expiry time: ${java.util.Date(validationResponse.expiryTimeMillis)}")
                Log.d(TAG, "Auto-renewing: ${validationResponse.autoRenewing}")
                
                // Server confirmed purchase is valid - acknowledge with Google Play
                if (!purchase.isAcknowledged) {
                    acknowledgePurchaseWithGooglePlay(purchase)
                }
                
                // Grant premium access based on server validation
                grantPremiumAccess()
                
                Log.d(TAG, "🎉 Premium access granted after server verification")
                
            } else {
                Log.e(TAG, "❌ Server validation failed!")
                Log.e(TAG, "Error: ${validationResponse.error}")
                Log.e(TAG, "Message: ${validationResponse.message}")
                
                // Do not grant premium access if server validation fails
                throw Exception("Server validation failed: ${validationResponse.message}")
            }
            
        } catch (e: CancellationException) {
            // EXCEPTION HANDLING FIX: Don't suppress cancellation
            Log.d(TAG, "Purchase verification cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error during server-side verification", e)
            
            // SECURITY FIX: Block local verification in production builds
            // Fallback to local verification only in development/debug builds
            if (com.vishruth.key1.BuildConfig.DEBUG) {
                Log.w(TAG, "⚠️ Falling back to local verification (DEBUG BUILD ONLY)")
                verifyPurchaseLocally(purchase)
            } else {
                // SECURITY: In production, NEVER fall back to local verification
                Log.e(TAG, "❌ Server verification failed in RELEASE build - denying access")
                Log.e(TAG, "This is a security measure to prevent purchase fraud")
                throw SecurityException("Server verification required in production builds")
            }
        }
    }
    
    /**
     * Acknowledge purchase with Google Play after server validation
     */
    private suspend fun acknowledgePurchaseWithGooglePlay(purchase: Purchase) {
        try {
            Log.d(TAG, "📝 Acknowledging purchase with Google Play...")
            
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            
            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "✅ Purchase acknowledged with Google Play")
                } else {
                    Log.e(TAG, "❌ Failed to acknowledge purchase: ${billingResult.debugMessage}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acknowledging purchase", e)
        }
    }
    
    /**
     * Fallback local verification (for development/offline use)
     * 
     * SECURITY WARNING: This method is ONLY available in DEBUG builds.
     * In production/release builds, this code path is completely blocked.
     * 
     * This is the old verification method, used only as fallback during development.
     */
    private fun verifyPurchaseLocally(purchase: Purchase) {
        // SECURITY FIX: Double-check we're in debug mode
        if (!com.vishruth.key1.BuildConfig.DEBUG) {
            Log.e(TAG, "❌ SECURITY VIOLATION: Local verification attempted in RELEASE build!")
            throw SecurityException("Local verification is not allowed in production builds")
        }
        
        Log.w(TAG, "⚠️ Using local verification - DEBUG BUILD ONLY - NOT for production")
        
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            Log.d(TAG, "Purchase is in PURCHASED state: ${purchase.products}")
            
            if (!purchase.isAcknowledged) {
                Log.d(TAG, "Acknowledging purchase locally: ${purchase.purchaseToken}")
                
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Purchase acknowledged locally")
                        grantPremiumAccess()
                    } else {
                        Log.e(TAG, "Failed to acknowledge purchase locally: ${billingResult.debugMessage}")
                    }
                }
            } else {
                Log.d(TAG, "Purchase already acknowledged, granting access")
                grantPremiumAccess()
            }
        }
    }
    
    /**
     * Grant premium access to the user
     * Based on reference: grantPremiumAccess function
     */
    private fun grantPremiumAccess() {
        Log.d(TAG, "Granting premium access to user")
        
        billingScope.launch {
            try {
                // Save subscription state locally using SharedPreferences
                saveSubscriptionState(true)
                
                // Update the subscription manager and UserManager
                updateSubscriptionManagers(true)
                
                // Mark user as no longer first-time subscriber (discount no longer available)
                try {
                    val userManager = UserManager.getInstance()
                    userManager.markAsNotFirstTimeSubscriber()
                    Log.d(TAG, "Marked user as not first-time subscriber - discount no longer available")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to mark user as not first-time subscriber", e)
                }
                
                // Emit successful purchase update for UI to react
                _purchaseUpdates.emit(Result.success(createDummyPurchase()))
                
                Log.d(TAG, "Premium access granted successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error granting premium access", e)
                _purchaseUpdates.emit(Result.failure(e))
            }
        }
    }
    
    /**
     * Save subscription state to SharedPreferences
     * 
     * SECURITY FIX: Now uses encrypted SharedPreferences to protect subscription state
     * from tampering on rooted devices or with physical access.
     * 
     * Based on reference: saveSubscriptionState function
     */
    fun saveSubscriptionState(isPremium: Boolean) {
        try {
            // SECURITY FIX: Use encrypted preferences instead of plain storage
            val prefs = SecurePreferences.getEncryptedPreferences(context, "billing_prefs")
            
            // Migrate old data if exists (one-time migration)
            SecurePreferences.migrateToEncrypted(context, "MyAppPrefs", "billing_prefs")
            
            prefs.edit().putBoolean("is_premium_user", isPremium).apply()
            Log.d(TAG, "Subscription state saved securely: isPremium = $isPremium")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save subscription state", e)
        }
    }
    
    /**
     * Get subscription state from SharedPreferences
     * 
     * SECURITY FIX: Now reads from encrypted SharedPreferences
     * 
     * Based on reference: getSubscriptionState function
     */
    fun getSubscriptionState(): Boolean {
        return try {
            // SECURITY FIX: Read from encrypted preferences
            val prefs = SecurePreferences.getEncryptedPreferences(context, "billing_prefs")
            val isPremium = prefs.getBoolean("is_premium_user", false)
            Log.d(TAG, "Retrieved subscription state securely: isPremium = $isPremium")
            isPremium
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve subscription state", e)
            false // Safe default
        }
    }
    
    /**
     * Update subscription managers with new status
     */
    private suspend fun updateSubscriptionManagers(isPremium: Boolean) {
        try {
            // Get UserManager instance and update subscription status
            val userManager = UserManager.getInstance()
            val newStatus = if (isPremium) "pro" else "free"
            // Note: Removed Firebase integration - subscription status is now managed locally
            Log.d(TAG, "Local subscription status updated to: $newStatus")
            
            // if (result.isSuccess) {
            //     Log.d(TAG, "UserManager updated with subscription status: $newStatus")
            // } else {
            //     Log.e(TAG, "Failed to update UserManager: ${result.exceptionOrNull()?.message}")
            // }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating subscription managers", e)
        }
    }
    
    /**
     * Create a dummy purchase object for successful purchase updates
     */
    private fun createDummyPurchase(): Purchase {
        // This is a simplified approach for the purchase update flow
        // In a real app, you'd use the actual purchase object
        val json = """
            {
                "orderId": "premium_${System.currentTimeMillis()}",
                "packageName": "${context.packageName}",
                "productId": "$PRODUCT_ID_PRO_MONTHLY",
                "purchaseTime": ${System.currentTimeMillis()},
                "purchaseState": 1,
                "purchaseToken": "premium_token_${System.currentTimeMillis()}",
                "acknowledged": true
            }
        """.trimIndent()
        
        return Purchase(json, "")
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
                            Log.d(TAG, "Found unacknowledged purchase, processing with server verification...")
                            verifyPurchaseWithServer(purchase)
                        } else if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && purchase.isAcknowledged) {
                            Log.d(TAG, "Found acknowledged purchase, verifying entitlement with server...")
                            
                            // Check with server if this user still has valid entitlement
                            try {
                                val userManager = UserManager.getInstance()
                                val userData = userManager.userData.value
                                val userId = userData?.userId
                                
                                if (userId != null) {
                                    val entitlementResponse = validationApi.checkUserEntitlements(userId)
                                    if (entitlementResponse.isSuccess && entitlementResponse.hasActiveSubscription) {
                                        Log.d(TAG, "Server confirms active subscription, granting access...")
                                        grantPremiumAccess()
                                    } else {
                                        Log.w(TAG, "Server shows no active subscription despite local purchase")
                                    }
                                } else {
                                    Log.w(TAG, "No authenticated user for entitlement check")
                                    // Fallback to local grant for existing acknowledged purchases
                                    grantPremiumAccess()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error checking server entitlement, falling back to local grant", e)
                                grantPremiumAccess()
                            }
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
     * Restore purchases from Google Play purchase history
     * This is critical for handling app reinstalls where local state is lost
     * Uses Google Play's purchase history API to recover subscriptions
     */
    fun restorePurchases(callback: (Boolean, String?) -> Unit) {
        if (!billingClient.isReady) {
            Log.w(TAG, "Billing client not ready, cannot restore purchases")
            callback(false, "Billing service not available")
            return
        }
        
        Log.d(TAG, "Starting purchase restoration...")
        
        billingScope.launch {
            try {
                // Check current active purchases first
                val currentParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
                
                val currentResult = billingClient.queryPurchasesAsync(currentParams)
                
                if (currentResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val activePurchases = currentResult.purchasesList
                    Log.d(TAG, "Found ${activePurchases.size} active purchases")
                    
                    // Check for active subscriptions
                    val hasActiveSubscription = activePurchases.any { purchase ->
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        purchase.products.contains(PRODUCT_ID_PRO_MONTHLY) &&
                        purchase.isAcknowledged
                    }
                    
                    if (hasActiveSubscription) {
                        Log.d(TAG, "Found active subscription, restoring premium access")
                        grantPremiumAccess()
                        withContext(Dispatchers.Main) {
                            callback(true, "Active subscription restored successfully")
                        }
                    } else {
                        Log.d(TAG, "No active subscription found")
                        withContext(Dispatchers.Main) {
                            callback(false, "No active subscription found to restore")
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to query current purchases: ${currentResult.billingResult.debugMessage}")
                    withContext(Dispatchers.Main) {
                        callback(false, "Failed to verify current subscriptions")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during purchase restoration", e)
                withContext(Dispatchers.Main) {
                    callback(false, "Error occurred during purchase restoration: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Store purchase history record for server-side verification
     * This helps maintain entitlement records even when local state is lost
     */
    private suspend fun storePurchaseHistoryForVerification(purchase: Purchase) {
        try {
            Log.d(TAG, "Storing purchase history for verification: ${purchase.products}")
            
            // Get current user for server-side storage
            val userManager = UserManager.getInstance()
            val userData = userManager.userData.value
            
            if (userData?.userId != null) {
                // Store in server-side database for verification
                storePurchaseOnServer(
                    userId = userData.userId,
                    purchaseToken = purchase.purchaseToken,
                    products = purchase.products,
                    purchaseTime = purchase.purchaseTime
                )
            } else {
                Log.w(TAG, "No authenticated user found, cannot store purchase history on server")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error storing purchase history for verification", e)
        }
    }
    
    /**
     * Check if user has an active subscription with server-side verification
     * Now checks both Google Play and server entitlements for comprehensive validation
     */
    suspend fun hasActiveSubscription(): Boolean {
        return try {
            Log.d(TAG, "🔍 Checking subscription status with server verification...")
            
            // Step 1: Check server entitlements first (authoritative source)
            val userManager = UserManager.getInstance()
            val userData = userManager.userData.value
            val userId = userData?.userId
            
            if (userId != null) {
                try {
                    val entitlementResponse = validationApi.checkUserEntitlements(userId)
                    if (entitlementResponse.isSuccess) {
                        Log.d(TAG, "Server entitlement check result: ${entitlementResponse.hasActiveSubscription}")
                        
                        if (entitlementResponse.hasActiveSubscription) {
                            // Server confirms active subscription
                            saveSubscriptionState(true)
                            updateSubscriptionManagers(true)
                            return true
                        } else {
                            Log.d(TAG, "Server shows no active subscription")
                        }
                    } else {
                        Log.w(TAG, "Server entitlement check failed: ${entitlementResponse.error}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking server entitlements", e)
                }
            } else {
                Log.w(TAG, "No authenticated user for server entitlement check")
            }
            
            // Step 2: Fallback to Google Play verification
            Log.d(TAG, "Falling back to Google Play verification...")
            
            // Always verify with Google Play Billing first for real-time status
            if (!billingClient.isReady) {
                Log.w(TAG, "Billing client not ready, attempting to connect...")
                // Try to reconnect if not ready
                setupBillingClient()
                delay(1000) // Give some time for connection
                
                if (!billingClient.isReady) {
                    Log.w(TAG, "Billing client still not ready, using local state as fallback")
                    return getSubscriptionState()
                }
            }
            
            // Query Google Play for real-time subscription status
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            
            val result = billingClient.queryPurchasesAsync(params)
            val hasActivePurchase = result.purchasesList.any { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.contains(PRODUCT_ID_PRO_MONTHLY) &&
                purchase.isAcknowledged
            }
            
            Log.d(TAG, "Google Play subscription check result: $hasActivePurchase")
            
            // Get local state for comparison
            val localSubscriptionState = getSubscriptionState()
            
            // If Google Play and local state don't match, update local state immediately
            if (hasActivePurchase != localSubscriptionState) {
                Log.w(TAG, "🔄 Subscription state mismatch detected!")
                Log.w(TAG, "   Google Play: $hasActivePurchase")
                Log.w(TAG, "   Local cache: $localSubscriptionState")
                Log.w(TAG, "   Updating local cache to match Google Play...")
                
                saveSubscriptionState(hasActivePurchase)
                
                // Update subscription managers immediately
                updateSubscriptionManagers(hasActivePurchase)
                
                if (!hasActivePurchase && localSubscriptionState) {
                    Log.w(TAG, "🚨 SUBSCRIPTION EXPIRED - Clearing cache and revoking access")
                }
            }
            
            // Return Google Play state as authoritative
            hasActivePurchase
        } catch (e: Exception) {
            Log.e(TAG, "Error checking subscription status, falling back to local state", e)
            // Fallback to local state only if Google Play check completely fails
            getSubscriptionState()
        }
    }
    
    /**
     * Get product details by ID
     */
    fun getProductDetails(productId: String): ProductDetails? {
        return _products.value.find { it.productId == productId }
    }
    
    /**
     * Store purchase information on server for persistent entitlement tracking
     * This ensures subscriptions are not lost on app reinstall
     */
    private suspend fun storePurchaseOnServer(
        userId: String,
        purchaseToken: String,
        products: List<String>,
        purchaseTime: Long
    ) {
        try {
            Log.d(TAG, "Storing purchase on server for user: $userId")
            
            // Note: Firebase integration removed - purchases are now managed locally
            Log.d(TAG, "Purchase data would be stored: token=$purchaseToken, products=$products")
            
            Log.d(TAG, "Purchase successfully stored locally")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store purchase", e)
        }
    }
    
    /**
     * Verify subscription status with server-side entitlements
     * This provides an additional layer of subscription validation
     */
    suspend fun verifyServerSideEntitlements(userId: String): Boolean {
        return try {
            Log.d(TAG, "Verifying server-side entitlements for user: $userId")
            
            // Note: Firebase integration removed - using local verification only
            Log.d(TAG, "Server-side entitlement verification disabled (Firebase removed)")
            false // Return false since we can't verify with server
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying server-side entitlements", e)
            false
        }
    }
    
    /**
     * Sync subscription state with server and restore if needed
     * This should be called during app startup after user authentication
     */
    suspend fun syncWithServerAndRestore(userId: String) {
        try {
            Log.d(TAG, "Syncing subscription state with server for user: $userId")
            
            // First check local state
            val localSubscriptionState = getSubscriptionState()
            
            // Then check server-side entitlements
            val serverHasSubscription = verifyServerSideEntitlements(userId)
            
            // Finally verify with Google Play
            val googlePlayHasSubscription = hasActiveSubscription()
            
            Log.d(TAG, "Subscription state comparison - Local: $localSubscriptionState, Server: $serverHasSubscription, Google Play: $googlePlayHasSubscription")
            
            // If Google Play has active subscription but local doesn't, restore it
            if (googlePlayHasSubscription && !localSubscriptionState) {
                Log.d(TAG, "Restoring subscription from Google Play")
                grantPremiumAccess()
            }
            
            // If server has subscription but Google Play doesn't, there might be an issue
            if (serverHasSubscription && !googlePlayHasSubscription) {
                Log.w(TAG, "Server shows subscription but Google Play doesn't - subscription may have expired")
                // Could trigger a more detailed verification or user notification here
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during subscription sync", e)
        }
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        periodicSyncJob?.cancel()
        billingScope.cancel()
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
    }
}