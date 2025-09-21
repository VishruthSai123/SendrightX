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
    private var lastRequestTime = 0L
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    private const val MIN_REQUEST_INTERVAL = 1000L // 1 second between requests
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 2000L // 2 seconds between retries
    
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
        
        // Try all available API keys with retry logic
        repeat(API_KEYS.size) { keyAttempt ->
            repeat(MAX_RETRY_ATTEMPTS) { retryAttempt ->
                val result = makeApiRequest(inputText, instruction, currentApiKeyIndex)
                
                result.fold(
                    onSuccess = { return@withContext result },
                    onFailure = { error ->
                        val shouldRetryWithSameKey = when {
                            error is IOException && (error.message?.contains("503") == true || 
                                                    error.message?.contains("Service temporarily unavailable") == true ||
                                                    error.message?.contains("500") == true ||
                                                    error.message?.contains("502") == true ||
                                                    error.message?.contains("504") == true ||
                                                    error.message?.contains("timeout") == true) -> true
                            else -> false
                        }
                        
                        val shouldTryNextKey = when {
                            error is IOException && (error.message?.contains("403") == true ||
                                                    error.message?.contains("401") == true ||
                                                    error.message?.contains("API key") == true) -> true
                            else -> false
                        }
                        
                        when {
                            shouldRetryWithSameKey && retryAttempt < MAX_RETRY_ATTEMPTS - 1 -> {
                                delay(RETRY_DELAY_MS * (retryAttempt + 1)) // Exponential backoff
                            }
                            shouldTryNextKey || retryAttempt == MAX_RETRY_ATTEMPTS - 1 -> {
                                // Move to next API key
                                currentApiKeyIndex = (currentApiKeyIndex + 1) % API_KEYS.size
                                return@repeat // Break out of retry loop to try next key
                            }
                            else -> {
                                return@withContext result // Return the error if can't retry
                            }
                        }
                    }
                )
            }
        }
        
        // All API keys exhausted
        Result.failure(Exception("❌ All services unavailable. Please try again later."))
    }
    
    private suspend fun makeApiRequest(inputText: String, instruction: String, apiKeyIndex: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Rate limiting: ensure minimum interval between requests
            val currentTime = System.currentTimeMillis()
            val timeSinceLastRequest = currentTime - lastRequestTime
            if (timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
                delay(MIN_REQUEST_INTERVAL - timeSinceLastRequest)
            }
            lastRequestTime = System.currentTimeMillis()
            
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
                connectTimeout = 30000 // 30 seconds
                readTimeout = 30000 // 30 seconds
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
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Result.failure(IOException("⏳ Too many requests. Please wait a moment and try again."))
            } else if (responseCode == 503) {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Result.failure(IOException("🔧 Service temporarily unavailable. Please try again later."))
            } else if (responseCode == 500 || responseCode == 502 || responseCode == 504) {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Result.failure(IOException("⚠️ Service experiencing issues. Please try again in a few moments."))
            } else if (responseCode == 403) {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Result.failure(IOException("🔐 Access denied. Please check your configuration."))
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Result.failure(IOException("❌ Service error. Please try again later."))
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("🕰️ Request timeout. Please check your connection and try again."))
        } catch (e: Exception) {
            Result.failure(IOException("❌ Something went wrong. Please try again."))
        }
    }
    
    /**
     * Get information about the current API key configuration
     */
    fun getApiKeyInfo(): String {
        return when {
            API_KEYS.isEmpty() -> "No API keys configured"
            API_KEYS.size == 1 -> "1 API key configured (no fallbacks)"
            else -> "${API_KEYS.size} API keys configured (1 primary + ${API_KEYS.size - 1} fallbacks)"
        }
    }
    
    /**
     * Reset to primary API key (for testing purposes)
     */
    fun resetToPrimaryKey() {
        currentApiKeyIndex = 0
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
