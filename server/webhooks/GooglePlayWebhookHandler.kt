package com.vishruth.sendright.server.webhooks

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.*

/**
 * Google Play Developer API Webhook Handler
 * 
 * This class handles real-time subscription state notifications from Google Play.
 * 
 * Notification Types:
 * - SUBSCRIPTION_CANCELED (Type 2): User cancelled but still has access during paid period
 * - SUBSCRIPTION_EXPIRED (Type 12): Subscription actually expired, revoke access
 * 
 * Setup Instructions:
 * 1. Deploy this as a server endpoint (e.g., Cloud Functions, AWS Lambda)
 * 2. Configure webhook URL in Google Play Console
 * 3. Set up authentication (API keys, OAuth, etc.)
 */
class GooglePlayWebhookHandler {
    
    companion object {
        private const val TAG = "GooglePlayWebhook"
        
        // Notification types from Google Play Developer API
        const val SUBSCRIPTION_RECOVERED = 1
        const val SUBSCRIPTION_CANCELED = 2
        const val SUBSCRIPTION_PURCHASED = 3
        const val SUBSCRIPTION_ON_HOLD = 5
        const val SUBSCRIPTION_IN_GRACE_PERIOD = 6
        const val SUBSCRIPTION_RESTARTED = 7
        const val SUBSCRIPTION_PRICE_CHANGE_CONFIRMED = 8
        const val SUBSCRIPTION_DEFERRED = 9
        const val SUBSCRIPTION_PAUSED = 10
        const val SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED = 11
        const val SUBSCRIPTION_REVOKED = 12
        const val SUBSCRIPTION_EXPIRED = 13
    }
    
    private val firestore = FirebaseFirestore.getInstance()
    
    /**
     * Main webhook handler function
     * Call this from your server endpoint when receiving Google Play notifications
     */
    suspend fun handleWebhook(
        notificationData: String,
        signature: String? = null
    ): WebhookResponse {
        return try {
            Log.d(TAG, "Received Google Play webhook notification")
            
            // Verify webhook signature if provided (recommended for production)
            if (signature != null && !verifySignature(notificationData, signature)) {
                Log.e(TAG, "Invalid webhook signature")
                return WebhookResponse.error("Invalid signature")
            }
            
            val notification = parseNotification(notificationData)
            processNotification(notification)
            
            WebhookResponse.success("Notification processed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing webhook", e)
            WebhookResponse.error("Failed to process notification: ${e.message}")
        }
    }
    
    /**
     * Parse the notification JSON from Google Play
     */
    private fun parseNotification(data: String): GooglePlayNotification {
        val json = JSONObject(data)
        val message = json.getJSONObject("message")
        val messageData = String(Base64.getDecoder().decode(message.getString("data")))
        val notificationJson = JSONObject(messageData)
        
        return GooglePlayNotification(
            version = notificationJson.getString("version"),
            packageName = notificationJson.getString("packageName"),
            eventTimeMillis = notificationJson.getLong("eventTimeMillis"),
            subscriptionNotification = notificationJson.optJSONObject("subscriptionNotification")?.let {
                SubscriptionNotification(
                    version = it.getString("version"),
                    notificationType = it.getInt("notificationType"),
                    purchaseToken = it.getString("purchaseToken"),
                    subscriptionId = it.getString("subscriptionId")
                )
            }
        )
    }
    
    /**
     * Process the parsed notification based on type
     */
    private suspend fun processNotification(notification: GooglePlayNotification) {
        val subNotification = notification.subscriptionNotification
            ?: return Log.w(TAG, "No subscription notification data")
        
        Log.d(TAG, "Processing notification type: ${subNotification.notificationType}")
        Log.d(TAG, "Purchase token: ${subNotification.purchaseToken}")
        Log.d(TAG, "Subscription ID: ${subNotification.subscriptionId}")
        
        when (subNotification.notificationType) {
            SUBSCRIPTION_CANCELED -> {
                handleSubscriptionCanceled(subNotification)
            }
            SUBSCRIPTION_EXPIRED -> {
                handleSubscriptionExpired(subNotification)
            }
            SUBSCRIPTION_REVOKED -> {
                handleSubscriptionRevoked(subNotification)
            }
            SUBSCRIPTION_RECOVERED -> {
                handleSubscriptionRecovered(subNotification)
            }
            SUBSCRIPTION_PURCHASED -> {
                handleSubscriptionPurchased(subNotification)
            }
            SUBSCRIPTION_ON_HOLD -> {
                handleSubscriptionOnHold(subNotification)
            }
            SUBSCRIPTION_IN_GRACE_PERIOD -> {
                handleSubscriptionInGracePeriod(subNotification)
            }
            else -> {
                Log.d(TAG, "Unhandled notification type: ${subNotification.notificationType}")
            }
        }
    }
    
    /**
     * Handle SUBSCRIPTION_CANCELED (Type 2)
     * User cancelled subscription but still has access during paid period
     * 
     * Action: Keep Pro access but note the cancellation
     */
    private suspend fun handleSubscriptionCanceled(notification: SubscriptionNotification) {
        Log.d(TAG, "🟡 SUBSCRIPTION_CANCELED: User cancelled but retains access")
        
        try {
            // Find user by purchase token
            val userId = findUserByPurchaseToken(notification.purchaseToken)
            if (userId == null) {
                Log.w(TAG, "User not found for purchase token: ${notification.purchaseToken}")
                return
            }
            
            // Update subscription status to "canceled_but_active"
            // User keeps Pro access until the paid period ends
            firestore.collection("users")
                .document(userId)
                .update(mapOf(
                    "subscriptionStatus" to "canceled_but_active",
                    "subscriptionCanceledAt" to System.currentTimeMillis(),
                    "lastNotificationProcessed" to System.currentTimeMillis()
                ))
                .await()
            
            // Update the subscription record
            updateSubscriptionRecord(
                userId = userId,
                purchaseToken = notification.purchaseToken,
                status = "canceled_but_active"
            )
            
            Log.d(TAG, "✅ Subscription marked as canceled but active for user: $userId")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling subscription cancellation", e)
        }
    }
    
    /**
     * Handle SUBSCRIPTION_EXPIRED (Type 13)
     * Subscription has actually expired - revoke Pro access
     * 
     * Action: Revoke Pro access immediately
     */
    private suspend fun handleSubscriptionExpired(notification: SubscriptionNotification) {
        Log.d(TAG, "🔴 SUBSCRIPTION_EXPIRED: Revoking Pro access")
        
        try {
            // Find user by purchase token
            val userId = findUserByPurchaseToken(notification.purchaseToken)
            if (userId == null) {
                Log.w(TAG, "User not found for purchase token: ${notification.purchaseToken}")
                return
            }
            
            // Revoke Pro access - set to "free"
            firestore.collection("users")
                .document(userId)
                .update(mapOf(
                    "subscriptionStatus" to "free",
                    "subscriptionExpiredAt" to System.currentTimeMillis(),
                    "lastNotificationProcessed" to System.currentTimeMillis()
                ))
                .await()
            
            // Update the subscription record
            updateSubscriptionRecord(
                userId = userId,
                purchaseToken = notification.purchaseToken,
                status = "expired"
            )
            
            Log.d(TAG, "✅ Pro access revoked for user: $userId")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling subscription expiration", e)
        }
    }
    
    /**
     * Handle SUBSCRIPTION_REVOKED (Type 12)
     * Google revoked the subscription (refund, chargeback, etc.)
     */
    private suspend fun handleSubscriptionRevoked(notification: SubscriptionNotification) {
        Log.d(TAG, "🔴 SUBSCRIPTION_REVOKED: Google revoked subscription")
        
        try {
            val userId = findUserByPurchaseToken(notification.purchaseToken)
            if (userId == null) {
                Log.w(TAG, "User not found for purchase token: ${notification.purchaseToken}")
                return
            }
            
            // Immediately revoke Pro access
            firestore.collection("users")
                .document(userId)
                .update(mapOf(
                    "subscriptionStatus" to "free",
                    "subscriptionRevokedAt" to System.currentTimeMillis(),
                    "lastNotificationProcessed" to System.currentTimeMillis()
                ))
                .await()
            
            updateSubscriptionRecord(
                userId = userId,
                purchaseToken = notification.purchaseToken,
                status = "revoked"
            )
            
            Log.d(TAG, "✅ Subscription revoked for user: $userId")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling subscription revocation", e)
        }
    }
    
    /**
     * Handle SUBSCRIPTION_RECOVERED (Type 1)
     * User recovered from grace period or on-hold
     */
    private suspend fun handleSubscriptionRecovered(notification: SubscriptionNotification) {
        Log.d(TAG, "🟢 SUBSCRIPTION_RECOVERED: Restoring Pro access")
        
        try {
            val userId = findUserByPurchaseToken(notification.purchaseToken)
            if (userId == null) {
                Log.w(TAG, "User not found for purchase token: ${notification.purchaseToken}")
                return
            }
            
            // Restore Pro access
            firestore.collection("users")
                .document(userId)
                .update(mapOf(
                    "subscriptionStatus" to "pro",
                    "subscriptionRecoveredAt" to System.currentTimeMillis(),
                    "lastNotificationProcessed" to System.currentTimeMillis()
                ))
                .await()
            
            updateSubscriptionRecord(
                userId = userId,
                purchaseToken = notification.purchaseToken,
                status = "active"
            )
            
            Log.d(TAG, "✅ Pro access restored for user: $userId")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling subscription recovery", e)
        }
    }
    
    /**
     * Handle SUBSCRIPTION_PURCHASED (Type 3)
     * New subscription purchased
     */
    private suspend fun handleSubscriptionPurchased(notification: SubscriptionNotification) {
        Log.d(TAG, "🟢 SUBSCRIPTION_PURCHASED: New subscription activated")
        
        try {
            val userId = findUserByPurchaseToken(notification.purchaseToken)
            if (userId == null) {
                Log.w(TAG, "User not found for purchase token: ${notification.purchaseToken}")
                return
            }
            
            // Grant Pro access
            firestore.collection("users")
                .document(userId)
                .update(mapOf(
                    "subscriptionStatus" to "pro",
                    "subscriptionPurchasedAt" to System.currentTimeMillis(),
                    "lastNotificationProcessed" to System.currentTimeMillis()
                ))
                .await()
            
            updateSubscriptionRecord(
                userId = userId,
                purchaseToken = notification.purchaseToken,
                status = "active"
            )
            
            Log.d(TAG, "✅ Pro access granted for user: $userId")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling subscription purchase", e)
        }
    }
    
    /**
     * Handle SUBSCRIPTION_ON_HOLD (Type 5)
     * Subscription is on hold due to payment issues
     */
    private suspend fun handleSubscriptionOnHold(notification: SubscriptionNotification) {
        Log.d(TAG, "🟡 SUBSCRIPTION_ON_HOLD: Payment issues detected")
        
        try {
            val userId = findUserByPurchaseToken(notification.purchaseToken)
            if (userId != null) {
                // Keep Pro access but note the hold status
                firestore.collection("users")
                    .document(userId)
                    .update(mapOf(
                        "subscriptionStatus" to "on_hold",
                        "subscriptionOnHoldAt" to System.currentTimeMillis(),
                        "lastNotificationProcessed" to System.currentTimeMillis()
                    ))
                    .await()
                
                updateSubscriptionRecord(
                    userId = userId,
                    purchaseToken = notification.purchaseToken,
                    status = "on_hold"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling subscription on hold", e)
        }
    }
    
    /**
     * Handle SUBSCRIPTION_IN_GRACE_PERIOD (Type 6)
     * Subscription is in grace period
     */
    private suspend fun handleSubscriptionInGracePeriod(notification: SubscriptionNotification) {
        Log.d(TAG, "🟡 SUBSCRIPTION_IN_GRACE_PERIOD: Grace period active")
        
        try {
            val userId = findUserByPurchaseToken(notification.purchaseToken)
            if (userId != null) {
                // Keep Pro access during grace period
                firestore.collection("users")
                    .document(userId)
                    .update(mapOf(
                        "subscriptionStatus" to "grace_period",
                        "subscriptionGracePeriodAt" to System.currentTimeMillis(),
                        "lastNotificationProcessed" to System.currentTimeMillis()
                    ))
                    .await()
                
                updateSubscriptionRecord(
                    userId = userId,
                    purchaseToken = notification.purchaseToken,
                    status = "grace_period"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling subscription grace period", e)
        }
    }
    
    /**
     * Find user ID by purchase token
     */
    private suspend fun findUserByPurchaseToken(purchaseToken: String): String? {
        return try {
            val querySnapshot = firestore.collectionGroup("subscriptions")
                .whereEqualTo("purchaseToken", purchaseToken)
                .limit(1)
                .get()
                .await()
            
            if (!querySnapshot.isEmpty) {
                val document = querySnapshot.documents.first()
                val path = document.reference.path
                // Extract user ID from path: users/{userId}/subscriptions/{subscriptionId}
                path.split("/")[1]
            } else null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error finding user by purchase token", e)
            null
        }
    }
    
    /**
     * Update subscription record with new status
     */
    private suspend fun updateSubscriptionRecord(
        userId: String,
        purchaseToken: String,
        status: String
    ) {
        try {
            firestore.collection("users")
                .document(userId)
                .collection("subscriptions")
                .document(purchaseToken)
                .update(mapOf(
                    "status" to status,
                    "lastUpdated" to System.currentTimeMillis()
                ))
                .await()
                
        } catch (e: Exception) {
            Log.e(TAG, "Error updating subscription record", e)
        }
    }
    
    /**
     * Verify webhook signature (implement based on your security setup)
     */
    private fun verifySignature(data: String, signature: String): Boolean {
        // TODO: Implement signature verification
        // This should verify the webhook came from Google Play
        // Use your webhook signing key from Google Play Console
        return true // Placeholder - implement proper verification
    }
}

/**
 * Data classes for Google Play notifications
 */
data class GooglePlayNotification(
    val version: String,
    val packageName: String,
    val eventTimeMillis: Long,
    val subscriptionNotification: SubscriptionNotification?
)

data class SubscriptionNotification(
    val version: String,
    val notificationType: Int,
    val purchaseToken: String,
    val subscriptionId: String
)

/**
 * Webhook response data class
 */
data class WebhookResponse(
    val success: Boolean,
    val message: String
) {
    companion object {
        fun success(message: String) = WebhookResponse(true, message)
        fun error(message: String) = WebhookResponse(false, message)
    }
}
