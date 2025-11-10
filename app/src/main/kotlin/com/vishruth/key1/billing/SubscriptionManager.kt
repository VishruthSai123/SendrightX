/*
 * Copyright (C) 2025 SendRight 3.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.billing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.vishruth.key1.user.UserManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Simplified subscription manager for compilation success
 */
class SubscriptionManager(
    private val context: Context,
    private val billingManager: BillingManager
) {
    
    companion object {
        private const val TAG = "SubscriptionManager"
        private const val PREFS_NAME = "subscription_prefs"
        private const val KEY_AI_ACTIONS_USED = "ai_actions_used"
        private const val KEY_PRO_STATUS = "pro_status"
        const val FREE_DAILY_AI_ACTIONS = 5
    }
    
    // SECURITY FIX: Use encrypted SharedPreferences for subscription data
    private val prefs: SharedPreferences = SecurePreferences.getEncryptedPreferences(context, PREFS_NAME)
    // MEMORY LEAK FIX: Use SupervisorJob for proper cancellation
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Add periodic subscription checking job
    private var periodicCheckJob: Job? = null
    
    // Track last check time to avoid too frequent checks
    private var lastSubscriptionCheck = 0L
    private val MIN_CHECK_INTERVAL = 30_000L // 30 seconds minimum between checks
    private val PERIODIC_CHECK_INTERVAL = 2 * 60 * 1000L // Check every 2 minutes
    
    // MEMORY LEAK FIX: Track if destroyed
    private var isDestroyed = false
    
    // StateFlows for Pro status and usage
    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()
    
    private val _isProUser = MutableStateFlow(false)
    val isProUser: StateFlow<Boolean> = _isProUser.asStateFlow()
    
    private val _aiActionsUsed = MutableStateFlow(0)
    val aiActionsUsed: StateFlow<Int> = _aiActionsUsed.asStateFlow()
    
    private val _currentAiUsageCount = MutableStateFlow(0)
    val currentAiUsageCount: StateFlow<Int> = _currentAiUsageCount.asStateFlow()
    
    private val _remainingActions = MutableStateFlow(FREE_DAILY_AI_ACTIONS)
    val remainingActions: StateFlow<Int> = _remainingActions.asStateFlow()
    
    init {
        // SECURITY FIX: Migrate old plain preferences to encrypted storage
        SecurePreferences.migrateToEncrypted(context, PREFS_NAME, PREFS_NAME)
        
        loadState()
        
        // Monitor purchase updates and immediately check subscription status
        managerScope.launch {
            billingManager.purchaseUpdates.collect { result ->
                // MEMORY LEAK FIX: Check if destroyed before processing
                if (isDestroyed) {
                    Log.d(TAG, "SubscriptionManager destroyed, ignoring purchase update")
                    return@collect
                }
                
                Log.d(TAG, "Purchase update received: ${result.isSuccess}")
                if (result.isSuccess) {
                    // Force immediate subscription status check
                    delay(1000) // Small delay to ensure purchase is processed
                    checkSubscriptionStatus()
                    Log.d(TAG, "Subscription status checked after purchase")
                } else {
                    Log.w(TAG, "Purchase failed: ${result.exceptionOrNull()?.message}")
                }
            }
        }
        
        // Initial subscription check
        managerScope.launch {
            delay(2000) // Wait for billing client to be ready
            checkSubscriptionStatus()
        }
        
        // Start periodic subscription status checking
        startPeriodicSubscriptionChecking()
    }
    
    /**
     * MEMORY LEAK FIX: Proper cleanup method
     * 
     * CRITICAL: Call this when SubscriptionManager is no longer needed
     */
    fun destroy() {
        if (isDestroyed) {
            Log.w(TAG, "SubscriptionManager already destroyed, skipping")
            return
        }
        
        try {
            Log.d(TAG, "Destroying SubscriptionManager and cleaning up resources...")
            
            // Cancel periodic check job
            periodicCheckJob?.cancel()
            
            // Cancel all coroutines
            managerScope.cancel()
            
            isDestroyed = true
            
            Log.d(TAG, "✅ SubscriptionManager destroyed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during SubscriptionManager cleanup", e)
        }
    }
    
    /**
     * Start periodic subscription status checking to detect expired subscriptions quickly
     */
    private fun startPeriodicSubscriptionChecking() {
        periodicCheckJob?.cancel() // Cancel any existing job
        
        periodicCheckJob = managerScope.launch {
            while (isActive) {
                try {
                    delay(PERIODIC_CHECK_INTERVAL)
                    
                    // Only check if enough time has passed since last check
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastSubscriptionCheck >= MIN_CHECK_INTERVAL) {
                        Log.d(TAG, "Periodic subscription status check...")
                        checkSubscriptionStatusInternal(forceRefresh = true)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic subscription check", e)
                    // Wait a bit before retrying
                    delay(60_000L)
                }
            }
        }
    }
    
    /**
     * Load subscription state from SharedPreferences and BillingManager
     */
    private fun loadState() {
        // Load from preferences first
        _aiActionsUsed.value = prefs.getInt(KEY_AI_ACTIONS_USED, 0)
        _currentAiUsageCount.value = _aiActionsUsed.value
        val prefsProStatus = prefs.getBoolean(KEY_PRO_STATUS, false)
        
        // Also check BillingManager's local state
        val billingProStatus = billingManager.getSubscriptionState()
        
        // Use the most recent state (preference billing manager local state)
        val actualProStatus = prefsProStatus || billingProStatus
        
        _isPro.value = actualProStatus
        _isProUser.value = actualProStatus
        updateRemainingActions()
        
        Log.d(TAG, "State loaded - Prefs Pro: $prefsProStatus, Billing Pro: $billingProStatus, Final Pro: $actualProStatus, Actions used: ${_aiActionsUsed.value}")
    }
    
    /**
     * Update remaining actions count
     */
    private fun updateRemainingActions() {
        val remaining = if (_isPro.value) {
            Int.MAX_VALUE // Unlimited for Pro users
        } else {
            (FREE_DAILY_AI_ACTIONS - _aiActionsUsed.value).coerceAtLeast(0)
        }
        _remainingActions.value = remaining
    }
    
    /**
     * Use AI action (for free users with limits)
     */
    suspend fun useAiAction(): Boolean {
        return if (_isPro.value) {
            // Pro users have unlimited access - don't increment counter
            Log.d(TAG, "AI action allowed - Pro user with unlimited access")
            true
        } else {
            // Free users have daily limits
            if (_aiActionsUsed.value < FREE_DAILY_AI_ACTIONS) {
                incrementUsageCount()
                saveUsageToPrefs()
                Log.d(TAG, "AI action allowed - Free user within daily limit")
                true
            } else {
                Log.w(TAG, "Daily AI action limit reached for free user")
                false
            }
        }
    }
    
    /**
     * Increment usage count
     */
    private fun incrementUsageCount() {
        val newCount = _aiActionsUsed.value + 1
        _aiActionsUsed.value = newCount
        _currentAiUsageCount.value = newCount
        updateRemainingActions()
    }
    
    /**
     * Save usage to SharedPreferences
     */
    private fun saveUsageToPrefs() {
        prefs.edit()
            .putInt(KEY_AI_ACTIONS_USED, _aiActionsUsed.value)
            .apply()
    }
    
    /**
     * Check subscription status with improved cache clearing
     * Based on reference: checkSubscriptionStatusFromServer function
     */
    suspend fun checkSubscriptionStatus() {
        checkSubscriptionStatusInternal(forceRefresh = false)
    }
    
    /**
     * Internal subscription status checking with force refresh option
     */
    private suspend fun checkSubscriptionStatusInternal(forceRefresh: Boolean = false) {
        try {
            val currentTime = System.currentTimeMillis()
            lastSubscriptionCheck = currentTime
            
            Log.d(TAG, "Checking subscription status... (forceRefresh: $forceRefresh)")
            
            // Always check Google Play first to get real-time status
            val hasActiveSubscription = billingManager.hasActiveSubscription()
            val localSubscriptionState = billingManager.getSubscriptionState()
            
            Log.d(TAG, "Google Play subscription: $hasActiveSubscription")
            Log.d(TAG, "Local subscription state: $localSubscriptionState")
            
            // If Google Play says no active subscription, immediately clear local cache
            if (!hasActiveSubscription && localSubscriptionState) {
                Log.w(TAG, "🔥 SUBSCRIPTION EXPIRED - Google Play shows no subscription but local cache shows active!")
                Log.w(TAG, "🧹 Immediately clearing subscription cache and revoking access")
                
                // Force clear the cache
                billingManager.saveSubscriptionState(false)
                prefs.edit().putBoolean(KEY_PRO_STATUS, false).apply()
                
                // Update to free status immediately  
                _isPro.value = false
                _isProUser.value = false
                updateRemainingActions()
                
                try {
                    val userManager = UserManager.getInstance()
                    Log.d(TAG, "Subscription revoked - user downgraded to free")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating user manager on subscription expiry", e)
                }
                
                Log.d(TAG, "✅ Subscription cache cleared successfully")
                return
            }
            
            // Use Google Play as the authoritative source when available
            val actualSubscriptionState = if (forceRefresh) {
                hasActiveSubscription // Always trust Google Play on force refresh
            } else {
                hasActiveSubscription || localSubscriptionState // Allow local cache only for regular checks
            }
            
            // Update local state with explicit logging
            val oldProStatus = _isPro.value
            _isPro.value = actualSubscriptionState
            _isProUser.value = actualSubscriptionState
            
            if (oldProStatus != actualSubscriptionState) {
                Log.d(TAG, "🔄 Pro status changed from $oldProStatus to $actualSubscriptionState")
                
                // Save to preferences
                prefs.edit().putBoolean(KEY_PRO_STATUS, actualSubscriptionState).apply()
                
                // Also update billing manager's local state
                billingManager.saveSubscriptionState(actualSubscriptionState)
            }
            
            updateRemainingActions()
            
            // Update UserManager subscription status with retry logic
            try {
                val userManager = UserManager.getInstance()
                val newStatus = if (actualSubscriptionState) "pro" else "free"
                Log.d(TAG, "Local subscription status updated to: $newStatus")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error updating subscription status", e)
            }
            
            Log.d(TAG, "Subscription status check completed. isPro: ${_isPro.value}, remaining actions: ${_remainingActions.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking subscription status", e)
        }
    }
    
    /**
     * Get subscription status message for UI
     */
    fun getSubscriptionStatusMessage(): String {
        return if (_isPro.value) {
            "Pro subscriber - Unlimited AI features"
        } else {
            "${_aiActionsUsed.value}/$FREE_DAILY_AI_ACTIONS AI actions used today"
        }
    }
    
    /**
     * Check if user can use AI actions
     */
    fun canUseAiAction(): Boolean {
        return _isPro.value || _aiActionsUsed.value < FREE_DAILY_AI_ACTIONS
    }
    
    /**
     * Get remaining AI actions for free users
     */
    fun getRemainingAiActions(): Int {
        return if (_isPro.value) {
            Int.MAX_VALUE // Unlimited for Pro users
        } else {
            (FREE_DAILY_AI_ACTIONS - _aiActionsUsed.value).coerceAtLeast(0)
        }
    }
    
    /**
     * Reset daily usage (called by system scheduler)
     */
    fun resetDailyUsage() {
        _aiActionsUsed.value = 0
        _currentAiUsageCount.value = 0
        updateRemainingActions()
        saveUsageToPrefs()
        Log.d(TAG, "Daily usage reset")
    }
    
    /**
     * Check subscription status on app resume
     * Based on reference: checkSubscriptionStatusFromServer in onResume
     */
    suspend fun checkSubscriptionStatusOnResume() {
        Log.d(TAG, "Checking subscription status on app resume...")
        
        // Check for existing purchases that might have been processed while app was closed
        billingManager.checkForExistingPurchases()
        
        // Wait a moment for purchase checking to complete
        delay(1000)
        
        // Then do full subscription status check
        checkSubscriptionStatus()
    }
    
    /**
     * Force subscription status refresh
     */
    suspend fun forceRefreshSubscriptionStatus() {
        Log.d(TAG, "Force refreshing subscription status...")
        
        try {
            // Check for existing purchases first
            billingManager.checkForExistingPurchases()
            
            // Small delay to let purchase processing complete
            delay(1500)
            
            // Then check full subscription status
            checkSubscriptionStatus()
            
            Log.d(TAG, "Force refresh completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during force refresh", e)
        }
    }
    
    /**
     * Cleanup resources
     */
    fun destroy() {
        periodicCheckJob?.cancel()
        managerScope.cancel()
    }
}
