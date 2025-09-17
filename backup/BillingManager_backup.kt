/*
 * Copyright (C) 2025 SendRight 3.0
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

package com.vishruth.key1.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.*
import com.vishruth.key1.user.UserManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Google Play Billing integration for SendRight 3.0
 * Handles subscription lifecycle, purchase verification, and billing state management
 * 
 * Implements all Google Play Billing Library integration steps:
 * 1. ✅ Add Dependency (in build.gradle)
 * 2. ✅ Initialize BillingClient 
 * 3. ✅ Connect to Google Play
 * 4. ✅ Query Products
 * 5. ✅ Display Products (via StateFlow)
 * 6. ✅ Launch Purchase Flow
 * 7. ✅ Verify Purchase
 * 8. ✅ Grant Content and Consume/Acknowledge
 */
class BillingManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) : PurchasesUpdatedListener, BillingClientStateListener {

    companion object {
        private const val TAG = "BillingManager"
        
        // SendRight 3.0 subscription product ID (configure in Google Play Console)
        const val SUBSCRIPTION_PRODUCT_ID = "sendright_go_pro.89"  // Simplified product ID
        
        // Subscription base plan ID (configure in Google Play Console)
        const val SUBSCRIPTION_BASE_PLAN_ID = "go-pro"  // Simplified base plan ID
    }

    // STEP 2: Initialize BillingClient
    private var billingClient: BillingClient? = null
    
    // Connection state management with conflated flow for better performance
    private val _connectionState = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()
    
    // STEP 4 & 5: Query and Display Products - available subscription products cached for performance
    private val _subscriptionProducts = MutableStateFlow<List<ProductDetails>>(emptyList())
    val subscriptionProducts: StateFlow<List<ProductDetails>> = _subscriptionProducts.asStateFlow()
    
    // Current user subscriptions - cached
    private val _userSubscriptions = MutableStateFlow<List<Purchase>>(emptyList())
    val userSubscriptions: StateFlow<List<Purchase>> = _userSubscriptions.asStateFlow()
    
    // STEP 6: Launch Purchase Flow - purchase result flow
    private val _purchaseResult = MutableStateFlow<PurchaseResult?>(null)
    val purchaseResult: StateFlow<PurchaseResult?> = _purchaseResult.asStateFlow()
    
    // Purchase in progress state management
    private val _purchaseInProgress = MutableStateFlow(false)
    val purchaseInProgress: StateFlow<Boolean> = _purchaseInProgress.asStateFlow()
    
    // Cache for subscription product to avoid repeated searches
    private var cachedSubscriptionProduct: ProductDetails? = null
    private var lastCacheUpdate: Long = 0
    private val cacheValidDuration = 30_000L // 30 seconds

    /**
     * Billing connection states
     */
    enum class BillingConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    /**
     * Purchase result data class for STEP 6 & 7
     */
    data class PurchaseResult(
        val billingResult: BillingResult,
        val purchases: List<Purchase>?
    )

    // Initialization flag to prevent multiple initializations
    private var isInitialized = false
    
    init {
        // STEP 2 & 3: Initialize billing client and connect asynchronously
        coroutineScope.launch {
            try {
                initializeBillingClient()
            } catch (e: Exception) {
                Log.e(TAG, "Error in BillingManager init block", e)
                _connectionState.value = BillingConnectionState.ERROR
            }
        }
    }

    /**
     * Force initialization and wait for connection - call this before showing subscription UI
     */
    suspend fun ensureInitializedAndConnected(): Boolean {
        Log.d(TAG, "🔄 Ensuring BillingClient is initialized and connected")
        
        // Initialize if not already done
        if (!isInitialized) {
            initializeBillingClient()
        }
        
        // Wait up to 10 seconds for connection
        var attempts = 0
        while (attempts < 20 && connectionState.value != BillingConnectionState.CONNECTED) {
            delay(500)
            attempts++
            Log.d(TAG, "⏳ Waiting for connection... attempt $attempts/20, state: ${connectionState.value}")
        }
        
        val isConnected = connectionState.value == BillingConnectionState.CONNECTED
        Log.d(TAG, if (isConnected) "✅ BillingClient ready!" else "❌ BillingClient connection timeout")
        
        return isConnected
    }

    /**
     * STEP 2: Initialize the BillingClient with proper configuration (async)
     */
    private suspend fun initializeBillingClient() {
        if (isInitialized) return
        
        try {
            Log.d(TAG, "STEP 2: Initializing BillingClient asynchronously")
            
            // Switch to IO thread for initialization
            withContext(Dispatchers.IO) {
                try {
                    billingClient = BillingClient.newBuilder(context)
                        .setListener(this@BillingManager)
                        .enableAutoServiceReconnection()  // Critical: Enable auto-reconnection per Google guidelines
                        .enablePendingPurchases(
                            PendingPurchasesParams.newBuilder()
                                // For subscriptions, we don't need enableOneTimeProducts()
                                .build()
                        )
                        .build()
                    
                    isInitialized = true
                    Log.d(TAG, "BillingClient created successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating BillingClient", e)
                    _connectionState.value = BillingConnectionState.ERROR
                    return@withContext
                }
            }
            
            // STEP 3: Start connection on main thread
            withContext(Dispatchers.Main) {
                try {
                    startConnection()
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting connection", e)
                    _connectionState.value = BillingConnectionState.ERROR
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in initializeBillingClient", e)
            _connectionState.value = BillingConnectionState.ERROR
        }
    }

    /**
     * STEP 3: Start connection to Google Play Billing
     */
    private fun startConnection() {
        if (!isInitialized || billingClient == null) {
            Log.w(TAG, "BillingClient not initialized, cannot start connection")
            return
        }
        
        _connectionState.value = BillingConnectionState.CONNECTING
        Log.d(TAG, "STEP 3: Starting connection to Google Play Billing")
        billingClient?.startConnection(this)
    }

    /**
     * STEP 3: Called when billing setup is finished
     */
    override fun onBillingSetupFinished(billingResult: BillingResult) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                Log.d(TAG, "STEP 3: ✅ Billing setup finished successfully")
                _connectionState.value = BillingConnectionState.CONNECTED
                Toast.makeText(context, "✅ Billing connected successfully!", Toast.LENGTH_SHORT).show()
                
                coroutineScope.launch {
                    try {
                        // STEP 4: Query available products once connected
                        querySubscriptionProducts()
                        queryUserSubscriptions()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error after billing setup finished", e)
                    }
                }
            }
            else -> {
                Log.e(TAG, "STEP 3: ❌ Billing setup failed: ${billingResult.debugMessage}")
                _connectionState.value = BillingConnectionState.ERROR
                Toast.makeText(context, "❌ Billing setup failed: ${billingResult.responseCode}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * STEP 3: Called when billing service is disconnected
     */
    override fun onBillingServiceDisconnected() {
        Log.w(TAG, "STEP 3: ⚠️ Billing service disconnected")
        _connectionState.value = BillingConnectionState.DISCONNECTED
        
        // Auto-reconnection is enabled, so this will be handled automatically
        // But we can implement additional retry logic if needed
    }

    /**
     * STEP 4: Query available subscription products with retry logic
     */
    private suspend fun querySubscriptionProducts() {
        if (billingClient?.isReady != true) {
            Log.w(TAG, "BillingClient not ready for product query")
            return
        }

        try {
            Log.d(TAG, "STEP 4: 🔍 Querying subscription products")
            
            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SUBSCRIPTION_PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            withContext(Dispatchers.IO) {
                val result = billingClient?.queryProductDetails(params)
                
                when (result?.billingResult?.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        val products = result.productDetailsList ?: emptyList()
                        Log.d(TAG, "STEP 4: ✅ Query successful - Found ${products.size} subscription products")
                        
                        if (products.isEmpty()) {
                            Log.e(TAG, "STEP 4: ❌ CRITICAL: No products returned by Google Play!")
                            Log.e(TAG, "STEP 4: ❌ This means:")
                            Log.e(TAG, "STEP 4: ❌   1. Product ID '$SUBSCRIPTION_PRODUCT_ID' doesn't exist in Play Console")
                            Log.e(TAG, "STEP 4: ❌   2. Certificate mismatch between APK and Play Console")
                            Log.e(TAG, "STEP 4: ❌   3. Package name '$context.packageName' doesn't match Play Console project")
                            Log.e(TAG, "STEP 4: ❌   4. App not uploaded to this Play Console project")
                            Log.e(TAG, "STEP 4: ❌   5. Product not activated in Play Console")
                            
                            Toast.makeText(context, "❌ No products found!\nCheck: Certificate, Package name, Product setup", Toast.LENGTH_LONG).show()
                        } else {
                            Log.d(TAG, "STEP 4: ✅ SUCCESS! Products loaded:")
                            products.forEach { product ->
                                Log.d(TAG, "STEP 4: 📦 Product: ${product.productId}")
                                Log.d(TAG, "STEP 4: 📦 Title: ${product.title}")
                                product.subscriptionOfferDetails?.forEach { offer ->
                                    Log.d(TAG, "STEP 4: 🎫 Base plan: ${offer.basePlanId}, Token: ${offer.offerToken.take(10)}...")
                                }
                            }
                            Toast.makeText(context, "✅ Found ${products.size} product(s) - billing ready!", Toast.LENGTH_SHORT).show()
                        }
                        
                        _subscriptionProducts.value = products
                        
                        // Update cache
                        cachedSubscriptionProduct = products.find { it.productId == SUBSCRIPTION_PRODUCT_ID }
                        lastCacheUpdate = System.currentTimeMillis()
                    }
                    else -> {
                        val responseCode = result?.billingResult?.responseCode
                        val debugMessage = result?.billingResult?.debugMessage
                        Log.e(TAG, "STEP 4: ❌ BILLING QUERY FAILED!")
                        Log.e(TAG, "STEP 4: ❌ Response Code: $responseCode")
                        Log.e(TAG, "STEP 4: ❌ Debug Message: $debugMessage")
                        Log.e(TAG, "STEP 4: ❌ Package Name: ${context.packageName}")
                        Log.e(TAG, "STEP 4: ❌ Product ID: $SUBSCRIPTION_PRODUCT_ID")
                        
                        when (responseCode) {
                            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {
                                Log.e(TAG, "STEP 4: ❌ ITEM_UNAVAILABLE - Product doesn't exist or not available in this region")
                                Toast.makeText(context, "❌ Product unavailable - check Play Console setup", Toast.LENGTH_LONG).show()
                            }
                            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                                Log.e(TAG, "STEP 4: ❌ BILLING_UNAVAILABLE - Billing not supported on this device")
                                Toast.makeText(context, "❌ Billing not available on this device", Toast.LENGTH_LONG).show()
                            }
                            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                                Log.e(TAG, "STEP 4: ❌ SERVICE_UNAVAILABLE - Google Play Store not available")
                                Toast.makeText(context, "❌ Google Play Store unavailable", Toast.LENGTH_LONG).show()
                            }
                            else -> {
                                Log.e(TAG, "STEP 4: ❌ Unknown error - check certificate and package name")
                                Toast.makeText(context, "❌ Query failed: Code $responseCode", Toast.LENGTH_LONG).show()
                            }
                        }
                        _subscriptionProducts.value = emptyList()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "STEP 4: ❌ Error querying subscription products", e)
            _subscriptionProducts.value = emptyList()
        }
    }

    /**
     * Retry product loading - call this if initial load fails
     */
    suspend fun retryProductLoading(): Boolean {
        Log.d(TAG, "🔄 Retrying product loading...")
        
        // Ensure connection first
        if (!ensureInitializedAndConnected()) {
            return false
        }
        
        // Retry product query
        querySubscriptionProducts()
        
        // Wait a bit for the query to complete
        delay(2000)
        
        val hasProducts = _subscriptionProducts.value.isNotEmpty()
        Log.d(TAG, if (hasProducts) "✅ Product retry successful" else "❌ Product retry failed")
        
        return hasProducts
    }

    /**
     * STEP 4: Query user's current subscriptions
     */
    private suspend fun queryUserSubscriptions() {
        if (billingClient?.isReady != true) {
            Log.w(TAG, "BillingClient not ready for subscription query")
            return
        }

        try {
            Log.d(TAG, "STEP 4: 🔍 Querying user subscriptions")
            
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            withContext(Dispatchers.IO) {
                val result = billingClient?.queryPurchasesAsync(params)
                
                when (result?.billingResult?.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        val purchases = result.purchasesList ?: emptyList()
                        Log.d(TAG, "STEP 4: ✅ Found ${purchases.size} user subscriptions")
                        
                        val activePurchases = purchases.filter { 
                            it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                            it.products.contains(SUBSCRIPTION_PRODUCT_ID)
                        }
                        
                        _userSubscriptions.value = activePurchases
                        
                        // Update UserManager subscription status based on query results
                        val hasActiveSubscription = activePurchases.isNotEmpty()
                        val newStatus = if (hasActiveSubscription) "pro" else "free"
                        
                        try {
                            val userManager = UserManager.getInstance()
                            val currentStatus = if (userManager.isPremiumUser()) "pro" else "free"
                            if (currentStatus != newStatus) {
                                Log.d(TAG, "STEP 4: 🔄 Subscription status changed from $currentStatus to $newStatus")
                                val result = userManager.updateSubscriptionStatus(newStatus)
                                if (result.isSuccess) {
                                    Log.d(TAG, "STEP 4: ✅ User subscription status synced to '$newStatus'")
                                } else {
                                    Log.e(TAG, "STEP 4: ❌ Failed to sync subscription status: ${result.exceptionOrNull()}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "STEP 4: ❌ Exception syncing subscription status", e)
                        }
                    }
                    else -> {
                        Log.e(TAG, "STEP 4: ❌ Failed to query subscriptions: ${result?.billingResult?.debugMessage}")
                        _userSubscriptions.value = emptyList()
                        
                        // If query failed, don't change subscription status
                        Log.w(TAG, "STEP 4: ⚠️ Keeping current subscription status due to query failure")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "STEP 4: ❌ Error querying user subscriptions", e)
            _userSubscriptions.value = emptyList()
        }
    }

    /**
     * STEP 6: Launch billing flow for subscription purchase with enhanced security and validation
     */
    fun launchBillingFlow(
        activity: Activity, 
        productDetails: ProductDetails, 
        offerToken: String,
        obfuscatedAccountId: String? = null
    ) {
        Log.d(TAG, "🚀 STEP 6: launchBillingFlow() called")
        Log.d(TAG, "📱 Activity: ${activity.javaClass.simpleName}")
        Log.d(TAG, "📦 Product ID: ${productDetails.productId}")
        Log.d(TAG, "🎫 Offer token: ${offerToken.take(10)}...")
        Log.d(TAG, "🔐 Obfuscated account ID: ${obfuscatedAccountId != null}")
        
        Toast.makeText(context, "🚀 BillingManager.launchBillingFlow() called", Toast.LENGTH_SHORT).show()
        
        // Prevent multiple concurrent purchases
        if (_purchaseInProgress.value) {
            Log.w(TAG, "STEP 6: ⚠️ Purchase already in progress, ignoring request")
            Toast.makeText(context, "⚠️ Purchase already in progress", Toast.LENGTH_SHORT).show()
            return
        }

        // Pre-purchase validation: Check if user already has active subscription
        if (hasActiveSubscription()) {
            Log.w(TAG, "STEP 6: ⚠️ User already has active subscription")
            Toast.makeText(context, "⚠️ Already subscribed", Toast.LENGTH_SHORT).show()
            _purchaseResult.value = PurchaseResult(
                BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED)
                    .setDebugMessage("User already has active subscription")
                    .build(),
                null
            )
            return
        }

        if (billingClient?.isReady != true) {
            Log.e(TAG, "STEP 6: ❌ BillingClient not ready for purchase. Ready: ${billingClient?.isReady}")
            Log.e(TAG, "STEP 6: ❌ Connection state: ${_connectionState.value}")
            Toast.makeText(context, "❌ BillingClient not ready: ${_connectionState.value}", Toast.LENGTH_LONG).show()
            _purchaseResult.value = PurchaseResult(
                BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                    .setDebugMessage("Billing service not ready")
                    .build(),
                null
            )
            return
        }

        // Check if subscriptions are supported
        val subscriptionsSupported = billingClient?.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
        if (subscriptionsSupported?.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "STEP 6: ❌ Subscriptions not supported on this device")
            _purchaseResult.value = PurchaseResult(
                BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED)
                    .setDebugMessage("Subscriptions not supported")
                    .build(),
                null
            )
            return
        }

        // Validate product and offer token
        if (productDetails.productId != SUBSCRIPTION_PRODUCT_ID) {
            Log.e(TAG, "STEP 6: ❌ Invalid product ID: ${productDetails.productId}")
            _purchaseResult.value = PurchaseResult(
                BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.ERROR)
                    .setDebugMessage("Invalid product ID")
                    .build(),
                null
            )
            return
        }

        try {
            _purchaseInProgress.value = true
            Log.d(TAG, "STEP 6: 🚀 Launching billing flow for product: ${productDetails.productId}")
            
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build()
            )

            val billingFlowParamsBuilder = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
            
            // Add obfuscated account ID for fraud prevention (Google recommendation)
            obfuscatedAccountId?.let { accountId ->
                billingFlowParamsBuilder.setObfuscatedAccountId(accountId)
                Log.d(TAG, "STEP 6: 🔒 Added obfuscated account ID for fraud prevention")
            }

            val billingFlowParams = billingFlowParamsBuilder.build()
            
            Log.d(TAG, "STEP 6: 🎯 About to call billingClient.launchBillingFlow()")
            Log.d(TAG, "STEP 6: 📱 Activity class: ${activity::class.java.simpleName}")
            
            Toast.makeText(context, "🎯 Calling Google Play...", Toast.LENGTH_SHORT).show()
            
            val billingResult = billingClient?.launchBillingFlow(activity, billingFlowParams)
            
            Log.d(TAG, "STEP 6: 📊 launchBillingFlow() returned:")
            Log.d(TAG, "STEP 6: 📊 Response code: ${billingResult?.responseCode}")
            Log.d(TAG, "STEP 6: 📊 Debug message: ${billingResult?.debugMessage}")
            
            if (billingResult?.responseCode != BillingClient.BillingResponseCode.OK) {
                _purchaseInProgress.value = false
                Log.e(TAG, "STEP 6: ❌ Failed to launch billing flow: Code ${billingResult?.responseCode} - ${billingResult?.debugMessage}")
                Log.e(TAG, "STEP 6: ❌ Expected: ${BillingClient.BillingResponseCode.OK}")
                Toast.makeText(context, "❌ Launch failed: Code ${billingResult?.responseCode}", Toast.LENGTH_LONG).show()
                _purchaseResult.value = PurchaseResult(
                    billingResult ?: BillingResult.newBuilder()
                        .setResponseCode(BillingClient.BillingResponseCode.ERROR)
                        .setDebugMessage("Unknown error launching billing flow")
                        .build(),
                    null
                )
            } else {
                Log.d(TAG, "STEP 6: ✅ Billing flow launched successfully - Google Play purchase screen should appear")
                Toast.makeText(context, "✅ Google Play launched successfully!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            _purchaseInProgress.value = false
            Log.e(TAG, "STEP 6: ❌ Error launching billing flow", e)
            _purchaseResult.value = PurchaseResult(
                BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.ERROR)
                    .setDebugMessage("Exception: ${e.message}")
                    .build(),
                null
            )
        }
    }

    /**
     * STEP 6 & 7: Called when purchases are updated with enhanced error handling
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        // Reset purchase in progress state
        _purchaseInProgress.value = false
        
        Log.d(TAG, "STEP 6: 📦 Purchases updated: ${billingResult.responseCode} - ${billingResult.debugMessage}")
        
        _purchaseResult.value = PurchaseResult(billingResult, purchases)
        
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                Log.d(TAG, "STEP 6: ✅ Purchase successful")
                purchases?.let { 
                    // STEP 7 & 8: Verify and process purchases
                    processPurchases(it) 
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "STEP 6: 🚫 User canceled purchase")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.d(TAG, "STEP 6: 🔄 Item already owned - refreshing subscription state")
                // Query existing purchases to update state
                coroutineScope.launch {
                    queryUserSubscriptions()
                }
            }
            BillingClient.BillingResponseCode.NETWORK_ERROR -> {
                Log.e(TAG, "STEP 6: 🌐 Network error during purchase")
            }
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                Log.e(TAG, "STEP 6: 🚫 Google Play service unavailable")
            }
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                Log.e(TAG, "STEP 6: 🚫 Billing unavailable on this device")
            }
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                Log.e(TAG, "STEP 6: 👨‍💻 Developer error - check product configuration")
            }
            BillingClient.BillingResponseCode.ERROR -> {
                Log.e(TAG, "STEP 6: ❌ Purchase error: ${billingResult.debugMessage}")
            }
            else -> {
                Log.e(TAG, "STEP 6: ❓ Unknown billing result: ${billingResult.responseCode} - ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * STEP 7 & 8: Process completed purchases - Verify and Grant Content
     */
    private fun processPurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            Log.d(TAG, "STEP 7: 🔍 Processing purchase: ${purchase.products}")
            
            // Check purchase state
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    // STEP 7: Verify purchase (implement your verification logic)
                    if (verifyPurchase(purchase)) {
                        // STEP 8: Grant entitlement to user
                        grantSubscriptionEntitlement(purchase)
                        // STEP 8: Acknowledge purchase if not already acknowledged (for subscriptions)
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                    }
                }
                Purchase.PurchaseState.PENDING -> {
                    Log.d(TAG, "STEP 7: ⏳ Purchase is pending: ${purchase.products}")
                    // Don't grant entitlement yet, wait for purchase to complete
                }
                else -> {
                    Log.w(TAG, "STEP 7: ❓ Unknown purchase state: ${purchase.purchaseState}")
                }
            }
        }
    }

    /**
     * STEP 7: Verify purchase authenticity following Google Play Billing security guidelines
     * 
     * ⚠️ IMPORTANT: In production, this should be done on your secure backend server
     * Current implementation does basic client-side validation
     * TODO: Implement server-side verification using Google Play Developer API
     */
    private fun verifyPurchase(purchase: Purchase): Boolean {
        Log.d(TAG, "STEP 7: 🔐 Verifying purchase: ${purchase.orderId}")
        
        // Basic client-side validation
        val isValidPurchase = purchase.purchaseToken.isNotEmpty() &&
               purchase.products.contains(SUBSCRIPTION_PRODUCT_ID) &&
               purchase.packageName == context.packageName &&
               purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        
        if (!isValidPurchase) {
            Log.w(TAG, "STEP 7: ❌ Purchase failed basic validation")
            return false
        }
        
        // TODO: Server-side verification
        // 1. Send purchase token to your secure backend
        // 2. Backend calls Google Play Developer API to verify:
        //    - Purchase authenticity using signature validation
        //    - Purchase status and subscription details
        //    - Token validity and expiration
        // 3. Backend responds with verification result
        // 4. Only proceed if verification passes
        
        Log.d(TAG, "STEP 7: ✅ Purchase passed client-side validation")
        return true
    }

    /**
     * STEP 8: Grant subscription entitlement to user
     */
    private fun grantSubscriptionEntitlement(purchase: Purchase) {
        Log.d(TAG, "STEP 8: 🎉 Granting subscription entitlement: ${purchase.products}")
        
        // Update subscription state through state flow
        val subscriptions = _userSubscriptions.value.toMutableList()
        
        // Remove any existing subscription with same product and add the new one
        subscriptions.removeAll { it.products.any { product -> purchase.products.contains(product) } }
        subscriptions.add(purchase)
        _userSubscriptions.value = subscriptions
        
        // Update user's subscription status in UserManager/Firestore
        if (purchase.products.contains(SUBSCRIPTION_PRODUCT_ID)) {
            coroutineScope.launch {
                try {
                    val userManager = UserManager.getInstance()
                    val result = userManager.updateSubscriptionStatus("pro")
                    if (result.isSuccess) {
                        Log.d(TAG, "STEP 8: ✅ User subscription status updated to 'pro' in Firestore")
                    } else {
                        Log.e(TAG, "STEP 8: ❌ Failed to update user subscription status: ${result.exceptionOrNull()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "STEP 8: ❌ Exception updating user subscription status", e)
                }
            }
        }
        
        Log.d(TAG, "STEP 8: ✅ Subscription entitlement granted successfully")
    }

    /**
     * STEP 8: Acknowledge purchase following Google Play Billing guidelines
     * Must be called within 3 days to prevent refund
     * 
     * Note: For subscriptions, use acknowledgePurchase()
     * For consumable products, use consumeAsync()
     */
    private fun acknowledgePurchase(purchase: Purchase) {
        Log.d(TAG, "STEP 8: ✅ Acknowledging purchase: ${purchase.products}")
        
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val result = billingClient?.acknowledgePurchase(acknowledgePurchaseParams)
                
                when (result?.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        Log.d(TAG, "STEP 8: ✅ Purchase acknowledged successfully")
                    }
                    else -> {
                        Log.e(TAG, "STEP 8: ❌ Failed to acknowledge purchase: ${result?.debugMessage}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "STEP 8: ❌ Error acknowledging purchase", e)
            }
        }
    }

    /**
     * STEP 5: Get subscription product with caching (for displaying products)
     */
    fun getSubscriptionProduct(): ProductDetails? {
        // Check cache first
        if (cachedSubscriptionProduct != null && 
            System.currentTimeMillis() - lastCacheUpdate < cacheValidDuration) {
            return cachedSubscriptionProduct
        }
        
        // Find product in current list
        val product = _subscriptionProducts.value.find { it.productId == SUBSCRIPTION_PRODUCT_ID }
        
        // Update cache
        cachedSubscriptionProduct = product
        lastCacheUpdate = System.currentTimeMillis()
        
        return product
    }

    /**
     * Get the offer token for the subscription following official documentation
     * From ProductDetails, get SubscriptionOfferDetails list and retrieve specific offerToken
     */
    fun getSubscriptionOfferToken(): String? {
        val product = getSubscriptionProduct()
        
        if (product == null) {
            Log.e(TAG, "❌ No subscription product available")
            return null
        }
        
        // Get the list of SubscriptionOfferDetails as per documentation
        val subscriptionOfferDetails = product.subscriptionOfferDetails
        
        if (subscriptionOfferDetails.isNullOrEmpty()) {
            Log.e(TAG, "❌ No subscription offers available for product: ${product.productId}")
            return null
        }
        
        // Find the specific offer for our base plan
        val targetOffer = subscriptionOfferDetails.find { offerDetail ->
            offerDetail.basePlanId == SUBSCRIPTION_BASE_PLAN_ID
        }
        
        if (targetOffer != null) {
            Log.d(TAG, "✅ Found offer token for base plan: $SUBSCRIPTION_BASE_PLAN_ID")
            Log.d(TAG, "📋 Offer details: Base plan=${targetOffer.basePlanId}, Token=${targetOffer.offerToken.take(10)}...")
            return targetOffer.offerToken
        }
        
        // Fallback: use first available offer (common practice)
        val fallbackOffer = subscriptionOfferDetails.first()
        Log.w(TAG, "⚠️ Base plan '$SUBSCRIPTION_BASE_PLAN_ID' not found, using fallback: ${fallbackOffer.basePlanId}")
        Log.d(TAG, "📋 Fallback offer: Base plan=${fallbackOffer.basePlanId}, Token=${fallbackOffer.offerToken.take(10)}...")
        
        return fallbackOffer.offerToken
    }

    /**
     * Check if user has active subscription with detailed state analysis
     * Subscription states: Active, Cancelled, Grace Period, On Hold, Expired
     */
    fun hasActiveSubscription(): Boolean {
        val subscriptions = _userSubscriptions.value
        
        return subscriptions.any { purchase ->
            val isCorrectProduct = purchase.products.contains(SUBSCRIPTION_PRODUCT_ID)
            val isPurchased = purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            val isAcknowledged = purchase.isAcknowledged
            
            Log.d(TAG, "🔍 Checking subscription:")
            Log.d(TAG, "   - Product: ${purchase.products}")
            Log.d(TAG, "   - State: ${purchase.purchaseState}")
            Log.d(TAG, "   - Acknowledged: $isAcknowledged")
            Log.d(TAG, "   - Order ID: ${purchase.orderId}")
            
            // For subscriptions, we consider it active if:
            // 1. It's the correct product
            // 2. Purchase state is PURCHASED (covers Active, Cancelled with access, Grace Period)
            // 3. Purchase is acknowledged (required for subscriptions)
            isCorrectProduct && isPurchased && isAcknowledged
        }
    }

    /**
     * Get detailed subscription status information
     */
    fun getSubscriptionStatus(): String {
        val subscriptions = _userSubscriptions.value
        
        if (subscriptions.isEmpty()) {
            return "No subscriptions found"
        }
        
        val subscription = subscriptions.find { it.products.contains(SUBSCRIPTION_PRODUCT_ID) }
        
        return when {
            subscription == null -> "Not subscribed"
            subscription.purchaseState == Purchase.PurchaseState.PURCHASED && subscription.isAcknowledged -> {
                "Active subscription (Order: ${subscription.orderId})"
            }
            subscription.purchaseState == Purchase.PurchaseState.PURCHASED && !subscription.isAcknowledged -> {
                "Pending acknowledgment"
            }
            subscription.purchaseState == Purchase.PurchaseState.PENDING -> {
                "Payment pending"
            }
            else -> "Unknown state: ${subscription.purchaseState}"
        }
    }

    /**
     * Refresh subscription data (STEP 4 repeat)
     */
    suspend fun refreshSubscriptionData() {
        Log.d(TAG, "🔄 Refreshing subscription data")
        try {
            querySubscriptionProducts()
            queryUserSubscriptions()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error refreshing subscription data", e)
        }
    }

    /**
     * Force re-initialization of billing client
     */
    fun forceReinitialize() {
        Log.d(TAG, "🔄 Force re-initializing BillingManager")
        isInitialized = false
        billingClient?.endConnection()
        billingClient = null
        _connectionState.value = BillingConnectionState.DISCONNECTED
        
        coroutineScope.launch {
            try {
                initializeBillingClient()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error during force re-initialization", e)
                _connectionState.value = BillingConnectionState.ERROR
            }
        }
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        Log.d(TAG, "🗑️ Destroying BillingManager")
        billingClient?.endConnection()
        billingClient = null
        coroutineScope.cancel()
    }

    /**
     * Get user-friendly error message for billing result codes
     */
    fun getUserFriendlyErrorMessage(responseCode: Int): String {
        return when (responseCode) {
            BillingClient.BillingResponseCode.USER_CANCELED -> "Purchase was canceled"
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "You already have an active subscription"
            BillingClient.BillingResponseCode.NETWORK_ERROR -> "Network error. Please check your connection and try again"
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "Google Play service is temporarily unavailable. Please try again later"
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Billing is not available on this device"
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> "Subscriptions are not supported on this device"
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "There's an issue with the app configuration. Please contact support"
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> "Connection to Google Play lost. Please try again"
            BillingClient.BillingResponseCode.ERROR -> "An error occurred. Please try again"
            else -> "Something went wrong. Please try again later"
        }
    }
}
