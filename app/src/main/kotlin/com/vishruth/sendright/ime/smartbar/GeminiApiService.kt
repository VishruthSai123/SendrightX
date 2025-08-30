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

import com.vishruth.key1.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object GeminiApiService {
    private val API_KEY = BuildConfig.GEMINI_API_KEY
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    private var lastRequestTime = 0L
    private const val MIN_REQUEST_INTERVAL = 1000L // 1 second between requests
    
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
    
    suspend fun transformText(inputText: String, instruction: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Validate API key
            if (API_KEY.isBlank()) {
                return@withContext Result.failure(Exception("Gemini API key not configured. Please add GEMINI_API_KEY to local.properties"))
            }
            
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
            
            val url = URL("$ENDPOINT?key=$API_KEY")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
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
                    Result.success(transformedText.trim())
                } else {
                    Result.failure(Exception("No response from Gemini API"))
                }
            } else if (responseCode == 429) {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Result.failure(IOException("Rate limit exceeded. Please wait a moment and try again."))
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Result.failure(IOException("HTTP $responseCode: $errorStream"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun buildPrompt(inputText: String, instruction: String): String {
        return """
            $instruction
            
            Text to transform: "$inputText"
            
            Important: Provide ONLY the transformed text as your response. No explanations, no prefixes like "Here is the answer", no suffixes like "Would you like me to do...", just the direct transformed text result.
        """.trimIndent()
    }
}
