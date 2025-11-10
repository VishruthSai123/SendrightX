package com.vishruth.key1.server.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vishruth.key1.utils.SecurePreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Entitlement database for storing and managing user subscription entitlements
 * 
 * This implementation uses SharedPreferences for simplicity and local storage.
 * In production, you should use a proper database (SQLite, Room, or server-side database).
 */
class EntitlementDatabase {
    
    companion object {
        private const val TAG = "EntitlementDatabase"
        private const val PREFS_NAME = "entitlements_db"
        private const val KEY_ENTITLEMENTS = "user_entitlements"
        
        @Volatile
        private var INSTANCE: EntitlementDatabase? = null
        
        fun getInstance(context: Context? = null): EntitlementDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EntitlementDatabase().also { instance ->
                    // RACE CONDITION FIX: Initialize before assigning to INSTANCE
                    // This prevents other threads from getting uninitialized instance
                    if (context != null) {
                        instance.initialize(context)
                    }
                    INSTANCE = instance
                }
            }
        }
    }
    
    private var prefs: SharedPreferences? = null
    private val gson = Gson()
    private val entitlementCache = ConcurrentHashMap<String, List<UserEntitlement>>()
    
    fun initialize(context: Context) {
        // SECURITY FIX: Use encrypted preferences for entitlement storage
        prefs = SecurePreferences.getEncryptedPreferences(context, PREFS_NAME)
        
        // SECURITY FIX: Migrate old plain preferences to encrypted storage
        SecurePreferences.migrateToEncrypted(context, PREFS_NAME, PREFS_NAME)
        
        loadEntitlementsFromPrefs()
    }
    
    /**
     * Save user entitlement to database
     */
    suspend fun saveEntitlement(entitlement: UserEntitlement): DatabaseResult {
        return try {
            Log.d(TAG, "Saving entitlement for user: ${entitlement.userId}")
            
            // Get existing entitlements for user
            val existingEntitlements = getUserEntitlements(entitlement.userId).toMutableList()
            
            // Check if entitlement already exists (by purchase token)
            val existingIndex = existingEntitlements.indexOfFirst { 
                it.purchaseToken == entitlement.purchaseToken 
            }
            
            if (existingIndex >= 0) {
                // Update existing entitlement
                existingEntitlements[existingIndex] = entitlement
                Log.d(TAG, "Updated existing entitlement for purchase token: ${entitlement.purchaseToken}")
            } else {
                // Add new entitlement
                existingEntitlements.add(entitlement)
                Log.d(TAG, "Added new entitlement for purchase token: ${entitlement.purchaseToken}")
            }
            
            // Update cache
            entitlementCache[entitlement.userId] = existingEntitlements
            
            // Save to persistent storage
            saveEntitlementsToPrefs()
            
            DatabaseResult.success("Entitlement saved successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving entitlement", e)
            DatabaseResult.error("Failed to save entitlement: ${e.message}")
        }
    }
    
    /**
     * Get all entitlements for a user
     */
    fun getUserEntitlements(userId: String): List<UserEntitlement> {
        return try {
            // Try cache first
            entitlementCache[userId] ?: run {
                // Load from persistent storage
                loadUserEntitlementsFromPrefs(userId).also { entitlements ->
                    entitlementCache[userId] = entitlements
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user entitlements", e)
            emptyList()
        }
    }
    
    /**
     * Get entitlement by purchase token
     */
    fun getEntitlementByPurchaseToken(purchaseToken: String): UserEntitlement? {
        return try {
            // Search through all cached entitlements
            entitlementCache.values.flatten().find { it.purchaseToken == purchaseToken }
                ?: run {
                    // If not in cache, load all entitlements and search
                    loadAllEntitlementsFromPrefs()
                    entitlementCache.values.flatten().find { it.purchaseToken == purchaseToken }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting entitlement by purchase token", e)
            null
        }
    }
    
    /**
     * Update entitlement status
     */
    suspend fun updateEntitlementStatus(entitlement: UserEntitlement): DatabaseResult {
        return try {
            Log.d(TAG, "Updating entitlement status for user: ${entitlement.userId}")
            
            // Get existing entitlements
            val entitlements = getUserEntitlements(entitlement.userId).toMutableList()
            
            // Find and update the entitlement
            val index = entitlements.indexOfFirst { it.purchaseToken == entitlement.purchaseToken }
            if (index >= 0) {
                entitlements[index] = entitlement
                
                // Update cache
                entitlementCache[entitlement.userId] = entitlements
                
                // Save to persistent storage
                saveEntitlementsToPrefs()
                
                Log.d(TAG, "✅ Entitlement status updated to: ${entitlement.subscriptionStatus}")
                DatabaseResult.success("Entitlement updated successfully")
            } else {
                Log.w(TAG, "Entitlement not found for purchase token: ${entitlement.purchaseToken}")
                DatabaseResult.error("Entitlement not found")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating entitlement status", e)
            DatabaseResult.error("Failed to update entitlement: ${e.message}")
        }
    }
    
    /**
     * Get active subscription for user
     */
    fun getActiveSubscription(userId: String): UserEntitlement? {
        return try {
            val entitlements = getUserEntitlements(userId)
            val currentTime = System.currentTimeMillis()
            
            // Find active subscription that hasn't expired
            entitlements
                .filter { it.subscriptionStatus == "active" }
                .filter { entitlement ->
                    entitlement.expiryTimeMillis == 0L || currentTime < entitlement.expiryTimeMillis
                }
                .maxByOrNull { it.expiryTimeMillis }
                
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active subscription", e)
            null
        }
    }
    
    /**
     * Check if user has active subscription
     */
    fun hasActiveSubscription(userId: String): Boolean {
        return getActiveSubscription(userId) != null
    }
    
    /**
     * Remove expired entitlements (cleanup)
     */
    suspend fun removeExpiredEntitlements(): DatabaseResult {
        return try {
            Log.d(TAG, "Removing expired entitlements...")
            
            val currentTime = System.currentTimeMillis()
            var removedCount = 0
            
            entitlementCache.keys.forEach { userId ->
                val entitlements = entitlementCache[userId] ?: emptyList()
                val activeEntitlements = entitlements.filter { entitlement ->
                    when (entitlement.subscriptionStatus) {
                        "expired", "canceled", "revoked" -> {
                            // Remove obviously expired/cancelled subscriptions
                            if (entitlement.expiryTimeMillis > 0 && currentTime > entitlement.expiryTimeMillis + (7 * 24 * 60 * 60 * 1000)) {
                                removedCount++
                                false
                            } else true
                        }
                        else -> true
                    }
                }
                
                if (activeEntitlements.size != entitlements.size) {
                    entitlementCache[userId] = activeEntitlements
                }
            }
            
            // Save updated entitlements
            saveEntitlementsToPrefs()
            
            Log.d(TAG, "✅ Removed $removedCount expired entitlements")
            DatabaseResult.success("Removed $removedCount expired entitlements")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error removing expired entitlements", e)
            DatabaseResult.error("Failed to remove expired entitlements: ${e.message}")
        }
    }
    
    /**
     * Load entitlements from SharedPreferences
     */
    private fun loadEntitlementsFromPrefs() {
        try {
            val prefsInstance = prefs ?: return
            val entitlementsJson = prefsInstance.getString(KEY_ENTITLEMENTS, null)
            
            if (entitlementsJson != null) {
                val type = object : TypeToken<Map<String, List<UserEntitlement>>>() {}.type
                val entitlementsMap: Map<String, List<UserEntitlement>> = gson.fromJson(entitlementsJson, type)
                
                entitlementCache.clear()
                entitlementCache.putAll(entitlementsMap)
                
                Log.d(TAG, "Loaded ${entitlementsMap.size} user entitlements from storage")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading entitlements from preferences", e)
        }
    }
    
    /**
     * Load entitlements for a specific user from SharedPreferences
     */
    private fun loadUserEntitlementsFromPrefs(userId: String): List<UserEntitlement> {
        return try {
            loadEntitlementsFromPrefs()
            entitlementCache[userId] ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user entitlements from preferences", e)
            emptyList()
        }
    }
    
    /**
     * Load all entitlements from SharedPreferences
     */
    private fun loadAllEntitlementsFromPrefs() {
        loadEntitlementsFromPrefs()
    }
    
    /**
     * Save entitlements to SharedPreferences
     */
    private fun saveEntitlementsToPrefs() {
        try {
            val prefsInstance = prefs ?: return
            val entitlementsJson = gson.toJson(entitlementCache.toMap())
            
            prefsInstance.edit()
                .putString(KEY_ENTITLEMENTS, entitlementsJson)
                .apply()
                
            Log.d(TAG, "Saved ${entitlementCache.size} user entitlements to storage")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving entitlements to preferences", e)
        }
    }
    
    /**
     * Clear all entitlements (for testing or reset)
     */
    suspend fun clearAllEntitlements(): DatabaseResult {
        return try {
            entitlementCache.clear()
            prefs?.edit()?.remove(KEY_ENTITLEMENTS)?.apply()
            
            Log.d(TAG, "✅ All entitlements cleared")
            DatabaseResult.success("All entitlements cleared")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing entitlements", e)
            DatabaseResult.error("Failed to clear entitlements: ${e.message}")
        }
    }
    
    /**
     * Get entitlement statistics for debugging
     */
    fun getEntitlementStats(): Map<String, Any> {
        return try {
            val totalUsers = entitlementCache.size
            val totalEntitlements = entitlementCache.values.sumOf { it.size }
            val activeEntitlements = entitlementCache.values.flatten().count { 
                it.subscriptionStatus == "active" 
            }
            val expiredEntitlements = entitlementCache.values.flatten().count { 
                it.subscriptionStatus == "expired" 
            }
            
            mapOf(
                "totalUsers" to totalUsers,
                "totalEntitlements" to totalEntitlements,
                "activeEntitlements" to activeEntitlements,
                "expiredEntitlements" to expiredEntitlements,
                "cacheSize" to entitlementCache.size
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting entitlement stats", e)
            mapOf("error" to e.message)
        }
    }
}

/**
 * Database operation result
 */
data class DatabaseResult(
    val isSuccess: Boolean,
    val message: String,
    val error: String?
) {
    companion object {
        fun success(message: String) = DatabaseResult(true, message, null)
        fun error(error: String) = DatabaseResult(false, error, error)
    }
}