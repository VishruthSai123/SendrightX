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
import com.vishruth.key1.BuildConfig
import com.vishruth.sendright.lib.network.NetworkUtils
import com.vishruth.key1.ime.smartbar.MagicWandInstructions
import com.vishruth.key1.user.UserManager
import com.vishruth.key1.ime.ai.AiUsageTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

object GeminiApiService {
    // Free user API keys (existing system)
    private val FREE_API_KEYS = listOf(
        BuildConfig.GEMINI_API_KEY,
        BuildConfig.GEMINI_API_KEY_FALLBACK_1,
        BuildConfig.GEMINI_API_KEY_FALLBACK_2,
        BuildConfig.GEMINI_API_KEY_FALLBACK_3
    ).filter { it.isNotBlank() } // Only use non-empty API keys
    
    // Premium API keys for pro users - Same level of fallback safety as free users
    private val PREMIUM_API_KEYS = listOf(
        BuildConfig.GEMINI_API_KEY_PREMIUM,
        BuildConfig.GEMINI_API_KEY_PREMIUM_FALLBACK_1,
        BuildConfig.GEMINI_API_KEY_PREMIUM_FALLBACK_2,
        BuildConfig.GEMINI_API_KEY_PREMIUM_FALLBACK_3
    ).filter { it.isNotBlank() } // Only use non-empty API keys
    
    // Separate tracking for free and premium keys
    private var currentFreeApiKeyIndex = 0
    private var currentPremiumApiKeyIndex = 0
    private val freeKeyLastRequestTime = mutableMapOf<Int, Long>() // Track per-key timing for free
    private val premiumKeyLastRequestTime = mutableMapOf<Int, Long>() // Track per-key timing for premium
    private val freeKeyFailureCount = mutableMapOf<Int, Int>() // Track key health for free
    private val premiumKeyFailureCount = mutableMapOf<Int, Int>() // Track key health for premium
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    private const val MIN_REQUEST_INTERVAL = 1000L // 1 second between requests (saves quota)
    private const val MAX_RETRY_ATTEMPTS = 1 // Only 1 retry per key (saves quota)
    private const val FAST_RETRY_DELAY_MS = 1200L // 1.2 second delay (saves quota)
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
    
    suspend fun transformText(inputText: String, instruction: String, context: Context? = null): Result<String> = withContext(Dispatchers.IO) {
        // Check network connectivity if context is provided
        context?.let {
            if (!NetworkUtils.isNetworkAvailable(it)) {
                return@withContext Result.failure(IOException("No Internet Connection"))
            }
        }
        
        // Get appropriate API keys based on user subscription status
        val apiKeys = getApiKeysForUser()
        val userType = if (isProUser()) "Premium" else "Free"
        
        // Validate API keys
        if (apiKeys.isEmpty()) {
            return@withContext Result.failure(Exception("🔑 $userType API key not configured. Please check settings."))
        }
        
        // Strategy 1: Try primary key with fast timeout
        val currentIndex = getCurrentApiKeyIndex()
        val primaryResult = makeApiRequestWithFastTimeout(inputText, instruction, currentIndex, apiKeys)
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
                    // Strategy 2: Quick retry with primary key (only for temporary issues)
                    delay(FAST_RETRY_DELAY_MS)
                    val retryResult = makeApiRequestWithFastTimeout(inputText, instruction, currentIndex, apiKeys)
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
        
        // Strategy 3: Smart sequential fallback (try up to 2 keys one by one - saves quota)
        if (apiKeys.size > 1) {
            return@withContext tryLimitedSequentialFallback(inputText, instruction, apiKeys)
        }
        
        // No fallback keys available
        return@withContext Result.failure(Exception("❌ All services unavailable. Please try again later."))
    }
    
    private suspend fun tryLimitedSequentialFallback(inputText: String, instruction: String, apiKeys: List<String>): Result<String> = withContext(Dispatchers.IO) {
        val currentIndex = getCurrentApiKeyIndex()
        val availableKeys = apiKeys.indices.filter { it != currentIndex }
        if (availableKeys.isEmpty()) {
            return@withContext Result.failure(Exception("❌ No fallback keys available"))
        }
        
        // Try only the best 2 fallback keys sequentially (saves quota vs parallel)
        val bestKeys = availableKeys.sortedBy { getKeyFailureCount(it) }.take(MAX_FALLBACK_KEYS)
        
        for ((attemptIndex, keyIndex) in bestKeys.withIndex()) {
            val result = makeApiRequestWithFastTimeout(inputText, instruction, keyIndex, apiKeys)
            
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
                    // Otherwise continue to next key (sequential, not parallel)
                }
            )
        }
        
        return@withContext Result.failure(Exception("❌ Unexpected fallback error"))
    }
    
    private suspend fun trySequentialFallback(inputText: String, instruction: String, apiKeys: List<String>): Result<String> {
        // Try remaining keys with fast timeouts
        val currentIndex = getCurrentApiKeyIndex()
        val remainingKeys = apiKeys.indices.filter { it != currentIndex }
            .sortedBy { getKeyFailureCount(it) } // Try healthiest keys first
        
        for (keyIndex in remainingKeys) {
            val result = makeApiRequestWithFastTimeout(inputText, instruction, keyIndex, apiKeys)
            result.fold(
                onSuccess = { 
                    markKeyAsHealthy(keyIndex)
                    setCurrentApiKeyIndex(keyIndex) // Switch to working key
                    return result
                },
                onFailure = { markKeyAsUnhealthy(keyIndex) }
            )
            
            // Short delay between sequential attempts
            delay(300L)
        }
        
        return Result.failure(Exception("❌ All services unavailable. Please try again later."))
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
    
    private suspend fun makeApiRequestWithFastTimeout(inputText: String, instruction: String, apiKeyIndex: Int, apiKeys: List<String>): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Per-key rate limiting for better performance
            val (requestTimeMap, _, _) = getKeyMaps()
            val currentTime = System.currentTimeMillis()
            val lastRequestTime = requestTimeMap[apiKeyIndex] ?: 0L
            val timeSinceLastRequest = currentTime - lastRequestTime
            if (timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
                delay(MIN_REQUEST_INTERVAL - timeSinceLastRequest)
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
                connectTimeout = 8000 // Reduced from 30s to 8s for faster response
                readTimeout = 12000 // Reduced from 30s to 12s for faster response
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
                Result.failure(IOException("⏳ Too many requests. Trying alternative service..."))
            } else if (responseCode == 503) {
                Result.failure(IOException("🔧 Service temporarily unavailable. Trying alternative..."))
            } else if (responseCode == 500 || responseCode == 502 || responseCode == 504) {
                Result.failure(IOException("⚠️ Service experiencing issues. Trying alternative..."))
            } else if (responseCode == 403 || responseCode == 401) {
                Result.failure(IOException("🔐 Access denied. Switching to backup service..."))
            } else {
                Result.failure(IOException("❌ Service error. Trying alternative..."))
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("🕰️ Request timeout. Trying faster alternative..."))
        } catch (e: Exception) {
            Result.failure(IOException("❌ Connection failed. Trying alternative service..."))
        }
    }
    
    private suspend fun makeApiRequest(inputText: String, instruction: String, apiKeyIndex: Int, apiKeys: List<String>): Result<String> = withContext(Dispatchers.IO) {
        // Legacy method for backwards compatibility
        return@withContext makeApiRequestWithFastTimeout(inputText, instruction, apiKeyIndex, apiKeys)
    }
    
    /**
     * Get information about the current API key configuration
     */
    fun getApiKeyInfo(): String {
        val apiKeys = getApiKeysForUser()
        val userType = if (isProUser()) "Premium" else "Free"
        val healthyKeys = apiKeys.indices.count { getKeyFailureCount(it) == 0 }
        return when {
            apiKeys.isEmpty() -> "No $userType API keys configured"
            apiKeys.size == 1 -> "1 $userType API key configured (no fallbacks)"
            else -> "${apiKeys.size} $userType API keys configured (1 primary + ${apiKeys.size - 1} fallbacks) - $healthyKeys healthy"
        }
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
                appendLine("✅ RESPONSE RULES:")
                appendLine("• Provide only the final result - no explanations unless asked")
                appendLine("• Be natural and contextually appropriate")
                appendLine("• Use the personal context intelligently")
                appendLine("• All personal references (my coach, my boss, etc.) refer to the USER'S relationships")
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
