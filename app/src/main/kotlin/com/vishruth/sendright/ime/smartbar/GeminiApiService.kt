/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vishruth.key1.ime.smartbar

import android.content.Context
import com.vishruth.key1.api.SupabaseConfig
import com.vishruth.sendright.lib.network.NetworkUtils
import com.vishruth.key1.ime.smartbar.MagicWandInstructions
import com.vishruth.key1.user.UserManager
import com.vishruth.key1.ime.ai.AiUsageTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import kotlin.math.pow
import kotlin.random.Random
import android.util.Log

object GeminiApiService {
    private const val TAG = "GeminiApiService"
    
    // SECURITY FIX: API keys now fetched dynamically from Supabase
    // This allows key rotation without app updates!
    private var FREE_API_KEYS = listOf<String>()
    private var PREMIUM_API_KEYS = listOf<String>()
    
    // Track if keys have been initialized
    private var keysInitialized = false
    
    // Separate tracking for free and premium keys
    private var currentFreeApiKeyIndex = 0
    private var currentPremiumApiKeyIndex = 0
    private val freeKeyLastRequestTime = mutableMapOf<Int, Long>() // Track per-key timing for free
    private val premiumKeyLastRequestTime = mutableMapOf<Int, Long>() // Track per-key timing for premium
    private val freeKeyFailureCount = mutableMapOf<Int, Int>() // Track key health for free
    private val premiumKeyFailureCount = mutableMapOf<Int, Int>() // Track key health for premium
    
    // Response caching to avoid repeated API calls for same inputs
    private val responseCache = mutableMapOf<String, Pair<String, Long>>() // Hash -> (Response, Timestamp)
    private const val CACHE_DURATION_MS = 300_000L // 5 minutes cache
    private const val MAX_CACHE_SIZE = 100 // Limit cache size to prevent memory issues
    
    // Request deduplication to prevent concurrent identical requests
    private val activeRequests = mutableMapOf<String, kotlinx.coroutines.Deferred<Result<String>>>()
    
    // Using Gemini 2.0 Flash (NOT 2.0 Pro or 2.5 Pro) for optimal performance and cost efficiency
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    private const val MIN_REQUEST_INTERVAL = 1500L // Increased from 1s to 1.5s for more conservative rate limiting
    private const val BASE_RETRY_DELAY_MS = 2000L // Base delay for exponential backoff (increased from 1.2s)
    private const val MAX_RETRY_DELAY_MS = 16000L // Maximum delay cap (16 seconds)
    private const val MAX_FALLBACK_KEYS = 2 // Try maximum 2 fallback keys (saves quota)
    
    @Serializable
    data class GeminiRequest(
        val contents: List<Content>
    )
    
    @Serializable
    data class Content(
        val parts: List<Part>
    )
    
    @Serializable
    data class Part(
        val text: String
    )
    
    @Serializable
    data class GeminiResponse(
        val candidates: List<Candidate>
    )
    
    @Serializable
    data class Candidate(
        val content: Content
    )
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Initialize API keys from Supabase
     * Call this on app startup to fetch the latest keys
     */
    suspend fun initializeApiKeys(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing API keys from Supabase...")
            
            val result = SupabaseConfig.fetchApiKeys(context)
            result.fold(
                onSuccess = { cache ->
                    FREE_API_KEYS = cache.freeKeys
                    PREMIUM_API_KEYS = cache.premiumKeys
                    keysInitialized = true
                    
                    Log.d(TAG, "✅ API keys initialized: ${FREE_API_KEYS.size} free, ${PREMIUM_API_KEYS.size} premium")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ Failed to initialize API keys from Supabase", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during API key initialization", e)
            Result.failure(e)
        }
    }
    
    /**
     * Refresh API keys from Supabase (force bypass cache)
     * Call this when you want to immediately pick up new keys
     */
    suspend fun refreshApiKeys(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Refreshing API keys from Supabase...")
            
            val result = SupabaseConfig.refreshKeys(context)
            result.fold(
                onSuccess = { cache ->
                    FREE_API_KEYS = cache.freeKeys
                    PREMIUM_API_KEYS = cache.premiumKeys
                    keysInitialized = true
                    
                    // Reset key indices to start with fresh keys
                    currentFreeApiKeyIndex = 0
                    currentPremiumApiKeyIndex = 0
                    freeKeyFailureCount.clear()
                    premiumKeyFailureCount.clear()
                    
                    Log.d(TAG, "✅ API keys refreshed: ${FREE_API_KEYS.size} free, ${PREMIUM_API_KEYS.size} premium")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ Failed to refresh API keys", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during API key refresh", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if the user is a pro user from multiple sources
     */
    private fun isProUser(): Boolean {
        return try {
            val aiUsageTracker = AiUsageTracker.getInstance()
            aiUsageTracker.isProUser()
        } catch (e: Exception) {
            false // Default to free user if there's any error
        }
    }
    
    /**
     * Get the appropriate API keys based on user subscription status
     */
    private fun getApiKeysForUser(): List<String> {
        return if (isProUser()) {
            PREMIUM_API_KEYS
        } else {
            FREE_API_KEYS
        }
    }
    
    /**
     * Get the current API key index based on user type
     */
    private fun getCurrentApiKeyIndex(): Int {
        return if (isProUser()) {
            currentPremiumApiKeyIndex
        } else {
            currentFreeApiKeyIndex
        }
    }
    
    /**
     * Set the current API key index based on user type
     */
    private fun setCurrentApiKeyIndex(index: Int) {
        if (isProUser()) {
            currentPremiumApiKeyIndex = index
        } else {
            currentFreeApiKeyIndex = index
        }
    }
    
    /**
     * Get the appropriate key tracking maps based on user type
     */
    private fun getKeyMaps(): Triple<MutableMap<Int, Long>, MutableMap<Int, Int>, Int> {
        return if (isProUser()) {
            Triple(premiumKeyLastRequestTime, premiumKeyFailureCount, currentPremiumApiKeyIndex)
        } else {
            Triple(freeKeyLastRequestTime, freeKeyFailureCount, currentFreeApiKeyIndex)
        }
    }
    
    suspend fun transformText(inputText: String, instruction: String, context: Context? = null, bypassCache: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        // Check network connectivity if context is provided
        context?.let {
            if (!NetworkUtils.isNetworkAvailable(it)) {
                return@withContext Result.failure(IOException("No Internet Connection"))
            }
        }
        
        // Create cache key for request deduplication and caching
        val cacheKey = generateCacheKey(inputText, instruction)
        
        // For regenerate requests, skip cache and active request checks to ensure fresh responses
        if (!bypassCache) {
            // Check if there's already an active request for the same input
            activeRequests[cacheKey]?.let { activeRequest ->
                try {
                    return@withContext activeRequest.await()
                } catch (e: Exception) {
                    // Remove failed request from active requests
                    activeRequests.remove(cacheKey)
                }
            }
            
            // Check cache first to avoid repeated API calls (only if not bypassing cache)
            getCachedResponse(cacheKey)?.let { cachedResponse ->
                return@withContext Result.success(cachedResponse)
            }
        }
        
        // Create deferred for this request to prevent duplicates (unless bypassing cache)
        val deferred = async {
            if (context != null) {
                performApiRequestWithNetworkMonitoring(inputText, instruction, cacheKey, context)
            } else {
                performApiRequest(inputText, instruction, cacheKey)
            }
        }
        
        if (!bypassCache) {
            activeRequests[cacheKey] = deferred
        }
        
        try {
            val result = deferred.await()
            
            // Cache successful responses (unless bypassing cache)
            if (!bypassCache) {
                result.onSuccess { response ->
                    cacheResponse(cacheKey, response)
                }
            }
            
            return@withContext result
        } finally {
            // Always remove from active requests when done (only if we added it)
            if (!bypassCache) {
                activeRequests.remove(cacheKey)
            }
        }
    }
    
    /**
     * Perform the actual API request with improved retry logic
     */
    private suspend fun performApiRequest(inputText: String, instruction: String, cacheKey: String): Result<String> = withContext(Dispatchers.IO) {
        
        // Get appropriate API keys based on user subscription status
        val apiKeys = getApiKeysForUser()
        val userType = if (isProUser()) "Premium" else "Free"
        
        // Validate API keys
        if (apiKeys.isEmpty()) {
            return@withContext Result.failure(Exception("🔑 $userType API key not configured. Please check settings."))
        }
        
        // Strategy 1: Try primary key with exponential backoff
        val currentIndex = getCurrentApiKeyIndex()
        val primaryResult = makeApiRequestWithExponentialBackoff(inputText, instruction, currentIndex, apiKeys, 0)
        primaryResult.fold(
            onSuccess = { 
                markKeyAsHealthy(currentIndex)
                return@withContext primaryResult 
            },
            onFailure = { error ->
                markKeyAsUnhealthy(currentIndex)
                
                // Check if it's a permanent failure that warrants immediate fallback
                val shouldImmediatelyTryFallback = error is IOException && (
                    error.message?.contains("403") == true ||
                    error.message?.contains("401") == true ||
                    error.message?.contains("429") == true ||
                    error.message?.contains("API key") == true
                )
                
                if (!shouldImmediatelyTryFallback) {
                    // Strategy 2: Exponential backoff retry with primary key (only for temporary issues)
                    val exponentialDelay = calculateExponentialBackoffDelay(1)
                    delay(exponentialDelay)
                    val retryResult = makeApiRequestWithExponentialBackoff(inputText, instruction, currentIndex, apiKeys, 1)
                    retryResult.fold(
                        onSuccess = { 
                            markKeyAsHealthy(currentIndex)
                            return@withContext retryResult 
                        },
                        onFailure = { /* Continue to fallback strategy */ }
                    )
                }
            }
        )
        
        // Strategy 3: Smart sequential fallback with exponential backoff
        if (apiKeys.size > 1) {
            return@withContext tryLimitedSequentialFallbackWithBackoff(inputText, instruction, apiKeys)
        }
        
        // No fallback keys available
        return@withContext Result.failure(Exception("❌ All services unavailable. Please try again later."))
    }
    
    /**
     * Enhanced API request with network monitoring during execution
     */
    private suspend fun performApiRequestWithNetworkMonitoring(inputText: String, instruction: String, cacheKey: String, context: Context): Result<String> = coroutineScope {
        
        // Start network monitoring in parallel
        val networkMonitoringJob = async {
            NetworkUtils.monitorNetworkDuringApiCall(context)
        }
        
        // Start API request in parallel
        val apiRequestJob = async {
            performApiRequest(inputText, instruction, cacheKey)
        }
        
        // Wait for either the API request to complete or network monitoring to timeout
        return@coroutineScope try {
            // Race between API request and network timeout
            select<Result<String>> {
                apiRequestJob.onAwait { result ->
                    networkMonitoringJob.cancel() // Cancel network monitoring as API completed
                    result
                }
                
                networkMonitoringJob.onAwait { networkResult ->
                    if (!networkResult) {
                        apiRequestJob.cancel() // Cancel API request due to network timeout
                        NetworkUtils.checkNetworkAndShowToast(context)
                        Result.failure(Exception("❌ Network timeout. Please check your connection."))
                    } else {
                        // Network is fine, wait for API result
                        apiRequestJob.await()
                    }
                }
            }
        } catch (e: Exception) {
            networkMonitoringJob.cancel()
            apiRequestJob.cancel()
            Result.failure(e)
        }
    }

    /**
     * Calculate exponential backoff delay with jitter to avoid thundering herd
     */
    private fun calculateExponentialBackoffDelay(attempt: Int): Long {
        val exponentialDelay = (BASE_RETRY_DELAY_MS * 2.0.pow(attempt.toDouble())).toLong()
        val cappedDelay = minOf(exponentialDelay, MAX_RETRY_DELAY_MS)
        // Add jitter (±25% randomness) to prevent thundering herd problem
        val jitter = (cappedDelay * 0.25 * (Random.nextDouble() - 0.5)).toLong()
        return maxOf(cappedDelay + jitter, MIN_REQUEST_INTERVAL)
    }
    
    private suspend fun tryLimitedSequentialFallbackWithBackoff(inputText: String, instruction: String, apiKeys: List<String>): Result<String> = withContext(Dispatchers.IO) {
        val currentIndex = getCurrentApiKeyIndex()
        val availableKeys = apiKeys.indices.filter { it != currentIndex }
        if (availableKeys.isEmpty()) {
            return@withContext Result.failure(Exception("❌ No fallback keys available"))
        }
        
        // Try only the best 2 fallback keys sequentially with exponential backoff
        val bestKeys = availableKeys.sortedBy { getKeyFailureCount(it) }.take(MAX_FALLBACK_KEYS)
        
        for ((attemptIndex, keyIndex) in bestKeys.withIndex()) {
            // Apply exponential backoff for fallback attempts
            if (attemptIndex > 0) {
                val backoffDelay = calculateExponentialBackoffDelay(attemptIndex)
                delay(backoffDelay)
            }
            
            val result = makeApiRequestWithExponentialBackoff(inputText, instruction, keyIndex, apiKeys, attemptIndex)
            
            result.fold(
                onSuccess = { response ->
                    // Success! Switch to this key and return
                    setCurrentApiKeyIndex(keyIndex)
                    markKeyAsHealthy(keyIndex)
                    return@withContext Result.success(response)
                },
                onFailure = { error ->
                    markKeyAsUnhealthy(keyIndex)
                    
                    // If this was the last attempt, return error
                    if (attemptIndex == bestKeys.size - 1) {
                        return@withContext Result.failure(Exception("❌ All ${bestKeys.size} backup services failed. Please try again later."))
                    }
                    // Otherwise continue to next key with exponential backoff
                }
            )
        }
        
        return@withContext Result.failure(Exception("❌ Unexpected fallback error"))
    }
    
    private suspend fun makeApiRequestWithExponentialBackoff(inputText: String, instruction: String, apiKeyIndex: Int, apiKeys: List<String>, attemptNumber: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Enhanced rate limiting with jitter to prevent thundering herd
            val (requestTimeMap, _, _) = getKeyMaps()
            val currentTime = System.currentTimeMillis()
            val lastRequestTime = requestTimeMap[apiKeyIndex] ?: 0L
            val timeSinceLastRequest = currentTime - lastRequestTime
            
            // Add jitter to prevent synchronized requests
            val jitteredInterval = MIN_REQUEST_INTERVAL + Random.nextLong(0, 500) // 0-500ms jitter
            
            if (timeSinceLastRequest < jitteredInterval) {
                delay(jitteredInterval - timeSinceLastRequest)
            }
            requestTimeMap[apiKeyIndex] = System.currentTimeMillis()
            
            val prompt = buildPrompt(inputText, instruction)
            val requestBody = GeminiRequest(
                contents = listOf(
                    Content(
                        parts = listOf(Part(text = prompt))
                    )
                )
            )
            
            val url = URL("$ENDPOINT?key=${apiKeys[apiKeyIndex]}")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Connection", "close") // Prevent keep-alive for faster cleanup
                setRequestProperty("User-Agent", "SendRightX-AI-Client/1.0") // Identify our client
                // Slightly increased timeouts for better reliability vs speed tradeoff
                connectTimeout = 10000 // 10 seconds
                readTimeout = 15000 // 15 seconds
                doOutput = true
            }
            
            val requestJson = json.encodeToString(GeminiRequest.serializer(), requestBody)
            connection.outputStream.use { outputStream ->
                outputStream.write(requestJson.toByteArray())
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseJson = connection.inputStream.bufferedReader().use { it.readText() }
                val response = json.decodeFromString(GeminiResponse.serializer(), responseJson)
                
                val transformedText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (transformedText != null) {
                    val trimmedText = transformedText.trim()
                    if (trimmedText.isNotBlank()) {
                        Result.success(trimmedText)
                    } else {
                        Result.failure(Exception("🤔 Empty response received. Please try again."))
                    }
                } else {
                    Result.failure(Exception("🤔 No response received. Please try again."))
                }
            } else if (responseCode == 429) {
                // Rate limited - apply exponential backoff for next attempt
                Result.failure(IOException("⏳ Rate limited. Applying exponential backoff..."))
            } else if (responseCode == 503) {
                Result.failure(IOException("🔧 Service temporarily unavailable. Trying alternative..."))
            } else if (responseCode == 500 || responseCode == 502 || responseCode == 504) {
                Result.failure(IOException("⚠️ Service experiencing issues. Trying alternative..."))
            } else if (responseCode == 403 || responseCode == 401) {
                Result.failure(IOException("🔐 Access denied. Switching to backup service..."))
            } else {
                Result.failure(IOException("❌ Service error (${responseCode}). Trying alternative..."))
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("🕰️ Request timeout. Trying faster alternative..."))
        } catch (e: Exception) {
            Result.failure(IOException("❌ Connection failed. Trying alternative service..."))
        }
    }
    
    private fun markKeyAsHealthy(keyIndex: Int) {
        val (_, failureMap, _) = getKeyMaps()
        failureMap[keyIndex] = 0
    }
    
    private fun markKeyAsUnhealthy(keyIndex: Int) {
        val (_, failureMap, _) = getKeyMaps()
        failureMap[keyIndex] = (failureMap[keyIndex] ?: 0) + 1
    }
    
    private fun getKeyFailureCount(keyIndex: Int): Int {
        val (_, failureMap, _) = getKeyMaps()
        return failureMap[keyIndex] ?: 0
    }
    
    /**
     * Generate cache key for request deduplication and caching
     * Includes date component to prevent cross-day cache conflicts
     */
    private fun generateCacheKey(inputText: String, instruction: String): String {
        // Include current date to prevent cache pollution across midnight resets
        val currentDay = System.currentTimeMillis() / (24 * 60 * 60 * 1000) // Days since epoch
        val combined = "$inputText|$instruction|$currentDay"
        return MessageDigest.getInstance("MD5")
            .digest(combined.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Get cached response if available and not expired
     */
    private fun getCachedResponse(cacheKey: String): String? {
        val currentTime = System.currentTimeMillis()
        val cached = responseCache[cacheKey]
        
        return if (cached != null && (currentTime - cached.second) < CACHE_DURATION_MS) {
            cached.first // Return cached response
        } else {
            // Remove expired cache entry
            responseCache.remove(cacheKey)
            null
        }
    }
    
    /**
     * Cache successful response with size management
     */
    private fun cacheResponse(cacheKey: String, response: String) {
        val currentTime = System.currentTimeMillis()
        
        // Remove expired entries and manage cache size
        if (responseCache.size >= MAX_CACHE_SIZE) {
            val expiredKeys = responseCache.filter { (currentTime - it.value.second) > CACHE_DURATION_MS }.keys
            expiredKeys.forEach { responseCache.remove(it) }
            
            // If still over limit, remove oldest entries
            if (responseCache.size >= MAX_CACHE_SIZE) {
                val oldestKeys = responseCache.entries
                    .sortedBy { it.value.second }
                    .take(responseCache.size - MAX_CACHE_SIZE + 1)
                    .map { it.key }
                oldestKeys.forEach { responseCache.remove(it) }
            }
        }
        
        responseCache[cacheKey] = Pair(response, currentTime)
    }
    
    private suspend fun makeApiRequest(inputText: String, instruction: String, apiKeyIndex: Int, apiKeys: List<String>): Result<String> = withContext(Dispatchers.IO) {
        // Updated to use the new exponential backoff method
        return@withContext makeApiRequestWithExponentialBackoff(inputText, instruction, apiKeyIndex, apiKeys, 0)
    }
    
    /**
     * Get information about the current API key configuration and cache status
     */
    fun getApiKeyInfo(): String {
        val apiKeys = getApiKeysForUser()
        val userType = if (isProUser()) "Premium" else "Free"
        val healthyKeys = apiKeys.indices.count { getKeyFailureCount(it) == 0 }
        val cacheInfo = "Cache: ${responseCache.size}/$MAX_CACHE_SIZE entries"
        return when {
            apiKeys.isEmpty() -> "No $userType API keys configured"
            apiKeys.size == 1 -> "1 $userType API key configured (no fallbacks) | $cacheInfo"
            else -> "${apiKeys.size} $userType API keys configured (1 primary + ${apiKeys.size - 1} fallbacks) - $healthyKeys healthy | $cacheInfo"
        }
    }
    
    /**
     * Clear response cache and active requests (called during daily reset to avoid stale data)
     */
    fun clearCacheForDailyReset() {
        responseCache.clear()
        activeRequests.clear()
        // Reset key failure counts to give fresh start each day
        freeKeyFailureCount.clear()
        premiumKeyFailureCount.clear()
        // Reset to primary keys for new day
        currentFreeApiKeyIndex = 0
        currentPremiumApiKeyIndex = 0
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): String {
        val currentTime = System.currentTimeMillis()
        val validEntries = responseCache.count { (currentTime - it.value.second) < CACHE_DURATION_MS }
        val expiredEntries = responseCache.size - validEntries
        return "Cache: $validEntries valid, $expiredEntries expired, ${activeRequests.size} active requests"
    }
    
    /**
     * Reset to primary API key (for testing purposes)
     */
    fun resetToPrimaryKey() {
        if (isProUser()) {
            currentPremiumApiKeyIndex = 0
            premiumKeyFailureCount.clear()
            premiumKeyLastRequestTime.clear()
        } else {
            currentFreeApiKeyIndex = 0
            freeKeyFailureCount.clear()
            freeKeyLastRequestTime.clear()
        }
    }
    
    /**
     * Get the healthiest API key index (lowest failure count)
     */
    fun getHealthiestKeyIndex(): Int {
        val apiKeys = getApiKeysForUser()
        return apiKeys.indices.minByOrNull { getKeyFailureCount(it) } ?: 0
    }
    
    /**
     * Switch to the healthiest API key
     */
    fun switchToHealthiestKey() {
        setCurrentApiKeyIndex(getHealthiestKeyIndex())
    }
    
    private fun buildPrompt(inputText: String, instruction: String): String {
        // Check if the instruction is already enhanced with context (contains context markers)
        val isEnhancedInstruction = instruction.contains("🧠 CONTEXT INTELLIGENCE SYSTEM:") ||
                                   instruction.contains("👤 USER PROFILE:") ||
                                   instruction.contains("🎯 USER'S CUSTOM ACTION:")
        
        if (isEnhancedInstruction) {
            // For enhanced instructions, use them directly with the input text
            return buildString {
                appendLine(instruction)
                appendLine()
                appendLine("📝 USER'S INPUT:")
                appendLine("\"$inputText\"")
                appendLine()
                appendLine("✅ CRITICAL RESPONSE RULES:")
                appendLine("• STRICTLY FOLLOW the RESPONSE LENGTH REQUIREMENT specified above")
                appendLine("• Provide only the final result - no explanations unless asked")
                appendLine("• Be natural and contextually appropriate")
                appendLine("• Use the personal context intelligently")
                appendLine("• All personal references (my coach, my boss, etc.) refer to the USER'S relationships")
                appendLine("• IMPORTANT: Respect the exact sentence count/length specified in the RESPONSE LENGTH REQUIREMENT")
            }
        }
        
        // Special handling for basic chat functionality - treat as direct conversation
        if (instruction == MagicWandInstructions.CHAT) {
            return """
                $instruction
                
                User message: "$inputText"
                
                Respond naturally and directly to the user's input without any prefixes or extra text. Provide helpful, concise responses.
            """.trimIndent()
        }
        
        return buildSimplePrompt(inputText, instruction)
    }
    

    
    /**
     * Build simple clean prompt 
     */
    private fun buildSimplePrompt(inputText: String, instruction: String): String {
        return buildString {
            appendLine("🎯 TASK:")
            appendLine(instruction)
            appendLine()
            appendLine("📝 USER'S INPUT:")
            appendLine("\"$inputText\"")
            appendLine()
            appendLine("✅ RULES:")
            appendLine("• Provide only the final result - no explanations")
            appendLine("• Be natural and contextually appropriate")
            appendLine("• Match the expected language and tone")
            appendLine("• IMPORTANT: You are helping the USER - all personal references (my coach, my boss, etc.) refer to the USER'S relationships")
            appendLine("• When writing emails/messages, you're helping the USER communicate with THEIR contacts")
        }
    }
    

    

    

    

    

    

    

    
    /**
     * Extract the primary instruction without context details
     */
    private fun extractPrimaryInstruction(instruction: String): String {
        val lines = instruction.lines()
        val cleanLines = mutableListOf<String>()
        
        for (line in lines) {
            val trimmedLine = line.trim()
            // Skip context-heavy lines and metadata
            if (!isContextLine(trimmedLine) && !isMetadataLine(trimmedLine)) {
                cleanLines.add(trimmedLine)
            }
        }
        
        return cleanLines.joinToString(" ").trim().takeIf { it.isNotBlank() } 
            ?: "Transform the given text according to the context requirements."
    }
    
    /**
     * Extract context details from surrounding lines
     */
    private fun extractContextDetails(lines: List<String>, startIndex: Int): String {
        val details = mutableListOf<String>()
        val endIndex = minOf(startIndex + 10, lines.size) // Look ahead up to 10 lines
        
        for (i in startIndex until endIndex) {
            val line = lines[i].trim()
            if (line.isNotBlank() && !isMetadataLine(line)) {
                details.add(line)
            }
        }
        
        return details.joinToString(". ").take(200) // Limit details to 200 chars
    }
    
    /**
     * Check if a line contains context information
     */
    private fun isContextLine(line: String): Boolean {
        val contextKeywords = listOf(
            "context", "relationship", "database", "profile", "user",
            "cultural", "linguistic", "detected", "relevant", "guidelines"
        )
        return contextKeywords.any { keyword -> line.contains(keyword, ignoreCase = true) }
    }
    
    /**
     * Check if a line is metadata (emojis, headers, decorative)
     */
    private fun isMetadataLine(line: String): Boolean {
        return line.startsWith("🎯") || line.startsWith("📋") || line.startsWith("•") ||
               line.startsWith("━") || line.contains("SYSTEM:") || line.contains("GUIDELINES:")
    }
    
    /**
     * Build standard prompt for non-context instructions
     */
    private fun buildStandardPrompt(inputText: String, instruction: String): String {
        return """
            $instruction
            
            Text to transform: "$inputText"
            
            Important: Provide ONLY the transformed text as your response. No explanations, no prefixes like "Here is the answer", no suffixes like "Would you like me to do...", just the direct transformed text result.
        """.trimIndent()
    }
    

}
