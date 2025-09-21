package com.vishruth.key1.server.verification

import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

/**
 * Server-side purchase verification service using Google Play Developer API
 * 
 * This service validates purchase tokens with Google Play to ensure:
 * 1. Purchase legitimacy (not client-side spoofed)
 * 2. Purchase state (purchased, canceled, expired)
 * 3. Subscription status and expiry dates
 * 4. Automatic refund/cancellation detection
 */
class PurchaseVerificationService {
    
    companion object {
        private const val TAG = "PurchaseVerification"
        private const val GOOGLE_PLAY_API_BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
        
        // Purchase states from Google Play API
        const val PURCHASE_STATE_PURCHASED = 0
        const val PURCHASE_STATE_CANCELED = 1
        
        // Subscription states
        const val SUBSCRIPTION_STATE_PENDING = 0
        const val SUBSCRIPTION_STATE_ACTIVE = 1
        const val SUBSCRIPTION_STATE_EXPIRED = 2
        const val SUBSCRIPTION_STATE_CANCELED = 3
        const val SUBSCRIPTION_STATE_ON_HOLD = 4
        const val SUBSCRIPTION_STATE_IN_GRACE_PERIOD = 5
        const val SUBSCRIPTION_STATE_PAUSED = 6
    }
    
    private var accessToken: String? = null
    private var tokenExpiryTime: Long = 0
    
    /**
     * Verify a subscription purchase with Google Play Developer API
     * 
     * @param packageName App package name (e.g., com.vishruth.sendright)
     * @param subscriptionId Subscription product ID (e.g., sendright.pro.89)
     * @param purchaseToken Purchase token from the purchase
     * @return VerificationResult with validation status and details
     */
    suspend fun verifySubscriptionPurchase(
        packageName: String,
        subscriptionId: String,
        purchaseToken: String
    ): VerificationResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Verifying subscription purchase:")
                Log.d(TAG, "  Package: $packageName")
                Log.d(TAG, "  Subscription: $subscriptionId")
                Log.d(TAG, "  Token: ${purchaseToken.take(20)}...")
                
                // Get valid access token
                val token = getValidAccessToken()
                if (token == null) {
                    Log.e(TAG, "Failed to obtain access token")
                    return@withContext VerificationResult.error("Failed to authenticate with Google Play API")
                }
                
                // Make API call to verify subscription
                val apiUrl = "$GOOGLE_PLAY_API_BASE/$packageName/purchases/subscriptions/$subscriptionId/tokens/$purchaseToken"
                val response = makeAuthenticatedApiCall(apiUrl, token)
                
                if (response.isSuccess) {
                    parseSubscriptionResponse(response.data)
                } else {
                    Log.e(TAG, "API call failed: ${response.error}")
                    VerificationResult.error("Google Play API verification failed: ${response.error}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during purchase verification", e)
                VerificationResult.error("Verification error: ${e.message}")
            }
        }
    }
    
    /**
     * Verify an in-app product purchase (one-time purchase)
     */
    suspend fun verifyProductPurchase(
        packageName: String,
        productId: String,
        purchaseToken: String
    ): VerificationResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Verifying product purchase:")
                Log.d(TAG, "  Package: $packageName")
                Log.d(TAG, "  Product: $productId")
                Log.d(TAG, "  Token: ${purchaseToken.take(20)}...")
                
                val token = getValidAccessToken()
                if (token == null) {
                    return@withContext VerificationResult.error("Failed to authenticate with Google Play API")
                }
                
                val apiUrl = "$GOOGLE_PLAY_API_BASE/$packageName/purchases/products/$productId/tokens/$purchaseToken"
                val response = makeAuthenticatedApiCall(apiUrl, token)
                
                if (response.isSuccess) {
                    parseProductResponse(response.data)
                } else {
                    VerificationResult.error("Google Play API verification failed: ${response.error}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during product verification", e)
                VerificationResult.error("Verification error: ${e.message}")
            }
        }
    }
    
    /**
     * Get a valid OAuth2 access token for Google Play Developer API
     */
    private suspend fun getValidAccessToken(): String? {
        return try {
            // Check if current token is still valid
            if (accessToken != null && System.currentTimeMillis() < tokenExpiryTime) {
                return accessToken
            }
            
            Log.d(TAG, "Obtaining new access token...")
            
            // Load service account credentials
            val credentials = loadServiceAccountCredentials()
            if (credentials == null) {
                Log.e(TAG, "Failed to load service account credentials")
                return null
            }
            
            // Refresh token
            credentials.refresh()
            
            // Store new token with expiry
            accessToken = credentials.accessToken.tokenValue
            tokenExpiryTime = System.currentTimeMillis() + (50 * 60 * 1000) // 50 minutes (tokens expire in 1 hour)
            
            Log.d(TAG, "Access token obtained successfully")
            accessToken
            
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining access token", e)
            null
        }
    }
    
    /**
     * Load service account credentials from environment or secure storage
     * 
     * In production, store the service account JSON securely:
     * - Environment variable
     * - Secure key management service
     * - Encrypted configuration file
     */
    private fun loadServiceAccountCredentials(): ServiceAccountCredentials? {
        return try {
            // Option 1: From environment variable (recommended for production)
            val serviceAccountJson = System.getenv("GOOGLE_SERVICE_ACCOUNT_JSON")
            if (serviceAccountJson != null) {
                val stream = ByteArrayInputStream(serviceAccountJson.toByteArray())
                return ServiceAccountCredentials.fromStream(stream)
                    .createScoped(listOf("https://www.googleapis.com/auth/androidpublisher"))
            }
            
            // Option 2: From secure file path
            val serviceAccountPath = System.getenv("GOOGLE_SERVICE_ACCOUNT_PATH")
            if (serviceAccountPath != null) {
                val stream = java.io.FileInputStream(serviceAccountPath)
                return ServiceAccountCredentials.fromStream(stream)
                    .createScoped(listOf("https://www.googleapis.com/auth/androidpublisher"))
            }
            
            // Option 3: From hardcoded credentials (DEVELOPMENT ONLY - NOT FOR PRODUCTION)
            // This should be replaced with secure credential storage
            val developmentCredentials = getDevelopmentCredentials()
            if (developmentCredentials != null) {
                Log.w(TAG, "⚠️ Using development credentials - NOT for production!")
                val stream = ByteArrayInputStream(developmentCredentials.toByteArray())
                return ServiceAccountCredentials.fromStream(stream)
                    .createScoped(listOf("https://www.googleapis.com/auth/androidpublisher"))
            }
            
            Log.e(TAG, "No service account credentials found")
            null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading service account credentials", e)
            null
        }
    }
    
    /**
     * Get development credentials (replace with your actual service account JSON)
     * 
     * To generate this:
     * 1. Go to Google Cloud Console
     * 2. Select your project
     * 3. Go to IAM & Admin > Service Accounts
     * 4. Create or select a service account
     * 5. Create a key (JSON format)
     * 6. Replace the placeholder below with your actual JSON
     */
    private fun getDevelopmentCredentials(): String? {
        // TODO: Replace with your actual service account JSON
        // This is just a placeholder structure
        return """
        {
          "type": "service_account",
          "project_id": "your-project-id",
          "private_key_id": "your-private-key-id",
          "private_key": "-----BEGIN PRIVATE KEY-----\nYOUR_PRIVATE_KEY_HERE\n-----END PRIVATE KEY-----\n",
          "client_email": "your-service-account@your-project-id.iam.gserviceaccount.com",
          "client_id": "your-client-id",
          "auth_uri": "https://accounts.google.com/o/oauth2/auth",
          "token_uri": "https://oauth2.googleapis.com/token",
          "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
          "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/your-service-account%40your-project-id.iam.gserviceaccount.com"
        }
        """.trimIndent()
        
        // Return null for now to force proper credential setup
        // return null
    }
    
    /**
     * Make authenticated API call to Google Play Developer API
     */
    private suspend fun makeAuthenticatedApiCall(
        url: String,
        accessToken: String
    ): ApiResponse {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "API call successful: ${response.take(200)}...")
                    ApiResponse.success(response)
                } else {
                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e(TAG, "API call failed with code $responseCode: $errorResponse")
                    ApiResponse.error("HTTP $responseCode: $errorResponse")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "API call exception", e)
                ApiResponse.error("Network error: ${e.message}")
            }
        }
    }
    
    /**
     * Parse subscription purchase response from Google Play API
     */
    private fun parseSubscriptionResponse(jsonResponse: String): VerificationResult {
        return try {
            val json = JSONObject(jsonResponse)
            
            val startTimeMillis = json.optLong("startTimeMillis", 0)
            val expiryTimeMillis = json.optLong("expiryTimeMillis", 0)
            val autoRenewing = json.optBoolean("autoRenewing", false)
            val orderId = json.optString("orderId", "")
            val purchaseType = json.optInt("purchaseType", 0)
            val acknowledgmentState = json.optInt("acknowledgmentState", 0)
            val kind = json.optString("kind", "")
            
            // Determine subscription status
            val currentTime = System.currentTimeMillis()
            val isExpired = expiryTimeMillis > 0 && currentTime > expiryTimeMillis
            val isActive = !isExpired && expiryTimeMillis > currentTime
            
            Log.d(TAG, "Subscription verification result:")
            Log.d(TAG, "  Start time: ${Date(startTimeMillis)}")
            Log.d(TAG, "  Expiry time: ${Date(expiryTimeMillis)}")
            Log.d(TAG, "  Auto-renewing: $autoRenewing")
            Log.d(TAG, "  Is active: $isActive")
            Log.d(TAG, "  Is expired: $isExpired")
            Log.d(TAG, "  Order ID: $orderId")
            
            if (isActive) {
                VerificationResult.success(
                    isValid = true,
                    subscriptionStatus = "active",
                    expiryTimeMillis = expiryTimeMillis,
                    autoRenewing = autoRenewing,
                    orderId = orderId,
                    message = "Subscription is active and valid"
                )
            } else if (isExpired) {
                VerificationResult.success(
                    isValid = false,
                    subscriptionStatus = "expired",
                    expiryTimeMillis = expiryTimeMillis,
                    autoRenewing = autoRenewing,
                    orderId = orderId,
                    message = "Subscription has expired"
                )
            } else {
                VerificationResult.error("Unable to determine subscription status")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing subscription response", e)
            VerificationResult.error("Failed to parse verification response: ${e.message}")
        }
    }
    
    /**
     * Parse product purchase response from Google Play API
     */
    private fun parseProductResponse(jsonResponse: String): VerificationResult {
        return try {
            val json = JSONObject(jsonResponse)
            
            val purchaseTimeMillis = json.optLong("purchaseTimeMillis", 0)
            val purchaseState = json.optInt("purchaseState", -1)
            val consumptionState = json.optInt("consumptionState", 0)
            val orderId = json.optString("orderId", "")
            val purchaseType = json.optInt("purchaseType", 0)
            val acknowledgmentState = json.optInt("acknowledgmentState", 0)
            
            Log.d(TAG, "Product verification result:")
            Log.d(TAG, "  Purchase time: ${Date(purchaseTimeMillis)}")
            Log.d(TAG, "  Purchase state: $purchaseState")
            Log.d(TAG, "  Consumption state: $consumptionState")
            Log.d(TAG, "  Order ID: $orderId")
            
            if (purchaseState == PURCHASE_STATE_PURCHASED) {
                VerificationResult.success(
                    isValid = true,
                    subscriptionStatus = "purchased",
                    expiryTimeMillis = 0, // Products don't expire
                    autoRenewing = false,
                    orderId = orderId,
                    message = "Product purchase is valid"
                )
            } else {
                VerificationResult.success(
                    isValid = false,
                    subscriptionStatus = "canceled",
                    expiryTimeMillis = 0,
                    autoRenewing = false,
                    orderId = orderId,
                    message = "Product purchase was canceled or refunded"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing product response", e)
            VerificationResult.error("Failed to parse verification response: ${e.message}")
        }
    }
}

/**
 * API response wrapper
 */
private data class ApiResponse(
    val isSuccess: Boolean,
    val data: String,
    val error: String?
) {
    companion object {
        fun success(data: String) = ApiResponse(true, data, null)
        fun error(error: String) = ApiResponse(false, "", error)
    }
}

/**
 * Purchase verification result
 */
data class VerificationResult(
    val isSuccess: Boolean,
    val isValid: Boolean,
    val subscriptionStatus: String,
    val expiryTimeMillis: Long,
    val autoRenewing: Boolean,
    val orderId: String,
    val message: String,
    val error: String?
) {
    companion object {
        fun success(
            isValid: Boolean,
            subscriptionStatus: String,
            expiryTimeMillis: Long,
            autoRenewing: Boolean,
            orderId: String,
            message: String
        ) = VerificationResult(
            isSuccess = true,
            isValid = isValid,
            subscriptionStatus = subscriptionStatus,
            expiryTimeMillis = expiryTimeMillis,
            autoRenewing = autoRenewing,
            orderId = orderId,
            message = message,
            error = null
        )
        
        fun error(error: String) = VerificationResult(
            isSuccess = false,
            isValid = false,
            subscriptionStatus = "error",
            expiryTimeMillis = 0,
            autoRenewing = false,
            orderId = "",
            message = error,
            error = error
        )
    }
}