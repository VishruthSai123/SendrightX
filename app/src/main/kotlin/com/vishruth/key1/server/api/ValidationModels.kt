package com.vishruth.key1.server.api

/**
 * Request data for purchase validation
 */
data class ValidationRequest(
    val userId: String,
    val packageName: String,
    val productId: String,
    val purchaseToken: String
)

/**
 * Response from purchase validation
 */
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

/**
 * Response from entitlement check
 */
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

/**
 * Response from refresh operation
 */
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