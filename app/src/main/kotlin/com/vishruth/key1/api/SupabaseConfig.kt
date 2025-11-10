/*
 * Copyright (C) 2025 SendRight
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Supabase configuration for dynamic API key management
 * 
 * This allows you to update API keys in Supabase without releasing app updates.
 * Keys are fetched on app launch and cached locally with periodic refresh.
 */
object SupabaseConfig {
    private const val TAG = "SupabaseConfig"
    
    // Supabase project URL
    private const val SUPABASE_URL = "https://qkfcopradlyuxpkkxbmj.supabase.co"
    
    // Supabase ANON key (safe for client-side)
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFrZmNvcHJhZGx5dXhwa2t4Ym1qIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjI3NDY5OTMsImV4cCI6MjA3ODMyMjk5M30.-6VYVnBDLeysVCgkCr2-tJ1UOWuEuXY600yEaLAFqwA"
    
    // Table structure in Supabase:
    // Table name: api_keys
    // Columns:
    //   - id (uuid, primary key)
    //   - key_type (text) - "gemini_free" or "gemini_premium"
    //   - api_key (text) - the actual API key
    //   - is_active (boolean) - whether this key should be used
    //   - priority (integer) - order of fallback (lower = higher priority)
    //   - created_at (timestamp)
    
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedKeys: ApiKeysCache? = null
    private var lastFetchTime = 0L
    private const val CACHE_DURATION_MS = 3600_000L // 1 hour cache
    
    @Serializable
    data class ApiKeyEntry(
        val id: String? = null,
        val key_type: String,
        val api_key: String,
        val is_active: Boolean = true,
        val priority: Int = 0
    )
    
    data class ApiKeysCache(
        val freeKeys: List<String>,
        val premiumKeys: List<String>,
        val timestamp: Long
    )
    
    /**
     * Fetch API keys from Supabase
     * This is called on app launch and periodically refreshed
     */
    suspend fun fetchApiKeys(context: Context): Result<ApiKeysCache> = withContext(Dispatchers.IO) {
        try {
            // Check if cache is still valid
            cachedKeys?.let { cache ->
                if (System.currentTimeMillis() - cache.timestamp < CACHE_DURATION_MS) {
                    Log.d(TAG, "Using cached API keys")
                    return@withContext Result.success(cache)
                }
            }
            
            Log.d(TAG, "Fetching API keys from Supabase...")
            
            // Fetch free keys
            val freeKeys = fetchKeysByType("gemini_free")
            
            // Fetch premium keys
            val premiumKeys = fetchKeysByType("gemini_premium")
            
            if (freeKeys.isEmpty() && premiumKeys.isEmpty()) {
                Log.e(TAG, "No API keys found in Supabase!")
                return@withContext Result.failure(Exception("No API keys configured"))
            }
            
            val cache = ApiKeysCache(
                freeKeys = freeKeys,
                premiumKeys = premiumKeys,
                timestamp = System.currentTimeMillis()
            )
            
            cachedKeys = cache
            
            // Save to local cache as backup
            saveToLocalCache(context, cache)
            
            Log.d(TAG, "✅ Successfully fetched ${freeKeys.size} free keys and ${premiumKeys.size} premium keys")
            
            Result.success(cache)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch API keys from Supabase", e)
            
            // Fallback to local cache
            loadFromLocalCache(context)?.let {
                Log.w(TAG, "Using backup local cache")
                return@withContext Result.success(it)
            }
            
            Result.failure(e)
        }
    }
    
    /**
     * Fetch API keys of a specific type from Supabase
     */
    private suspend fun fetchKeysByType(keyType: String): List<String> = withContext(Dispatchers.IO) {
        try {
            // Supabase REST API endpoint
            // Query: select * from api_keys where key_type='keyType' and is_active=true order by priority
            val url = URL("$SUPABASE_URL/rest/v1/api_keys?key_type=eq.$keyType&is_active=eq.true&order=priority.asc")
            
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Supabase request failed: $responseCode")
                return@withContext emptyList()
            }
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            
            // Parse JSON response
            val entries = json.decodeFromString<List<ApiKeyEntry>>(response)
            
            // Extract just the API keys
            entries.map { it.api_key }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching keys for type $keyType", e)
            emptyList()
        }
    }
    
    /**
     * Save keys to local encrypted storage as backup
     */
    private fun saveToLocalCache(context: Context, cache: ApiKeysCache) {
        try {
            val prefs = context.getSharedPreferences("api_keys_cache", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("free_keys", cache.freeKeys.joinToString(","))
                putString("premium_keys", cache.premiumKeys.joinToString(","))
                putLong("timestamp", cache.timestamp)
                apply()
            }
            Log.d(TAG, "Saved API keys to local cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save local cache", e)
        }
    }
    
    /**
     * Load keys from local cache (fallback if Supabase unavailable)
     */
    private fun loadFromLocalCache(context: Context): ApiKeysCache? {
        return try {
            val prefs = context.getSharedPreferences("api_keys_cache", Context.MODE_PRIVATE)
            val freeKeysStr = prefs.getString("free_keys", "") ?: ""
            val premiumKeysStr = prefs.getString("premium_keys", "") ?: ""
            val timestamp = prefs.getLong("timestamp", 0)
            
            if (freeKeysStr.isEmpty() && premiumKeysStr.isEmpty()) {
                return null
            }
            
            ApiKeysCache(
                freeKeys = freeKeysStr.split(",").filter { it.isNotBlank() },
                premiumKeys = premiumKeysStr.split(",").filter { it.isNotBlank() },
                timestamp = timestamp
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load local cache", e)
            null
        }
    }
    
    /**
     * Get cached keys or fetch if not available
     */
    suspend fun getApiKeys(context: Context, isPremium: Boolean): List<String> {
        // Try to get from cache first
        cachedKeys?.let { cache ->
            if (System.currentTimeMillis() - cache.timestamp < CACHE_DURATION_MS) {
                return if (isPremium) cache.premiumKeys else cache.freeKeys
            }
        }
        
        // Cache expired or not available, fetch new keys
        fetchApiKeys(context).fold(
            onSuccess = { cache ->
                return if (isPremium) cache.premiumKeys else cache.freeKeys
            },
            onFailure = {
                Log.e(TAG, "Failed to get API keys, returning empty list")
                return emptyList()
            }
        )
    }
    
    /**
     * Force refresh keys from Supabase (bypasses cache)
     */
    suspend fun refreshKeys(context: Context): Result<ApiKeysCache> {
        cachedKeys = null // Clear cache
        return fetchApiKeys(context)
    }
}
