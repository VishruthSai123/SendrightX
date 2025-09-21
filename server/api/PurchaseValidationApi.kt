package com.vishruth.key1.server.api

import android.util.Log
import com.vishruth.key1.server.verification.PurchaseVerificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.*

/**
 * Server-side purchase validation API
 * 
 * This service provides secure endpoints for validating purchases and managing entitlements.
 * It acts as the authoritative source for subscription status.
 */
class PurchaseValidationApi {
    
    companion object {
        private const val TAG = "PurchaseValidationApi"
    }
    
    private val verificationService = PurchaseVerificationService()
    private val entitlementDatabase = EntitlementDatabase()
    
    /**
     * Validate a subscription purchase
     * 
     * This endpoint should be called by the client after a successful purchase
     * to verify the purchase with Google Play and grant entitlements.
     * 
     * @param request ValidationRequest containing purchase details
     * @return ValidationResponse with entitlement status
     */
    suspend fun validateSubscriptionPurchase(request: ValidationRequest): ValidationResponse {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Validating subscription purchase for user: ${request.userId}")
                Log.d(TAG, "Package: ${request.packageName}, Product: ${request.productId}")
                
                // Step 1: Verify purchase with Google Play Developer API
                val verificationResult = verificationService.verifySubscriptionPurchase(
                    packageName = request.packageName,
                    subscriptionId = request.productId,
                    purchaseToken = request.purchaseToken
                )
                
                if (!verificationResult.isSuccess) {
                    Log.e(TAG, "Google Play verification failed: ${verificationResult.error}")
                    return@withContext ValidationResponse.error(
                        "Purchase verification failed: ${verificationResult.error}"
                    )
                }
                
                if (!verificationResult.isValid) {
                    Log.w(TAG, "Purchase is not valid: ${verificationResult.message}")
                    return@withContext ValidationResponse.invalid(
                        "Purchase is not valid: ${verificationResult.message}"
                    )
                }
                
                // Step 2: Store entitlement in database
                val entitlement = UserEntitlement(
                    userId = request.userId,
                    productId = request.productId,
                    purchaseToken = request.purchaseToken,
                    orderId = verificationResult.orderId,
                    subscriptionStatus = verificationResult.subscriptionStatus,
                    expiryTimeMillis = verificationResult.expiryTimeMillis,
                    autoRenewing = verificationResult.autoRenewing,
                    createdAt = System.currentTimeMillis(),
                    lastVerified = System.currentTimeMillis()
                )
                
                val saveResult = entitlementDatabase.saveEntitlement(entitlement)
                if (!saveResult.isSuccess) {
                    Log.e(TAG, "Failed to save entitlement: ${saveResult.error}")
                    return@withContext ValidationResponse.error(
                        "Failed to save entitlement: ${saveResult.error}"
                    )
                }
                
                Log.d(TAG, "✅ Purchase validated successfully for user: ${request.userId}")
                
                // Step 3: Return success response with entitlement details
                ValidationResponse.success(
                    isValid = true,
                    subscriptionStatus = verificationResult.subscriptionStatus,
                    expiryTimeMillis = verificationResult.expiryTimeMillis,
                    autoRenewing = verificationResult.autoRenewing,
                    message = "Purchase validated and entitlement granted"
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during purchase validation", e)
                ValidationResponse.error("Validation error: ${e.message}")
            }
        }
    }
    
    /**
     * Check user entitlements
     * 
     * This endpoint returns the current entitlement status for a user.
     * The client should call this to check Pro access status.
     * 
     * @param userId User ID to check entitlements for
     * @return EntitlementResponse with current status
     */
    suspend fun checkUserEntitlements(userId: String): EntitlementResponse {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Checking entitlements for user: $userId")
                
                // Get user's entitlements from database
                val entitlements = entitlementDatabase.getUserEntitlements(userId)
                
                if (entitlements.isEmpty()) {
                    Log.d(TAG, "No entitlements found for user: $userId")
                    return@withContext EntitlementResponse.noEntitlements("No active subscriptions found")
                }
                
                // Find the most recent active subscription
                val activeEntitlement = entitlements
                    .filter { it.subscriptionStatus == "active" }
                    .maxByOrNull { it.expiryTimeMillis }
                
                if (activeEntitlement == null) {
                    Log.d(TAG, "No active entitlements for user: $userId")
                    return@withContext EntitlementResponse.noEntitlements("No active subscriptions")
                }
                
                // Check if subscription is still valid (not expired)
                val currentTime = System.currentTimeMillis()
                val isExpired = activeEntitlement.expiryTimeMillis > 0 && currentTime > activeEntitlement.expiryTimeMillis
                
                if (isExpired) {
                    Log.d(TAG, "Subscription expired for user: $userId")
                    
                    // Update entitlement status to expired
                    entitlementDatabase.updateEntitlementStatus(
                        activeEntitlement.copy(subscriptionStatus = "expired")
                    )
                    
                    return@withContext EntitlementResponse.expired("Subscription has expired")
                }
                
                Log.d(TAG, "✅ Active subscription found for user: $userId")
                
                EntitlementResponse.active(
                    hasActiveSubscription = true,
                    subscriptionStatus = activeEntitlement.subscriptionStatus,
                    expiryTimeMillis = activeEntitlement.expiryTimeMillis,
                    autoRenewing = activeEntitlement.autoRenewing,
                    productId = activeEntitlement.productId,
                    message = "Active subscription found"
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking user entitlements", e)
                EntitlementResponse.error("Error checking entitlements: ${e.message}")
            }
        }
    }
    
    /**
     * Refresh user entitlements by re-verifying with Google Play
     * 
     * This endpoint re-validates all user purchases with Google Play
     * to ensure the local entitlement database is up to date.
     * 
     * @param userId User ID to refresh entitlements for
     * @return RefreshResponse with updated status
     */
    suspend fun refreshUserEntitlements(userId: String): RefreshResponse {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Refreshing entitlements for user: $userId")
                
                val entitlements = entitlementDatabase.getUserEntitlements(userId)
                var refreshedCount = 0
                var activeCount = 0
                
                for (entitlement in entitlements) {
                    try {
                        // Re-verify each entitlement with Google Play
                        val verificationResult = verificationService.verifySubscriptionPurchase(
                            packageName = "com.vishruth.key1", // Updated to correct package
                            subscriptionId = entitlement.productId,
                            purchaseToken = entitlement.purchaseToken
                        )
                        
                        if (verificationResult.isSuccess) {
                            // Update entitlement with latest status
                            val updatedEntitlement = entitlement.copy(
                                subscriptionStatus = verificationResult.subscriptionStatus,
                                expiryTimeMillis = verificationResult.expiryTimeMillis,
                                autoRenewing = verificationResult.autoRenewing,
                                lastVerified = System.currentTimeMillis()
                            )
                            
                            entitlementDatabase.updateEntitlementStatus(updatedEntitlement)
                            refreshedCount++
                            
                            if (verificationResult.isValid) {
                                activeCount++
                            }
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error refreshing entitlement: ${entitlement.productId}", e)
                    }
                }
                
                Log.d(TAG, "✅ Refreshed $refreshedCount entitlements for user: $userId ($activeCount active)")
                
                RefreshResponse.success(
                    refreshedCount = refreshedCount,
                    activeCount = activeCount,
                    message = "Entitlements refreshed successfully"
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing user entitlements", e)
                RefreshResponse.error("Refresh error: ${e.message}")
            }
        }
    }
    
    /**
     * Handle webhook notifications from Google Play
     * 
     * This endpoint processes real-time notifications about subscription changes
     * (cancellations, expirations, refunds, etc.)
     * 
     * @param webhookData Raw webhook data from Google Play
     * @return WebhookResponse indicating processing status
     */
    suspend fun handleWebhookNotification(webhookData: String): WebhookResponse {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing webhook notification")
                
                // Parse webhook notification
                val notification = parseWebhookNotification(webhookData)
                if (notification == null) {
                    return@withContext WebhookResponse.error("Failed to parse webhook notification")
                }
                
                // Find user by purchase token
                val entitlement = entitlementDatabase.getEntitlementByPurchaseToken(notification.purchaseToken)
                if (entitlement == null) {
                    Log.w(TAG, "No entitlement found for purchase token: ${notification.purchaseToken}")
                    return@withContext WebhookResponse.error("Entitlement not found")
                }
                
                // Process notification based on type
                when (notification.notificationType) {
                    2 -> { // SUBSCRIPTION_CANCELED
                        Log.d(TAG, "Processing subscription cancellation")
                        entitlementDatabase.updateEntitlementStatus(
                            entitlement.copy(subscriptionStatus = "canceled_but_active")
                        )
                    }
                    12, 13 -> { // SUBSCRIPTION_REVOKED or SUBSCRIPTION_EXPIRED
                        Log.d(TAG, "Processing subscription revocation/expiration")
                        entitlementDatabase.updateEntitlementStatus(
                            entitlement.copy(subscriptionStatus = "expired")
                        )
                    }
                    1 -> { // SUBSCRIPTION_RECOVERED
                        Log.d(TAG, "Processing subscription recovery")
                        entitlementDatabase.updateEntitlementStatus(
                            entitlement.copy(subscriptionStatus = "active")
                        )
                    }
                    3 -> { // SUBSCRIPTION_PURCHASED
                        Log.d(TAG, "Processing new subscription purchase")
                        entitlementDatabase.updateEntitlementStatus(
                            entitlement.copy(subscriptionStatus = "active")
                        )
                    }
                }
                
                Log.d(TAG, "✅ Webhook notification processed successfully")
                WebhookResponse.success("Notification processed successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing webhook notification", e)
                WebhookResponse.error("Webhook processing error: ${e.message}")
            }
        }
    }
    
    /**
     * Parse webhook notification from Google Play
     */
    private fun parseWebhookNotification(webhookData: String): WebhookNotification? {
        return try {
            val json = JSONObject(webhookData)
            val message = json.getJSONObject("message")
            val messageData = String(Base64.getDecoder().decode(message.getString("data")))
            val notificationJson = JSONObject(messageData)
            
            val subscriptionNotification = notificationJson.optJSONObject("subscriptionNotification")
            if (subscriptionNotification != null) {
                WebhookNotification(
                    notificationType = subscriptionNotification.getInt("notificationType"),
                    purchaseToken = subscriptionNotification.getString("purchaseToken"),
                    subscriptionId = subscriptionNotification.getString("subscriptionId")
                )
            } else null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing webhook notification", e)
            null
        }
    }
}

/**
 * Data classes for API requests and responses
 */
data class ValidationRequest(
    val userId: String,
    val packageName: String,
    val productId: String,
    val purchaseToken: String
)

data class ValidationResponse(
    val isSuccess: Boolean,
    val isValid: Boolean,
    val subscriptionStatus: String,
    val expiryTimeMillis: Long,
    val autoRenewing: Boolean,
    val message: String,
    val error: String?
) {
    companion object {
        fun success(
            isValid: Boolean,
            subscriptionStatus: String,
            expiryTimeMillis: Long,
            autoRenewing: Boolean,
            message: String
        ) = ValidationResponse(true, isValid, subscriptionStatus, expiryTimeMillis, autoRenewing, message, null)
        
        fun invalid(message: String) = ValidationResponse(true, false, "invalid", 0, false, message, null)
        
        fun error(error: String) = ValidationResponse(false, false, "error", 0, false, error, error)
    }
}

data class EntitlementResponse(
    val isSuccess: Boolean,
    val hasActiveSubscription: Boolean,
    val subscriptionStatus: String,
    val expiryTimeMillis: Long,
    val autoRenewing: Boolean,
    val productId: String,
    val message: String,
    val error: String?
) {
    companion object {
        fun active(
            hasActiveSubscription: Boolean,
            subscriptionStatus: String,
            expiryTimeMillis: Long,
            autoRenewing: Boolean,
            productId: String,
            message: String
        ) = EntitlementResponse(true, hasActiveSubscription, subscriptionStatus, expiryTimeMillis, autoRenewing, productId, message, null)
        
        fun noEntitlements(message: String) = EntitlementResponse(true, false, "none", 0, false, "", message, null)
        
        fun expired(message: String) = EntitlementResponse(true, false, "expired", 0, false, "", message, null)
        
        fun error(error: String) = EntitlementResponse(false, false, "error", 0, false, "", error, error)
    }
}

data class RefreshResponse(
    val isSuccess: Boolean,
    val refreshedCount: Int,
    val activeCount: Int,
    val message: String,
    val error: String?
) {
    companion object {
        fun success(refreshedCount: Int, activeCount: Int, message: String) = 
            RefreshResponse(true, refreshedCount, activeCount, message, null)
        
        fun error(error: String) = RefreshResponse(false, 0, 0, error, error)
    }
}

data class WebhookResponse(
    val isSuccess: Boolean,
    val message: String,
    val error: String?
) {
    companion object {
        fun success(message: String) = WebhookResponse(true, message, null)
        fun error(error: String) = WebhookResponse(false, error, error)
    }
}

data class WebhookNotification(
    val notificationType: Int,
    val purchaseToken: String,
    val subscriptionId: String
)

/**
 * User entitlement data model
 */
data class UserEntitlement(
    val userId: String,
    val productId: String,
    val purchaseToken: String,
    val orderId: String,
    val subscriptionStatus: String,
    val expiryTimeMillis: Long,
    val autoRenewing: Boolean,
    val createdAt: Long,
    val lastVerified: Long
)