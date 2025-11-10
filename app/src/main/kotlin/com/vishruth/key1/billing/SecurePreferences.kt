/*
 * Copyright (C) 2025 SendRight 3.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.billing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure encrypted SharedPreferences wrapper
 * 
 * SECURITY FIX: Replaces plain SharedPreferences with encrypted storage
 * to protect sensitive data like subscription status and entitlements.
 * 
 * This ensures that even on rooted devices or with physical access,
 * sensitive data cannot be easily tampered with or read.
 */
object SecurePreferences {
    
    private const val TAG = "SecurePreferences"
    private const val ENCRYPTED_PREFS_NAME = "sendright_secure_prefs"
    
    /**
     * Get encrypted SharedPreferences instance
     * 
     * SECURITY ENHANCEMENT: Uses AES256-GCM encryption with hardware-backed keys
     * when available. Falls back to plain SharedPreferences only if encryption
     * setup fails (e.g., very old devices), with a warning.
     * 
     * @param context Application context
     * @param prefsName Optional custom preferences name
     * @return Encrypted SharedPreferences instance
     */
    fun getEncryptedPreferences(
        context: Context,
        prefsName: String = ENCRYPTED_PREFS_NAME
    ): SharedPreferences {
        return try {
            // Create or retrieve the master key for encryption
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            // Create encrypted SharedPreferences
            EncryptedSharedPreferences.create(
                context,
                prefsName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // FALLBACK: If encryption setup fails (very rare), fall back to regular prefs
            // with a loud warning. This ensures app doesn't crash but alerts us to the issue.
            Log.e(TAG, "⚠️ CRITICAL: Failed to create encrypted preferences, falling back to plain storage", e)
            Log.e(TAG, "⚠️ This should be investigated immediately - sensitive data may be at risk")
            
            // Use plain SharedPreferences as last resort
            context.getSharedPreferences("${prefsName}_fallback", Context.MODE_PRIVATE)
        }
    }
    
    /**
     * Migrate existing plain preferences to encrypted storage
     * 
     * MIGRATION HELPER: Call this once to move existing data from plain
     * SharedPreferences to encrypted storage.
     * 
     * @param context Application context
     * @param oldPrefsName Name of the old plain SharedPreferences
     * @param newPrefsName Name for the new encrypted SharedPreferences
     * @return true if migration succeeded or no migration needed
     */
    fun migrateToEncrypted(
        context: Context,
        oldPrefsName: String,
        newPrefsName: String = ENCRYPTED_PREFS_NAME
    ): Boolean {
        return try {
            val oldPrefs = context.getSharedPreferences(oldPrefsName, Context.MODE_PRIVATE)
            val newPrefs = getEncryptedPreferences(context, newPrefsName)
            
            // Check if migration already done or not needed
            if (oldPrefs.all.isEmpty()) {
                Log.d(TAG, "No data to migrate from $oldPrefsName")
                return true
            }
            
            // Check if already migrated
            if (newPrefs.getBoolean("_migration_completed_$oldPrefsName", false)) {
                Log.d(TAG, "Migration already completed for $oldPrefsName")
                return true
            }
            
            // Copy all data from old to new
            val editor = newPrefs.edit()
            oldPrefs.all.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
                }
            }
            
            // Mark migration as completed
            editor.putBoolean("_migration_completed_$oldPrefsName", true)
            editor.apply()
            
            // Clear old preferences after successful migration
            oldPrefs.edit().clear().apply()
            
            Log.i(TAG, "Successfully migrated ${oldPrefs.all.size} preferences from $oldPrefsName to encrypted storage")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate preferences to encrypted storage", e)
            false
        }
    }
}
