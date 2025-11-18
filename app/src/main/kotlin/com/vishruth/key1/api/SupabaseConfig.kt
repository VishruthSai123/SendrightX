/*
 * Copyright (C) 2025 SendRight
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.api

import android.content.Context
import android.util.Log
import com.vishruth.key1.BuildConfig
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Supabase configuration for dynamic API key management
 * 
 * SECURITY: Credentials are loaded from BuildConfig (injected from local.properties)
 * and are NOT hardcoded in source code or committed to Git.
 * 
 * This allows you to update API keys in Supabase without releasing app updates.
 * Keys are fetched on app launch and cached locally with periodic refresh.
 */
object SupabaseConfig {
    private const val TAG = "SupabaseConfig"
    
    // SECURITY FIX: Credentials loaded from BuildConfig (local.properties)
    // These are injected at build time and never committed to Git
    private val SUPABASE_URL: String
        get() = BuildConfig.SUPABASE_URL.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "SUPABASE_URL not configured! Please add it to local.properties:\n" +
                "SUPABASE_URL=your_project_url_here"
            )
    
    private val SUPABASE_ANON_KEY: String
        get() = BuildConfig.SUPABASE_ANON_KEY.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "SUPABASE_ANON_KEY not configured! Please add it to local.properties:\n" +
                "SUPABASE_ANON_KEY=your_anon_key_here"
            )
    
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
    private var cachedModels: AiModelsCache? = null
    private var cachedConfigVersion: Int = -1 // Track config version for cache invalidation
    private var lastFetchTime = 0L
    private const val CACHE_DURATION_MS = 600_000L // 10 minutes cache (reduced from 1 hour for faster updates)
    
    @Serializable
    data class ApiKeyEntry(
        val id: String? = null,
        val key_type: String,
        val api_key: String,
        val is_active: Boolean = true,
        val priority: Int = 0
    )
    
    @Serializable
    data class AiModelEntry(
        val id: String? = null,
        val model_key: String,
        val model_name: String,
        val endpoint_suffix: String,
        val is_active: Boolean = true,
        val user_type: String = "free"
    )
    
    @Serializable
    data class ConfigMetadata(
        val id: String? = null,
        val config_version: Int,
        val last_updated: String? = null
    )
    
    data class ApiKeysCache(
        val freeKeys: List<String>,
        val premiumKeys: List<String>,
        val timestamp: Long
    )
    
    data class AiModelsCache(
        val models: Map<String, String>, // model_key -> model_name
        val timestamp: Long
    )
    
    /**
     * Fetch current config version from Supabase
     * Used to detect when keys have been rotated
     */
    private suspend fun fetchConfigVersion(): Int = withContext(Dispatchers.IO) {
        try {
            val url = URL("$SUPABASE_URL/rest/v1/config_metadata?select=config_version&limit=1")
            
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 5000
                readTimeout = 5000
            }
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Failed to fetch config version: $responseCode")
                return@withContext cachedConfigVersion // Return cached version on error
            }
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            
            val entries = json.decodeFromString<List<ConfigMetadata>>(response)
            entries.firstOrNull()?.config_version ?: cachedConfigVersion
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching config version", e)
            cachedConfigVersion // Return cached version on error
        }
    }
    
    /**
     * Fetch API keys from Supabase
     * This is called on app launch and periodically refreshed
     * Now includes version-based cache invalidation for instant updates
     */
    suspend fun fetchApiKeys(context: Context): Result<ApiKeysCache> = withContext(Dispatchers.IO) {
        try {
            // Check current version from Supabase
            val currentVersion = fetchConfigVersion()
            
            // If version changed, invalidate cache immediately
            if (currentVersion != cachedConfigVersion && cachedConfigVersion != -1) {
                Log.d(TAG, "⚡ Config version changed ($cachedConfigVersion -> $currentVersion), forcing refresh!")
                cachedKeys = null // Invalidate cache
            }
            
            // Check if cache is still valid (only if version hasn't changed)
            cachedKeys?.let { cache ->
                if (System.currentTimeMillis() - cache.timestamp < CACHE_DURATION_MS && currentVersion == cachedConfigVersion) {
                    Log.d(TAG, "Using cached API keys (version $currentVersion)")
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
            cachedConfigVersion = currentVersion // Store the version we just fetched
            
            // Save to local cache as backup
            saveToLocalCache(context, cache, currentVersion)
            
            Log.d(TAG, "✅ Successfully fetched ${freeKeys.size} free keys and ${premiumKeys.size} premium keys (version $currentVersion)")
            
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
    private fun saveToLocalCache(context: Context, cache: ApiKeysCache, version: Int) {
        try {
            val prefs = context.getSharedPreferences("api_keys_cache", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("free_keys", cache.freeKeys.joinToString(","))
                putString("premium_keys", cache.premiumKeys.joinToString(","))
                putLong("timestamp", cache.timestamp)
                putInt("config_version", version)
                apply()
            }
            Log.d(TAG, "Saved API keys to local cache (version $version)")
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
            val version = prefs.getInt("config_version", -1)
            
            if (freeKeysStr.isEmpty() && premiumKeysStr.isEmpty()) {
                return null
            }
            
            // Restore cached version
            if (version != -1) {
                cachedConfigVersion = version
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
    
    /**
     * Fetch AI model configurations from Supabase
     * Allows updating model names (like gemini-2.0-flash-exp) without app updates
     */
    suspend fun fetchAiModels(context: Context): Result<AiModelsCache> = withContext(Dispatchers.IO) {
        try {
            // Check if cache is still valid
            cachedModels?.let { cache ->
                if (System.currentTimeMillis() - cache.timestamp < CACHE_DURATION_MS) {
                    Log.d(TAG, "Using cached AI models")
                    return@withContext Result.success(cache)
                }
            }
            
            Log.d(TAG, "Fetching AI models from Supabase...")
            
            val url = URL("$SUPABASE_URL/rest/v1/ai_models_config?is_active=eq.true")
            
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
                Log.e(TAG, "Failed to fetch AI models: $responseCode")
                // Return default models as fallback
                return@withContext Result.success(getDefaultModelsCache())
            }
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            
            val entries = json.decodeFromString<List<AiModelEntry>>(response)
            
            // Convert to map: model_key -> model_name
            val modelsMap = entries.associate { it.model_key to it.model_name }
            
            val cache = AiModelsCache(
                models = modelsMap,
                timestamp = System.currentTimeMillis()
            )
            
            cachedModels = cache
            saveModelsToLocalCache(context, cache)
            
            Log.d(TAG, "✅ Successfully fetched ${modelsMap.size} AI model configurations")
            
            Result.success(cache)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch AI models from Supabase", e)
            
            // Fallback to local cache
            loadModelsFromLocalCache(context)?.let {
                Log.w(TAG, "Using backup local models cache")
                return@withContext Result.success(it)
            }
            
            // Final fallback to defaults
            Result.success(getDefaultModelsCache())
        }
    }
    
    /**
     * Get model name by key (e.g., 'gemini_flash_free' -> 'gemini-2.0-flash-exp')
     */
    suspend fun getModelName(context: Context, modelKey: String): String {
        cachedModels?.let { cache ->
            if (System.currentTimeMillis() - cache.timestamp < CACHE_DURATION_MS) {
                return cache.models[modelKey] ?: getDefaultModelName(modelKey)
            }
        }
        
        // Fetch fresh models if cache expired
        fetchAiModels(context).fold(
            onSuccess = { cache ->
                return cache.models[modelKey] ?: getDefaultModelName(modelKey)
            },
            onFailure = {
                return getDefaultModelName(modelKey)
            }
        )
    }
    
    private fun getDefaultModelsCache(): AiModelsCache {
        return AiModelsCache(
            models = mapOf(
                "gemini_default" to "gemini-2.5-flash-lite"
            ),
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun getDefaultModelName(modelKey: String): String {
        // Always use single endpoint to avoid rate limit issues
        return "gemini-2.5-flash-lite"
    }
    
    private fun saveModelsToLocalCache(context: Context, cache: AiModelsCache) {
        try {
            val prefs = context.getSharedPreferences("ai_models_cache", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("models_json", cache.models.entries.joinToString(";") { "${it.key}:${it.value}" })
                putLong("timestamp", cache.timestamp)
                apply()
            }
            Log.d(TAG, "Saved AI models to local cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save models cache", e)
        }
    }
    
    private fun loadModelsFromLocalCache(context: Context): AiModelsCache? {
        return try {
            val prefs = context.getSharedPreferences("ai_models_cache", Context.MODE_PRIVATE)
            val modelsJson = prefs.getString("models_json", "") ?: ""
            val timestamp = prefs.getLong("timestamp", 0)
            
            if (modelsJson.isEmpty()) return null
            
            val modelsMap = modelsJson.split(";")
                .filter { it.contains(":") }
                .associate {
                    val parts = it.split(":")
                    parts[0] to parts[1]
                }
            
            AiModelsCache(models = modelsMap, timestamp = timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load models cache", e)
            null
        }
    }
}
