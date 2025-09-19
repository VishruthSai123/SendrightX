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

package com.vishruth.key1.user

import android.content.Context
import android.util.Log
import com.vishruth.key1.billing.BillingManager
import com.vishruth.key1.billing.SubscriptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UserManager class to handle user profile management and subscription state
 */
class UserManager private constructor() {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    // Billing and subscription managers
    private var billingManager: BillingManager? = null
    private var subscriptionManager: SubscriptionManager? = null
    
    // State flow for emitting user data updates
    private val _userData = MutableStateFlow<UserData?>(null)
    val userData: StateFlow<UserData?> = _userData.asStateFlow()
    
    // State flow for authentication state (simplified - always authenticated for local user)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Authenticated(getLocalUserData()))
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    // Initialization state
    private var isInitialized = false
    private var isInitializing = false

    init {
        // Set initial local user data
        _userData.value = getLocalUserData()
    }
    
    companion object {
        private const val TAG = "UserManager"
        private var instance: UserManager? = null
        
        /**
         * Get the singleton instance of UserManager
         */
        @Synchronized
        fun getInstance(): UserManager {
            if (instance == null) {
                instance = UserManager()
            }
            return instance!!
        }
    }
    
    /**
     * Initialize the UserManager with the application context
     * This should be called during application startup
     *
     * @param context The application context
     */
    fun initialize(context: Context) {
        if (isInitialized || isInitializing) {
            Log.d(TAG, "UserManager already initialized or initializing, skipping")
            return
        }
        
        isInitializing = true
        Log.d(TAG, "Initializing UserManager asynchronously")
        
        // Start async initialization to avoid blocking main thread
        coroutineScope.launch {
            try {
                // Initialize billing managers
                initializeBillingManagers(context)
                
                isInitialized = true
                isInitializing = false
                Log.d(TAG, "UserManager initialization completed")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during UserManager initialization", e)
                isInitializing = false
                withContext(Dispatchers.Main) {
                    _authState.value = AuthState.Error(e)
                }
            }
        }
    }
    
    /**
     * Initialize billing managers asynchronously
     */
    private suspend fun initializeBillingManagers(context: Context) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing billing managers")
            billingManager = BillingManager(context)
            subscriptionManager = SubscriptionManager(context, billingManager!!)
            Log.d(TAG, "Billing managers initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing billing managers", e)
            billingManager = null
            subscriptionManager = null
        }
    }
    
    /**
     * Get local user data (no authentication required)
     */
    private fun getLocalUserData(): UserData {
        return UserData(
            userId = "local_user",
            email = "",
            displayName = "Local User",
            subscriptionStatus = "free",
            lastRewardedAdDate = null,
            totalAdRewardsUsed = 0
        )
    }
    
    /**
     * Check subscription status on app resume
     */
    suspend fun onAppResume() {
        try {
            Log.d(TAG, "App resumed - checking subscription status...")
            subscriptionManager?.checkSubscriptionStatusOnResume()
            Log.d(TAG, "App resume subscription check completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during app resume subscription check", e)
        }
    }

    /**
     * Check if user can use rewarded ad (once per month)
     *
     * @return true if user can use rewarded ad, false otherwise
     */
    fun canUseRewardedAd(): Boolean {
        val userData = _userData.value ?: return true // Allow if no user data
        val lastAdDate = userData.lastRewardedAdDate ?: return true // Allow if no previous ad usage
        
        val currentTime = System.currentTimeMillis()
        val oneMonthInMillis = 30L * 24L * 60L * 60L * 1000L // 30 days in milliseconds
        
        return (currentTime - lastAdDate) > oneMonthInMillis
    }
    
    // MARK: - Subscription Methods
    
    /**
     * Get the billing manager instance
     */
    fun getBillingManager(): BillingManager? = billingManager
    
    /**
     * Get the subscription manager instance
     */
    fun getSubscriptionManager(): SubscriptionManager? = subscriptionManager
    
    /**
     * Check if user is a premium subscriber
     */
    fun isPremiumUser(): Boolean {
        return subscriptionManager?.isPro?.value ?: false
    }
    
    /**
     * Check if user can use AI action
     */
    fun canUseAiAction(): Boolean {
        return subscriptionManager?.canUseAiAction() ?: true
    }
    
    /**
     * Use an AI action (increment counter for free users) with integrity verification
     */
    suspend fun useAiAction(): Boolean {
        return subscriptionManager?.useAiAction() ?: true
    }
    
    /**
     * Get remaining AI actions for free users
     */
    fun getRemainingAiActions(): Int {
        return subscriptionManager?.getRemainingAiActions() ?: -1
    }
    
    /**
     * Check if ads should be shown
     */
    fun shouldShowAds(): Boolean {
        return !isPremiumUser()
    }
    
    /**
     * Get subscription status message
     */
    fun getSubscriptionStatusMessage(): String {
        return subscriptionManager?.getSubscriptionStatusMessage() ?: "Free Plan"
    }
    
    /**
     * Restore purchases
     */
    fun restorePurchases(callback: (Boolean, String?) -> Unit) {
        Log.d(TAG, "Restoring purchases...")
        
        billingManager?.restorePurchases { success, message ->
            if (success) {
                Log.d(TAG, "Purchase restoration successful")
            } else {
                Log.w(TAG, "Purchase restoration failed: $message")
            }
            callback(success, message)
        }
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        subscriptionManager?.destroy()
        billingManager?.destroy()
    }
    
    /**
     * Trigger purchase restoration manually (can be called from UI)
     * This is useful for "Restore Purchases" buttons in settings
     */
    fun triggerPurchaseRestoration(callback: (Boolean, String?) -> Unit) {
        if (!isInitialized) {
            callback(false, "UserManager not initialized")
            return
        }
        
        restorePurchases(callback)
    }
}

/**
 * Data class representing user data
 */
data class UserData(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val subscriptionStatus: String = "free",
    val subscriptionExpiryTime: Long? = null,
    val lastRewardedAdDate: Long? = null,
    val totalAdRewardsUsed: Int = 0
)

/**
 * Sealed class representing authentication states
 */
sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val userData: UserData) : AuthState()
    data class Error(val exception: Exception) : AuthState()
}