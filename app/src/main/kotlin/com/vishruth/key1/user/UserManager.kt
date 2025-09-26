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
import android.content.SharedPreferences
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
    
    // Context for SharedPreferences
    private var appContext: Context? = null
    private var sharedPrefs: SharedPreferences? = null
    
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
        // Set initial local user data (will be updated after context is available)
        _userData.value = getLocalUserData()
    }
    
    companion object {
        private const val TAG = "UserManager"
        private const val PREFS_NAME = "user_manager_prefs"
        private const val KEY_LAST_REWARDED_AD_DATE = "last_rewarded_ad_date"
        private const val KEY_TOTAL_AD_REWARDS_USED = "total_ad_rewards_used"
        private const val KEY_PRO_FEATURES_TOAST_SHOWN = "pro_features_toast_shown"
        private const val KEY_FIRST_TIME_SUBSCRIBER = "first_time_subscriber"
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
        appContext = context.applicationContext
        sharedPrefs = appContext!!.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.d(TAG, "Initializing UserManager asynchronously")
        
        // Load persisted user data
        _userData.value = getLocalUserData()
        
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
     * Initialize billing and subscription managers
     */
    private suspend fun initializeBillingManagers(context: Context) {
        withContext(Dispatchers.Main) {
            // Initialize BillingManager
            billingManager = BillingManager(context)
            
            // Initialize SubscriptionManager
            subscriptionManager = SubscriptionManager(context, billingManager!!)
            
            Log.d(TAG, "Billing managers initialized")
        }
    }
    
    /**
     * Get local user data (simulated - replace with actual data management)
     */
    private fun getLocalUserData(): UserData {
        val prefs = sharedPrefs
        
        return if (prefs != null) {
            // Load from SharedPreferences
            val lastRewardedAdDate = prefs.getLong(KEY_LAST_REWARDED_AD_DATE, 0L)
            val totalAdRewardsUsed = prefs.getInt(KEY_TOTAL_AD_REWARDS_USED, 0)
            
            UserData(
                userId = "local_user",
                displayName = "Local User",
                email = "user@device.local",
                lastRewardedAdDate = if (lastRewardedAdDate == 0L) null else lastRewardedAdDate,
                totalAdRewardsUsed = totalAdRewardsUsed
            )
        } else {
            // Fallback to default data
            UserData(
                userId = "local_user",
                displayName = "Local User",
                email = "user@device.local",
                lastRewardedAdDate = null,
                totalAdRewardsUsed = 0
            )
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
    
    /**
     * Record that user has used a rewarded ad
     * Updates lastRewardedAdDate to current time and increments totalAdRewardsUsed
     */
    suspend fun recordRewardedAdUsage() {
        val currentTime = System.currentTimeMillis()
        val currentData = _userData.value ?: getLocalUserData()
        
        val updatedData = currentData.copy(
            lastRewardedAdDate = currentTime,
            totalAdRewardsUsed = currentData.totalAdRewardsUsed + 1
        )
        
        // Save to SharedPreferences
        val prefs = sharedPrefs
        if (prefs != null) {
            prefs.edit()
                .putLong(KEY_LAST_REWARDED_AD_DATE, currentTime)
                .putInt(KEY_TOTAL_AD_REWARDS_USED, updatedData.totalAdRewardsUsed)
                .apply()
            Log.d(TAG, "Saved rewarded ad usage to SharedPreferences")
        }
        
        _userData.value = updatedData
        Log.d(TAG, "Recorded rewarded ad usage at: $currentTime")
    }
    
    /**
     * Update subscription status in user data
     */
    private fun updateSubscriptionStatus(isActive: Boolean) {
        val currentData = _userData.value ?: getLocalUserData()
        val updatedData = currentData.copy(
            subscriptionStatus = if (isActive) "premium" else "free"
        )
        _userData.value = updatedData
        Log.d(TAG, "Updated subscription status: ${updatedData.subscriptionStatus}")
    }
    
    /**
     * Get the billing manager instance
     */
    fun getBillingManager(): BillingManager? = billingManager
    
    /**
     * Get the subscription manager instance
     */
    fun getSubscriptionManager(): SubscriptionManager? = subscriptionManager
    
    /**
     * Restore purchases
     */
    fun restorePurchases(callback: (Boolean, String?) -> Unit) {
        Log.d(TAG, "Restoring purchases...")
        billingManager?.restorePurchases { success, message ->
            Log.d(TAG, "Restore purchases result: success=$success, message=$message")
            callback(success, message)
        }
    }
    
    /**
     * Check if the user has an active subscription
     */
    suspend fun hasActiveSubscription(): Boolean {
        return billingManager?.hasActiveSubscription() == true
    }
    
    /**
     * Simple wrapper for restore purchases
     * This is useful for "Restore Purchases" buttons in settings
     */
    fun handleRestorePurchases(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        restorePurchases { success, message ->
            if (success) {
                onSuccess(message ?: "Purchases restored successfully")
            } else {
                onError(message ?: "Failed to restore purchases")
            }
        }
    }
    
    /**
     * Check if the pro features unlocked toast has been shown
     */
    fun hasProFeaturesToastBeenShown(): Boolean {
        val prefs = sharedPrefs ?: return false
        return prefs.getBoolean(KEY_PRO_FEATURES_TOAST_SHOWN, false)
    }
    
    /**
     * Mark that the pro features unlocked toast has been shown
     */
    fun markProFeaturesToastAsShown() {
        val prefs = sharedPrefs
        if (prefs != null) {
            prefs.edit()
                .putBoolean(KEY_PRO_FEATURES_TOAST_SHOWN, true)
                .apply()
            Log.d(TAG, "Marked pro features toast as shown")
        }
    }
    
    /**
     * Check if this is the user's first time subscribing (eligible for discount)
     */
    fun isFirstTimeSubscriber(): Boolean {
        val prefs = sharedPrefs ?: return true // Default to true if no prefs
        return prefs.getBoolean(KEY_FIRST_TIME_SUBSCRIBER, true)
    }
    
    /**
     * Mark user as no longer first-time subscriber (after successful purchase)
     */
    fun markAsNotFirstTimeSubscriber() {
        val prefs = sharedPrefs
        if (prefs != null) {
            prefs.edit()
                .putBoolean(KEY_FIRST_TIME_SUBSCRIBER, false)
                .apply()
            Log.d(TAG, "Marked user as not first-time subscriber - no longer eligible for discount")
        }
    }
}

/**
 * Data class representing user information and subscription status
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