/*
 * Copyright (C) 2025 SendRight 4.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.app.settings.aiworkspace

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.florisboard.lib.kotlin.tryOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Manager for AI Workspace actions - handles storage, retrieval, and management of AI actions
 */
class AIWorkspaceManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: AIWorkspaceManager? = null
        private const val AI_ACTIONS_FILE = "ai_workspace_actions.json"
        
        fun getInstance(context: Context): AIWorkspaceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AIWorkspaceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val _enabledPopularActions = mutableStateListOf<AIAction>()
    private val _customActions = mutableStateListOf<AIAction>()
    
    val enabledPopularActions: SnapshotStateList<AIAction> = _enabledPopularActions
    val customActions: SnapshotStateList<AIAction> = _customActions
    
    // Add a version counter to force refresh
    private var _refreshCounter = mutableStateOf(0)
    val refreshCounter = _refreshCounter
    
    private fun triggerRefresh() {
        _refreshCounter.value = _refreshCounter.value + 1
    }
    
    /**
     * Get all enabled AI actions (both popular and custom)
     */
    fun getAllEnabledActions(): List<AIAction> {
        return _enabledPopularActions + _customActions.filter { it.isEnabled }
    }
    
    /**
     * Get enabled custom actions
     */
    fun getEnabledCustomActions(): List<AIAction> {
        return _customActions.filter { it.isEnabled }
    }
    
    /**
     * Get disabled custom actions
     */
    fun getDisabledCustomActions(): List<AIAction> {
        return _customActions.filter { !it.isEnabled }
    }
    
    /**
     * Get available popular actions that are not yet enabled
     */
    fun getAvailablePopularActions(): List<AIAction> {
        val enabledIds = _enabledPopularActions.map { it.id }.toSet()
        return PopularAIActions.actions.filter { it.id !in enabledIds }
    }
    
    /**
     * Add a popular action to enabled list
     */
    suspend fun addPopularAction(action: AIAction) {
        if (!_enabledPopularActions.any { it.id == action.id }) {
            _enabledPopularActions.add(action)
            saveActions()
        }
    }
    
    /**
     * Remove a popular action from enabled list
     */
    suspend fun removePopularAction(actionId: String) {
        _enabledPopularActions.removeAll { it.id == actionId }
        saveActions()
    }
    
    /**
     * Create a new custom action
     */
    suspend fun createCustomAction(
        title: String, 
        description: String, 
        prompt: String,
        includePersonalDetails: Boolean = false,
        includeDateTime: Boolean = false
    ): AIAction {
        val action = AIAction(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            prompt = prompt,
            iconName = "auto_awesome",
            isPopular = false,
            isUserCreated = true,
            isEnabled = true,
            includePersonalDetails = includePersonalDetails,
            includeDateTime = includeDateTime
        )
        withContext(Dispatchers.Main) {
            _customActions.add(action)
            triggerRefresh()
        }
        saveActions()
        return action
    }
    
    /**
     * Update an existing custom action
     */
    suspend fun updateCustomAction(
        actionId: String, 
        title: String, 
        description: String, 
        prompt: String,
        includePersonalDetails: Boolean = false,
        includeDateTime: Boolean = false
    ) {
        android.util.Log.d("AIWorkspaceManager", "updateCustomAction called for ID: $actionId")
        withContext(Dispatchers.Main) {
            val index = _customActions.indexOfFirst { it.id == actionId }
            android.util.Log.d("AIWorkspaceManager", "Found action at index: $index")
            if (index != -1) {
                val existingAction = _customActions[index]
                val updatedAction = existingAction.copy(
                    title = title,
                    description = description,
                    prompt = prompt,
                    includePersonalDetails = includePersonalDetails,
                    includeDateTime = includeDateTime
                )
                _customActions.removeAt(index)
                _customActions.add(index, updatedAction)
                triggerRefresh()
                android.util.Log.d("AIWorkspaceManager", "Updated action: ${updatedAction.title}")
            }
        }
        saveActions()
    }
    
    /**
     * Toggle enable/disable status of a custom action (for Remove button)
     */
    suspend fun toggleCustomAction(actionId: String) {
        withContext(Dispatchers.Main) {
            val index = _customActions.indexOfFirst { it.id == actionId }
            if (index != -1) {
                val existingAction = _customActions[index]
                val updatedAction = existingAction.copy(isEnabled = !existingAction.isEnabled)
                _customActions.removeAt(index)
                _customActions.add(index, updatedAction)
                triggerRefresh()
            }
        }
        saveActions()
    }
    
    /**
     * Permanently delete a custom action (for long press delete)
     */
    suspend fun deleteCustomAction(actionId: String) {
        android.util.Log.d("AIWorkspaceManager", "deleteCustomAction called for ID: $actionId")
        withContext(Dispatchers.Main) {
            val initialSize = _customActions.size
            _customActions.removeAll { it.id == actionId }
            android.util.Log.d("AIWorkspaceManager", "Actions size: $initialSize -> ${_customActions.size}")
            triggerRefresh()
        }
        saveActions()
    }
    
    /**
     * Remove a custom action (kept for backward compatibility - now toggles instead)
     */
    suspend fun removeCustomAction(actionId: String) {
        toggleCustomAction(actionId)
    }
    
    /**
     * Load actions from storage
     */
    suspend fun loadActions() {
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, AI_ACTIONS_FILE)
            if (file.exists()) {
                tryOrNull {
                    val jsonContent = file.readText()
                    val savedData = json.decodeFromString<SavedAIActions>(jsonContent)
                    
                    withContext(Dispatchers.Main) {
                        _enabledPopularActions.clear()
                        _enabledPopularActions.addAll(savedData.enabledPopularActions)
                        
                        _customActions.clear()
                        _customActions.addAll(savedData.customActions)
                    }
                }
            } else {
                // Load default popular actions on first run - include GenZ in top 4
                withContext(Dispatchers.Main) {
                    // Get actions in order: Humanise, Reply, Continue Writing, GenZ (instead of Facebook)
                    val defaultActionIds = listOf("humanise", "reply", "continue_writing", "genz_translate")
                    val defaultActions = defaultActionIds.mapNotNull { id ->
                        PopularAIActions.actions.find { it.id == id }
                    }
                    _enabledPopularActions.addAll(defaultActions)
                }
                saveActions()
            }
        }
    }
    
    /**
     * Save actions to storage
     */
    private suspend fun saveActions() {
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, AI_ACTIONS_FILE)
                val savedData = SavedAIActions(
                    enabledPopularActions = _enabledPopularActions.toList(),
                    customActions = _customActions.toList()
                )
                
                val jsonString = json.encodeToString(savedData)
                file.writeText(jsonString)
                android.util.Log.d("AIWorkspaceManager", "Saved ${_customActions.size} custom actions to storage")
            } catch (e: Exception) {
                android.util.Log.e("AIWorkspaceManager", "Failed to save actions", e)
            }
        }
    }
    
    /**
     * Find an action by ID in both popular and custom actions
     */
    fun findActionById(actionId: String): AIAction? {
        return _enabledPopularActions.find { it.id == actionId } 
            ?: _customActions.find { it.id == actionId }
    }
}

/**
 * Data class for saving/loading AI actions
 */
@kotlinx.serialization.Serializable
private data class SavedAIActions(
    val enabledPopularActions: List<AIAction>,
    val customActions: List<AIAction>
)