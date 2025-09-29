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
    private val API_KEYS = listOf(
        BuildConfig.GEMINI_API_KEY,
        BuildConfig.GEMINI_API_KEY_FALLBACK_1,
        BuildConfig.GEMINI_API_KEY_FALLBACK_2,
        BuildConfig.GEMINI_API_KEY_FALLBACK_3
    ).filter { it.isNotBlank() } // Only use non-empty API keys
    
    private var currentApiKeyIndex = 0
    private val keyLastRequestTime = mutableMapOf<Int, Long>() // Track per-key timing
    private val keyFailureCount = mutableMapOf<Int, Int>() // Track key health
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    private const val MIN_REQUEST_INTERVAL = 500L // Reduced to 500ms for faster responses
    private const val MAX_RETRY_ATTEMPTS = 2 // Reduced from 3 to 2 for speed
    private const val FAST_RETRY_DELAY_MS = 800L // Reduced from 2000ms to 800ms
    private const val PARALLEL_TIMEOUT_MS = 3000L // Quick timeout for parallel attempts
    
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
    
    suspend fun transformText(inputText: String, instruction: String, context: Context? = null): Result<String> = withContext(Dispatchers.IO) {
        // Check network connectivity if context is provided
        context?.let {
            if (!NetworkUtils.isNetworkAvailable(it)) {
                return@withContext Result.failure(IOException("No Internet Connection"))
            }
        }
        
        // Validate API keys
        if (API_KEYS.isEmpty()) {
            return@withContext Result.failure(Exception("🔑 API key not configured. Please check settings."))
        }
        
        // Strategy 1: Try primary key with fast timeout
        val primaryResult = makeApiRequestWithFastTimeout(inputText, instruction, currentApiKeyIndex)
        primaryResult.fold(
            onSuccess = { 
                markKeyAsHealthy(currentApiKeyIndex)
                return@withContext primaryResult 
            },
            onFailure = { error ->
                markKeyAsUnhealthy(currentApiKeyIndex)
                
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
                    val retryResult = makeApiRequestWithFastTimeout(inputText, instruction, currentApiKeyIndex)
                    retryResult.fold(
                        onSuccess = { 
                            markKeyAsHealthy(currentApiKeyIndex)
                            return@withContext retryResult 
                        },
                        onFailure = { /* Continue to fallback strategy */ }
                    )
                }
            }
        )
        
        // Strategy 3: Fast parallel fallback (try multiple keys simultaneously)
        if (API_KEYS.size > 1) {
            return@withContext tryParallelFallback(inputText, instruction)
        }
        
        // Strategy 4: Sequential fallback with fast timeouts (if parallel fails)
        return@withContext trySequentialFallback(inputText, instruction)
    }
    
    private suspend fun tryParallelFallback(inputText: String, instruction: String): Result<String> = withContext(Dispatchers.IO) {
        val availableKeys = API_KEYS.indices.filter { it != currentApiKeyIndex }
        if (availableKeys.isEmpty()) {
            return@withContext Result.failure(Exception("❌ No fallback keys available"))
        }
        
        // Launch parallel requests with the best 2 fallback keys
        val keyIndices = availableKeys.sortedBy { getKeyFailureCount(it) }.take(2)
        
        try {
            kotlinx.coroutines.withTimeout(PARALLEL_TIMEOUT_MS) {
                coroutineScope {
                    val jobs = keyIndices.map { keyIndex ->
                        async {
                            makeApiRequestWithFastTimeout(inputText, instruction, keyIndex)
                        }
                    }
                    
                    // Wait for first successful result
                    for (job in jobs) {
                        try {
                            val result = job.await()
                            if (result.isSuccess) {
                                jobs.forEach { if (it != job) it.cancel() }
                                return@coroutineScope result
                            }
                        } catch (e: Exception) {
                            continue
                        }
                    }
                    
                    Result.failure<String>(Exception("❌ Parallel fallback failed"))
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("🕰️ All services are responding slowly. Please try again."))
        }
    }
    
    private suspend fun trySequentialFallback(inputText: String, instruction: String): Result<String> {
        // Try remaining keys with fast timeouts
        val remainingKeys = API_KEYS.indices.filter { it != currentApiKeyIndex }
            .sortedBy { getKeyFailureCount(it) } // Try healthiest keys first
        
        for (keyIndex in remainingKeys) {
            val result = makeApiRequestWithFastTimeout(inputText, instruction, keyIndex)
            result.fold(
                onSuccess = { 
                    markKeyAsHealthy(keyIndex)
                    currentApiKeyIndex = keyIndex // Switch to working key
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
        keyFailureCount[keyIndex] = 0
    }
    
    private fun markKeyAsUnhealthy(keyIndex: Int) {
        keyFailureCount[keyIndex] = (keyFailureCount[keyIndex] ?: 0) + 1
    }
    
    private fun getKeyFailureCount(keyIndex: Int): Int {
        return keyFailureCount[keyIndex] ?: 0
    }
    
    private suspend fun makeApiRequestWithFastTimeout(inputText: String, instruction: String, apiKeyIndex: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Per-key rate limiting for better performance
            val currentTime = System.currentTimeMillis()
            val lastRequestTime = keyLastRequestTime[apiKeyIndex] ?: 0L
            val timeSinceLastRequest = currentTime - lastRequestTime
            if (timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
                delay(MIN_REQUEST_INTERVAL - timeSinceLastRequest)
            }
            keyLastRequestTime[apiKeyIndex] = System.currentTimeMillis()
            
            val prompt = buildPrompt(inputText, instruction)
            val requestBody = GeminiRequest(
                contents = listOf(
                    Content(
                        parts = listOf(Part(text = prompt))
                    )
                )
            )
            
            val url = URL("$ENDPOINT?key=${API_KEYS[apiKeyIndex]}")
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
    
    private suspend fun makeApiRequest(inputText: String, instruction: String, apiKeyIndex: Int): Result<String> = withContext(Dispatchers.IO) {
        // Legacy method for backwards compatibility
        return@withContext makeApiRequestWithFastTimeout(inputText, instruction, apiKeyIndex)
    }
    
    /**
     * Get information about the current API key configuration
     */
    fun getApiKeyInfo(): String {
        val healthyKeys = API_KEYS.indices.count { getKeyFailureCount(it) == 0 }
        return when {
            API_KEYS.isEmpty() -> "No API keys configured"
            API_KEYS.size == 1 -> "1 API key configured (no fallbacks)"
            else -> "${API_KEYS.size} API keys configured (1 primary + ${API_KEYS.size - 1} fallbacks) - $healthyKeys healthy"
        }
    }
    
    /**
     * Reset to primary API key (for testing purposes)
     */
    fun resetToPrimaryKey() {
        currentApiKeyIndex = 0
        keyFailureCount.clear()
        keyLastRequestTime.clear()
    }
    
    /**
     * Get the healthiest API key index (lowest failure count)
     */
    fun getHealthiestKeyIndex(): Int {
        return API_KEYS.indices.minByOrNull { getKeyFailureCount(it) } ?: 0
    }
    
    /**
     * Switch to the healthiest API key
     */
    fun switchToHealthiestKey() {
        currentApiKeyIndex = getHealthiestKeyIndex()
    }
    
    private fun buildPrompt(inputText: String, instruction: String): String {
        // Special handling for chat functionality - treat as direct conversation
        if (instruction == MagicWandInstructions.CHAT) {
            return """
                $instruction
                
                User message: "$inputText"
                
                Respond naturally and directly to the user's input without any prefixes or extra text. Provide helpful, concise responses.
            """.trimIndent()
        }
        
        // Standard prompt for text transformation features
        return """
            $instruction
            
            Text to transform: "$inputText"
            
            Important: Provide ONLY the transformed text as your response. No explanations, no prefixes like "Here is the answer", no suffixes like "Would you like me to do...", just the direct transformed text result.
        """.trimIndent()
    }
}
