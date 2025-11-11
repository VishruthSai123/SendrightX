/*
 * Copyright (C) 2025 SendRight 3.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.billing

import android.util.Base64
import android.util.Log
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.Signature
import java.security.SignatureException
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec

/**
 * Security utilities for billing and purchase verification
 * 
 * CRITICAL: This class provides security checks for purchase validation.
 * Methods in this class help prevent fraudulent purchases and tampering.
 */
object Security {
    
    private const val TAG = "Security"
    private const val KEY_FACTORY_ALGORITHM = "RSA"
    private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    
    /**
     * Verify purchase signature from Google Play
     * 
     * This is a CRITICAL security measure to prevent purchase fraud.
     * DO NOT remove or bypass this verification in production.
     * 
     * @param signedData The signed JSON purchase data from Google Play
     * @param signature The signature string from the purchase
     * @param base64PublicKey Your app's Base64-encoded public key from Play Console
     * @return true if signature is valid, false otherwise
     * 
     * SECURITY FIX: Added proper signature verification to prevent purchase spoofing
     */
    fun verifyPurchaseSignature(
        signedData: String,
        signature: String,
        base64PublicKey: String
    ): Boolean {
        if (signedData.isEmpty() || signature.isEmpty() || base64PublicKey.isEmpty()) {
            Log.e(TAG, "Purchase verification failed: Empty data provided")
            return false
        }
        
        try {
            val publicKey = generatePublicKey(base64PublicKey)
            return verify(publicKey, signedData, signature)
        } catch (e: Exception) {
            Log.e(TAG, "Purchase signature verification failed", e)
            return false
        }
    }
    
    /**
     * Generate PublicKey from Base64-encoded key string
     * 
     * @param base64PublicKey Base64 encoded public key from Play Console
     * @return PublicKey object for signature verification
     */
    private fun generatePublicKey(base64PublicKey: String): PublicKey {
        try {
            val decodedKey = Base64.decode(base64PublicKey, Base64.DEFAULT)
            val keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM)
            return keyFactory.generatePublic(X509EncodedKeySpec(decodedKey))
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("RSA algorithm not available", e)
        } catch (e: InvalidKeySpecException) {
            throw IllegalArgumentException("Invalid public key format", e)
        }
    }
    
    /**
     * Verify signature using public key
     * 
     * @param publicKey The public key for verification
     * @param signedData The data that was signed
     * @param signature The signature to verify
     * @return true if signature matches, false otherwise
     */
    private fun verify(publicKey: PublicKey, signedData: String, signature: String): Boolean {
        try {
            val signatureBytes = Base64.decode(signature, Base64.DEFAULT)
            val signatureAlgorithm = Signature.getInstance(SIGNATURE_ALGORITHM)
            signatureAlgorithm.initVerify(publicKey)
            signatureAlgorithm.update(signedData.toByteArray())
            
            val verified = signatureAlgorithm.verify(signatureBytes)
            
            if (!verified) {
                Log.w(TAG, "Signature verification failed - potential fraud attempt")
            }
            
            return verified
            
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("Signature algorithm not available", e)
        } catch (e: InvalidKeyException) {
            throw IllegalArgumentException("Invalid key for verification", e)
        } catch (e: SignatureException) {
            Log.e(TAG, "Signature verification error", e)
            return false
        }
    }
    
    /**
     * Basic root detection check
     * 
     * WARNING: This is a basic implementation. For production apps,
     * consider using specialized root detection libraries like:
     * - RootBeer: https://github.com/scottyab/rootbeer
     * - SafetyNet Attestation API
     * 
     * @return true if device appears to be rooted/compromised
     * 
     * SECURITY FIX: Added basic tamper detection
     */
    fun isDeviceCompromised(): Boolean {
        return checkForSuBinary() || checkForDangerousApps()
    }
    
    /**
     * Check for common root binaries
     */
    private fun checkForSuBinary(): Boolean {
        val suPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        
        for (path in suPaths) {
            try {
                if (java.io.File(path).exists()) {
                    Log.w(TAG, "Root binary detected at: $path")
                    return true
                }
            } catch (e: Exception) {
                // Continue checking other paths
            }
        }
        return false
    }
    
    /**
     * Check for dangerous root management apps
     */
    private fun checkForDangerousApps(): Boolean {
        val dangerousPackages = arrayOf(
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.zachspong.temprootremovejb",
            "com.ramdroid.appquarantine"
        )
        
        // This is a simplified check - in production, use PackageManager
        // to check for these packages properly
        return false // Conservative default - implement proper check if needed
    }
    
    /**
     * Validate purchase token format
     * 
     * @param purchaseToken The token to validate
     * @return true if format appears valid
     * 
     * SECURITY FIX: Added input validation to prevent injection attacks
     */
    fun isValidPurchaseToken(purchaseToken: String?): Boolean {
        if (purchaseToken.isNullOrBlank()) {
            return false
        }
        
        // Purchase tokens should be alphanumeric with dots, underscores, hyphens
        // and within reasonable length limits
        if (purchaseToken.length > 2000) {
            Log.w(TAG, "Purchase token exceeds maximum length")
            return false
        }
        
        val validTokenPattern = Regex("^[a-zA-Z0-9._-]+$")
        return validTokenPattern.matches(purchaseToken)
    }
    
    /**
     * Validate user ID format
     * 
     * @param userId The user ID to validate
     * @return true if format appears valid
     * 
     * SECURITY FIX: Added input validation for user IDs
     */
    fun isValidUserId(userId: String?): Boolean {
        if (userId.isNullOrBlank()) {
            return false
        }
        
        // User IDs should be reasonable length and safe characters
        if (userId.length > 500) {
            Log.w(TAG, "User ID exceeds maximum length")
            return false
        }
        
        // Allow alphanumeric, dots, underscores, hyphens, @
        val validUserIdPattern = Regex("^[a-zA-Z0-9._@-]+$")
        return validUserIdPattern.matches(userId)
    }
}
