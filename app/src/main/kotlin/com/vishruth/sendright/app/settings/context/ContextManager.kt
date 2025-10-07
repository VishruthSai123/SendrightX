/*
 * Copyright (C) 2025 SendRight 4.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.app.settings.context

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Manager for handling context configuration including personal details and custom variables
 */
class ContextManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: ContextManager? = null
        const val MAX_CUSTOM_VARIABLES = 5
        
        fun getInstance(context: Context): ContextManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ContextManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val contextFile = File(context.filesDir, "context_configuration.json")
    
    // Reactive state for UI
    private var _personalDetails = mutableStateOf(PersonalDetails())
    val personalDetails = _personalDetails
    
    private val _customVariables: SnapshotStateList<CustomVariable> = emptyList<CustomVariable>().toMutableStateList()
    val customVariables: SnapshotStateList<CustomVariable> = _customVariables
    
    private var _isContextActionEnabled = mutableStateOf(false)
    val isContextActionEnabled = _isContextActionEnabled
    
    // Refresh counter for triggering UI updates
    private var _refreshCounter = mutableStateOf(0)
    val refreshCounter = _refreshCounter
    
    private fun triggerRefresh() {
        _refreshCounter.value = _refreshCounter.value + 1
    }
    
    /**
     * Load context configuration from storage
     */
    suspend fun loadConfiguration() {
        android.util.Log.d("ContextManager", "loadConfiguration called")
        withContext(Dispatchers.IO) {
            try {
                if (contextFile.exists()) {
                    val jsonContent = contextFile.readText()
                    val config = Json.decodeFromString<ContextConfiguration>(jsonContent)
                    
                    withContext(Dispatchers.Main) {
                        _personalDetails.value = config.personalDetails
                        _customVariables.clear()
                        _customVariables.addAll(config.customVariables)
                        _isContextActionEnabled.value = config.isContextActionEnabled
                        triggerRefresh()
                    }
                    android.util.Log.d("ContextManager", "Configuration loaded successfully")
                } else {
                    android.util.Log.d("ContextManager", "No existing configuration file found")
                }
            } catch (e: Exception) {
                android.util.Log.e("ContextManager", "Error loading configuration", e)
            }
        }
    }
    
    /**
     * Save context configuration to storage
     */
    suspend fun saveConfiguration() {
        android.util.Log.d("ContextManager", "saveConfiguration called")
        withContext(Dispatchers.IO) {
            try {
                // Check if this is the first time context is being configured
                val wasConfiguredPreviously = if (contextFile.exists()) {
                    try {
                        val jsonContent = contextFile.readText()
                        val existingConfig = Json.decodeFromString<ContextConfiguration>(jsonContent)
                        existingConfig.personalDetails.name.isNotBlank() || 
                        existingConfig.personalDetails.status.isNotBlank() || 
                        existingConfig.personalDetails.email.isNotBlank() || 
                        existingConfig.customVariables.isNotEmpty()
                    } catch (e: Exception) {
                        false
                    }
                } else {
                    false
                }
                
                // Auto-enable context action when configuring for the first time
                if (!wasConfiguredPreviously && isContextConfigured()) {
                    withContext(Dispatchers.Main) {
                        _isContextActionEnabled.value = true
                    }
                    android.util.Log.d("ContextManager", "Auto-enabled context action for first-time configuration")
                }
                
                val config = ContextConfiguration(
                    personalDetails = _personalDetails.value,
                    customVariables = _customVariables.toList(),
                    isContextActionEnabled = _isContextActionEnabled.value
                )
                val jsonContent = Json.encodeToString(config)
                contextFile.writeText(jsonContent)
                android.util.Log.d("ContextManager", "Configuration saved successfully")
            } catch (e: Exception) {
                android.util.Log.e("ContextManager", "Error saving configuration", e)
            }
        }
    }
    
    /**
     * Update personal details
     */
    suspend fun updatePersonalDetails(details: PersonalDetails) {
        withContext(Dispatchers.Main) {
            _personalDetails.value = details
            triggerRefresh()
        }
        saveConfiguration()
        android.util.Log.d("ContextManager", "Personal details updated: ${details.name}")
    }
    
    /**
     * Add a new custom variable (max 5 variables allowed)
     */
    suspend fun addCustomVariable(contextName: String, description: String): CustomVariable? {
        // Check if we've reached the maximum limit
        if (_customVariables.size >= MAX_CUSTOM_VARIABLES) {
            android.util.Log.w("ContextManager", "Cannot add variable: maximum limit of $MAX_CUSTOM_VARIABLES reached")
            return null
        }
        
        val variable = CustomVariable(
            id = UUID.randomUUID().toString(),
            contextName = contextName,
            description = description
        )
        
        withContext(Dispatchers.Main) {
            _customVariables.add(variable)
            triggerRefresh()
        }
        saveConfiguration()
        android.util.Log.d("ContextManager", "Custom variable added: $contextName")
        return variable
    }
    
    /**
     * Update an existing custom variable
     */
    suspend fun updateCustomVariable(variableId: String, contextName: String, description: String) {
        withContext(Dispatchers.Main) {
            val index = _customVariables.indexOfFirst { it.id == variableId }
            if (index != -1) {
                val updatedVariable = _customVariables[index].copy(
                    contextName = contextName,
                    description = description
                )
                _customVariables.removeAt(index)
                _customVariables.add(index, updatedVariable)
                triggerRefresh()
                android.util.Log.d("ContextManager", "Custom variable updated: $contextName")
            }
        }
        saveConfiguration()
    }
    
    /**
     * Delete a custom variable
     */
    suspend fun deleteCustomVariable(variableId: String) {
        withContext(Dispatchers.Main) {
            val variable = _customVariables.find { it.id == variableId }
            if (_customVariables.removeIf { it.id == variableId }) {
                triggerRefresh()
                android.util.Log.d("ContextManager", "Custom variable deleted: ${variable?.contextName}")
            }
        }
        saveConfiguration()
    }
    
    /**
     * Enable or disable the context action
     */
    suspend fun setContextActionEnabled(enabled: Boolean) {
        withContext(Dispatchers.Main) {
            _isContextActionEnabled.value = enabled
            triggerRefresh()
        }
        saveConfiguration()
        android.util.Log.d("ContextManager", "Context action enabled: $enabled")
    }
    
    /**
     * Generate the context instruction to be injected into AI prompts
     */
    fun generateContextInstruction(): String {
        val details = _personalDetails.value
        val variables = _customVariables.toList()
        
        val instruction = buildString {
            appendLine("🧠 CONTEXT INTELLIGENCE SYSTEM:")
            appendLine("You are an intelligent AI assistant with deep understanding of the user's personal context and relationships.")
            appendLine()
            
            // Add current date and time context
            val currentDateTime = LocalDateTime.now()
            val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
            appendLine("📅 CURRENT CONTEXT:")
            appendLine("• Today is: ${currentDateTime.format(dateFormatter)}")
            appendLine("• Current time: ${currentDateTime.format(timeFormatter)}")
            appendLine()
            
            appendLine("👤 USER PROFILE:")
            if (details.name.isNotBlank()) {
                appendLine("• User's name: ${details.name}")
            }
            if (details.status.isNotBlank()) {
                appendLine("• Current status/role: ${details.status}")
            }
            if (details.age != null && details.age > 0) {
                appendLine("• Age: ${details.age} years")
            }
            appendLine("• Preferred communication language: ${details.preferredLanguage}")
            appendLine("• Preferred communication style: ${details.typingStyle}")
            if (details.email.isNotBlank()) {
                appendLine("• Email: ${details.email}")
            }
            
            if (variables.isNotEmpty()) {
                appendLine()
                appendLine("🔍 RELATIONSHIP & CONTEXT DATABASE:")
                variables.forEach { variable ->
                    appendLine("• ${variable.contextName}: ${variable.description}")
                }
            }
            
            appendLine()
            appendLine("🎯 INTELLIGENT RESPONSE GUIDELINES:")
            appendLine("1. ANALYZE the user's request to understand WHO they're referring to and WHAT they want to accomplish")
            appendLine("2. MATCH entities in the request with your context database (people, relationships, situations)")
            appendLine("3. APPLY appropriate cultural norms, relationship dynamics, and communication styles")
            appendLine("4. GENERATE responses that show deep understanding of the context, not just translation")
            appendLine("5. USE natural, culturally appropriate language that reflects the relationship dynamics")
            
            if (variables.any { it.description.contains("telugu", ignoreCase = true) || it.description.contains("teacher", ignoreCase = true) }) {
                appendLine()
                appendLine("📚 CULTURAL INTELLIGENCE:")
                appendLine("• For Telugu contexts: Use respectful honorifics (garu, sir), appropriate greetings")
                appendLine("• For teacher relationships: Show proper respect, use formal address")
                appendLine("• For festival wishes: Use traditional, warm, and culturally authentic expressions")
            }
            
            appendLine()
            appendLine("✨ Your task is to understand the context deeply and respond intelligently, not just translate or follow basic instructions.")
            appendLine("Now process this user request with full contextual understanding:")
        }
        
        return instruction.trim()
    }
    
    /**
     * Generate enhanced context instruction with intelligent text analysis
     */
    fun generateIntelligentContextInstruction(userText: String): String {
        val details = _personalDetails.value
        val variables = _customVariables.toList()
        
        // Analyze user text for context clues
        val relevantContexts = findRelevantContexts(userText, variables)
        
        val instruction = buildString {
            appendLine("🧠 ADVANCED CONTEXT INTELLIGENCE SYSTEM:")
            appendLine("You are an intelligent AI assistant with deep contextual understanding and cultural awareness.")
            appendLine()
            
            // Add current date and time context
            val currentDateTime = LocalDateTime.now()
            val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
            appendLine("📅 CURRENT CONTEXT:")
            appendLine("• Today is: ${currentDateTime.format(dateFormatter)}")
            appendLine("• Current time: ${currentDateTime.format(timeFormatter)}")
            appendLine()
            
            appendLine("👤 USER PROFILE:")
            if (details.name.isNotBlank()) {
                appendLine("• User's name: ${details.name}")
            }
            if (details.status.isNotBlank()) {
                appendLine("• Current status/role: ${details.status}")
            }
            if (details.age != null && details.age > 0) {
                appendLine("• Age: ${details.age} years")
            }
            appendLine("• Preferred communication language: ${details.preferredLanguage}")
            appendLine("• Preferred communication style: ${details.typingStyle}")
            if (details.email.isNotBlank()) {
                appendLine("• Email: ${details.email}")
            }
            
            if (variables.isNotEmpty()) {
                appendLine()
                appendLine("🔍 COMPLETE RELATIONSHIP & CONTEXT DATABASE:")
                variables.forEach { variable ->
                    val isRelevant = relevantContexts.contains(variable)
                    val marker = if (isRelevant) "🎯 [HIGHLY RELEVANT]" else "📋"
                    appendLine("$marker ${variable.contextName}: ${variable.description}")
                }
            }
            
            if (relevantContexts.isNotEmpty()) {
                appendLine()
                appendLine("🎯 DETECTED RELEVANT CONTEXTS FOR THIS REQUEST:")
                relevantContexts.forEach { context ->
                    appendLine("• ${context.contextName}: ${context.description}")
                }
            }
            
            appendLine()
            appendLine("🎯 INTELLIGENT PROCESSING GUIDELINES:")
            appendLine("1. ANALYZE the user's text for names, relationships, cultural references, and context clues")
            appendLine("2. MATCH detected entities with your relationship database above")
            appendLine("3. UNDERSTAND the intent: What does the user really want to accomplish?")
            appendLine("4. APPLY cultural intelligence and relationship dynamics")
            appendLine("5. GENERATE responses that demonstrate deep contextual understanding")
            appendLine("6. BE conversational and natural, not robotic or translation-like")
            
            if (relevantContexts.any { it.description.contains("telugu", ignoreCase = true) || it.description.contains("teacher", ignoreCase = true) }) {
                appendLine()
                appendLine("📚 CULTURAL & LINGUISTIC INTELLIGENCE:")
                appendLine("• Telugu context detected: Use appropriate honorifics, cultural expressions")
                appendLine("• Teacher relationship: Show proper respect and formal address")
                appendLine("• Festival/celebration: Use warm, traditional, culturally authentic language")
                appendLine("• Avoid literal translations - be naturally conversational in the target language")
            }
            
            appendLine()
            appendLine("✨ CRITICAL: Understand the context deeply, analyze relationships, and respond with intelligence and cultural awareness.")
            appendLine("User's request to process:")
        }
        
        return instruction.trim()
    }
    
    /**
     * Find contexts relevant to the user's text input using intelligent matching
     */
    private fun findRelevantContexts(userText: String, variables: List<CustomVariable>): List<CustomVariable> {
        val lowercaseText = userText.lowercase()
        val relevantContexts = mutableListOf<CustomVariable>()
        
        variables.forEach { variable ->
            val contextNameLower = variable.contextName.lowercase()
            val descriptionLower = variable.description.lowercase()
            val relevanceScore = calculateRelevanceScore(lowercaseText, variable)
            
            // Add context if it has any relevance
            if (relevanceScore > 0) {
                relevantContexts.add(variable)
            }
        }
        
        // Sort by relevance score (highest first) and return
        return relevantContexts.sortedByDescending { calculateRelevanceScore(lowercaseText, it) }
    }
    
    /**
     * Calculate relevance score for a context variable based on user text
     */
    private fun calculateRelevanceScore(userText: String, variable: CustomVariable): Int {
        val contextNameLower = variable.contextName.lowercase()
        val descriptionLower = variable.description.lowercase()
        var score = 0
        
        // Direct name match (highest priority)
        if (userText.contains(contextNameLower)) {
            score += 100
        }
        
        // Individual words from context name
        contextNameLower.split(" ").forEach { word ->
            if (word.length > 2 && userText.contains(word)) {
                score += 50
            }
        }
        
        // Language indicators
        val languageIndicators = mapOf(
            "telugu" to listOf("telugu", "తెలుగు", "telugu script", "telugu language"),
            "hindi" to listOf("hindi", "हिंदी", "hindi script", "hindi language"),
            "english" to listOf("english", "english language")
        )
        
        languageIndicators.forEach { (lang, indicators) ->
            if (indicators.any { userText.contains(it) } && descriptionLower.contains(lang)) {
                score += 80
            }
        }
        
        // Relationship indicators
        val relationshipKeywords = mapOf(
            "teacher" to listOf("teacher", "sir", "garu", "guru", "mentor", "professor"),
            "friend" to listOf("friend", "buddy", "pal", "mate"),
            "family" to listOf("family", "mother", "father", "brother", "sister", "parent"),
            "colleague" to listOf("colleague", "coworker", "teammate", "office"),
            "professional" to listOf("professional", "formal", "business", "work", "office")
        )
        
        relationshipKeywords.forEach { (category, keywords) ->
            if (keywords.any { userText.contains(it) } && descriptionLower.contains(category)) {
                score += 60
            }
        }
        
        // Festival and occasion keywords
        val occasionKeywords = listOf(
            "dussera", "dussehra", "festival", "celebration", "holiday", "birthday", 
            "anniversary", "new year", "diwali", "holi", "eid", "christmas"
        )
        
        occasionKeywords.forEach { occasion ->
            if (userText.contains(occasion) && (descriptionLower.contains("festival") || 
                descriptionLower.contains("celebration") || descriptionLower.contains(occasion))) {
                score += 70
            }
        }
        
        // Communication style indicators
        val styleKeywords = mapOf(
            "polite" to listOf("polite", "respectful", "formal", "courteous"),
            "casual" to listOf("casual", "informal", "friendly", "relaxed"),
            "professional" to listOf("professional", "formal", "business")
        )
        
        styleKeywords.forEach { (style, keywords) ->
            if (keywords.any { userText.contains(it) } && descriptionLower.contains(style)) {
                score += 40
            }
        }
        
        return score
    }
    
    /**
     * Check if we can add more custom variables
     */
    fun canAddMoreVariables(): Boolean {
        return _customVariables.size < MAX_CUSTOM_VARIABLES
    }
    
    /**
     * Get remaining variable slots
     */
    fun getRemainingVariableSlots(): Int {
        return MAX_CUSTOM_VARIABLES - _customVariables.size
    }
    
    /**
     * Check if context is configured (has any meaningful data)
     */
    fun isContextConfigured(): Boolean {
        val details = _personalDetails.value
        return details.name.isNotBlank() || 
               details.status.isNotBlank() || 
               details.email.isNotBlank() || 
               _customVariables.isNotEmpty()
    }
    

}