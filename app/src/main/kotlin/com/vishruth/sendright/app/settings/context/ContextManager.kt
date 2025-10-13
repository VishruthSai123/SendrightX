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
     * Load context configuration from storage synchronously for immediate use
     */
    private fun loadConfigurationSync() {
        try {
            if (contextFile.exists()) {
                val jsonContent = contextFile.readText()
                val config = Json.decodeFromString<ContextConfiguration>(jsonContent)
                
                _personalDetails.value = config.personalDetails
                _customVariables.clear()
                _customVariables.addAll(config.customVariables)
                _isContextActionEnabled.value = config.isContextActionEnabled
                android.util.Log.d("ContextManager", "Configuration loaded synchronously for context generation")
            }
        } catch (e: Exception) {
            android.util.Log.e("ContextManager", "Error loading configuration synchronously", e)
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
        // Load fresh configuration to ensure we have the latest preferences
        loadConfigurationSync()
        
        val details = _personalDetails.value
        val variables = _customVariables.toList()
        
        val instruction = buildString {
            appendLine("🧠 CONTEXT INTELLIGENCE SYSTEM:")
            appendLine("You are an intelligent AI assistant with deep understanding of the user's personal context and relationships.")
            appendLine()
            
            // Add current date and time context with improved prompting
            val currentDateTime = LocalDateTime.now()
            val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
            appendLine("📅 CURRENT DATE & TIME CONTEXT:")
            appendLine("• Today's date: ${currentDateTime.format(dateFormatter)}")
            appendLine("• Present time: ${currentDateTime.format(timeFormatter)}")
            appendLine("• IMPORTANT: Use today's date when needed for greetings, scheduling, deadlines, or time-sensitive content")
            appendLine("• Include appropriate dates in letters, emails, or formal documents")
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
            appendLine("4. UTILIZE today's date intelligently - include dates in letters, emails, schedules, or time-sensitive content")
            appendLine("5. GENERATE responses that show deep understanding of the context, not just translation")
            appendLine("6. USE natural, culturally appropriate language that reflects the relationship dynamics")
            
            // Add response length preference
            val responseLengthInstruction = when (details.responseLength.lowercase()) {
                "short" -> "7. RESPONSE LENGTH: Keep responses concise and to-the-point. Provide brief, clear answers without unnecessary elaboration."
                "medium" -> "7. RESPONSE LENGTH: Provide balanced responses with adequate detail. Include necessary explanations while maintaining clarity."
                "lengthy" -> "7. RESPONSE LENGTH: Provide comprehensive, detailed responses. Include thorough explanations, examples, and additional context when helpful."
                else -> "7. RESPONSE LENGTH: Provide balanced responses with adequate detail. Include necessary explanations while maintaining clarity."
            }
            
            appendLine(responseLengthInstruction)

            appendLine()
            appendLine("📅 DATE-AWARE INTELLIGENCE:")
            appendLine("• For letters/emails: Automatically include today's date in proper format")
            appendLine("• For scheduling: Reference current date for deadlines, appointments, meetings")
            appendLine("• For greetings: Use time-appropriate greetings (Good morning/afternoon/evening)")
            appendLine("• For formal documents: Include date headers where professionally appropriate")
            
            if (variables.any { it.description.contains("telugu", ignoreCase = true) || it.description.contains("teacher", ignoreCase = true) }) {
                appendLine()
                appendLine("📚 CULTURAL INTELLIGENCE:")
                appendLine("• For Telugu contexts: Use respectful honorifics (garu, sir), appropriate greetings")
                appendLine("• For teacher relationships: Show proper respect, use formal address")
                appendLine("• For festival wishes: Use traditional, warm, and culturally authentic expressions")
            }
            
            appendLine()
            appendLine("✨ Your task is to understand the context deeply and respond intelligently, utilizing today's date when relevant.")
            appendLine("Remember: Include dates naturally in letters, formal documents, schedules, or any time-sensitive content.")
            appendLine("Now process this user request with full contextual and temporal understanding:")
        }
        
        return instruction.trim()
    }
    

    
    /**
     * Get fresh personal details by loading from storage if needed
     */
    fun getFreshPersonalDetails(): PersonalDetails {
        loadConfigurationSync()
        return _personalDetails.value
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