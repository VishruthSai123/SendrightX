/*
 * Copyright (C) 2025 SendRight 3.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.billing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
        const val FREE_DAILY_AI_ACTIONS = 10
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
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
        loadState()
        
        // Monitor purchase updates
        managerScope.launch {
            billingManager.purchaseUpdates.collect { result ->
                if (result.isSuccess) {
                    checkSubscriptionStatus()
                }
            }
        }
    }
    
    /**
     * Load subscription state from SharedPreferences
     */
    private fun loadState() {
        _aiActionsUsed.value = prefs.getInt(KEY_AI_ACTIONS_USED, 0)
        _currentAiUsageCount.value = _aiActionsUsed.value
        _isPro.value = prefs.getBoolean(KEY_PRO_STATUS, false)
        _isProUser.value = _isPro.value
        updateRemainingActions()
        
        Log.d(TAG, "State loaded - Pro: ${_isPro.value}, Actions used: ${_aiActionsUsed.value}")
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
            // Pro users have unlimited access
            incrementUsageCount()
            true
        } else {
            // Free users have daily limits
            if (_aiActionsUsed.value < FREE_DAILY_AI_ACTIONS) {
                incrementUsageCount()
                saveUsageToPrefs()
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
     * Check subscription status
     */
    suspend fun checkSubscriptionStatus() {
        try {
            val hasActiveSubscription = billingManager.hasActiveSubscription()
            _isPro.value = hasActiveSubscription
            _isProUser.value = hasActiveSubscription
            
            prefs.edit().putBoolean(KEY_PRO_STATUS, hasActiveSubscription).apply()
            updateRemainingActions()
            
            Log.d(TAG, "Subscription status updated: $hasActiveSubscription")
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
     * Cleanup resources
     */
    fun destroy() {
        managerScope.cancel()
    }
}
