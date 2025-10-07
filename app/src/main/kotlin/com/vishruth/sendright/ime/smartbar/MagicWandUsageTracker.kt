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
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Data class representing section settings for manual vs auto arrangement
 */
@Serializable
data class SectionSettings(
    val isAutoArrangeEnabled: Boolean = false,
    val manualOrder: List<String> = emptyList()
)

/**
 * Data class representing usage statistics for a MagicWand section
 */
@Serializable
data class SectionUsageStats(
    val sectionTitle: String,
    val totalUsageCount: Int = 0,
    val lastUsedTimestamp: Long = 0L,
    val recentUsageCount: Int = 0 // Usage count in the last 7 days
) {
    /**
     * Calculate a combined score for ordering sections
     * Higher score = higher priority (should appear first)
     */
    fun calculatePriorityScore(): Double {
        val currentTime = System.currentTimeMillis()
        val daysSinceLastUse = (currentTime - lastUsedTimestamp) / (1000 * 60 * 60 * 24)
        
        // Recency factor: more recent usage gets higher score (decays over time)
        val recencyFactor = when {
            daysSinceLastUse == 0L -> 10.0 // Used today
            daysSinceLastUse <= 1 -> 8.0  // Used yesterday
            daysSinceLastUse <= 3 -> 6.0  // Used in last 3 days
            daysSinceLastUse <= 7 -> 4.0  // Used in last week
            else -> 1.0 // Older usage
        }
        
        // Frequency factor: total usage count contributes to score
        val frequencyFactor = totalUsageCount * 0.5
        
        // Recent usage factor: usage in last 7 days gets extra weight
        val recentFactor = recentUsageCount * 2.0
        
        return recencyFactor + frequencyFactor + recentFactor
    }
}

/**
 * Manages usage tracking and dynamic ordering of MagicWand sections
 */
class MagicWandUsageTracker private constructor(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "magic_wand_usage_prefs"
        private const val USAGE_STATS_KEY = "section_usage_stats"
        private const val SECTION_SETTINGS_KEY = "section_settings"
        private const val RECENT_USAGE_WINDOW_DAYS = 7
        
        @Volatile
        private var INSTANCE: MagicWandUsageTracker? = null
        
        fun getInstance(context: Context): MagicWandUsageTracker {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MagicWandUsageTracker(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    
    // Thread-safe map to store usage stats
    private val usageStatsMap = ConcurrentHashMap<String, SectionUsageStats>()
    
    // StateFlow to notify UI of changes
    private val _orderedSections = MutableStateFlow<List<String>>(emptyList())
    val orderedSections: StateFlow<List<String>> = _orderedSections.asStateFlow()
    
    // StateFlow for section settings
    private val _sectionSettings = MutableStateFlow(SectionSettings())
    val sectionSettings: StateFlow<SectionSettings> = _sectionSettings.asStateFlow()
    
    init {
        loadUsageStats()
        loadSectionSettings()
    }
    
    /**
     * Record that a section was used (when any button in the section is clicked)
     * Note: This only saves the usage data, reordering happens when panel is reopened
     */
    fun recordSectionUsage(sectionTitle: String) {
        val currentTime = System.currentTimeMillis()
        val currentStats = usageStatsMap[sectionTitle] ?: SectionUsageStats(sectionTitle)
        
        // Update usage stats
        val updatedStats = currentStats.copy(
            totalUsageCount = currentStats.totalUsageCount + 1,
            lastUsedTimestamp = currentTime,
            recentUsageCount = calculateRecentUsageCount(currentStats, currentTime)
        )
        
        usageStatsMap[sectionTitle] = updatedStats
        saveUsageStats()
        // Note: We don't call updateOrderedSections() here anymore
        // Reordering will happen when refreshOrderedSections() is called on panel open
    }
    
    /**
     * Get the current ordered list of sections based on settings (auto vs manual)
     */
    fun getOrderedSections(defaultSections: List<String>): List<String> {
        val currentSettings = _sectionSettings.value
        
        return if (currentSettings.isAutoArrangeEnabled) {
            // Auto arrange based on usage
            // Ensure all default sections have stats entries
            defaultSections.forEach { sectionTitle ->
                if (!usageStatsMap.containsKey(sectionTitle)) {
                    usageStatsMap[sectionTitle] = SectionUsageStats(sectionTitle)
                }
            }
            
            // Sort sections by priority score (highest first)
            defaultSections.sortedByDescending { sectionTitle ->
                usageStatsMap[sectionTitle]?.calculatePriorityScore() ?: 0.0
            }
        } else {
            // Use manual order if available, otherwise default
            if (currentSettings.manualOrder.isNotEmpty()) {
                currentSettings.manualOrder
            } else {
                defaultSections
            }
        }
    }
    
    /**
     * Calculate recent usage count within the recent usage window
     */
    private fun calculateRecentUsageCount(currentStats: SectionUsageStats, currentTime: Long): Int {
        val recentWindowMs = RECENT_USAGE_WINDOW_DAYS * 24 * 60 * 60 * 1000L
        return if (currentTime - currentStats.lastUsedTimestamp <= recentWindowMs) {
            currentStats.recentUsageCount + 1
        } else {
            1 // Reset recent count if outside window
        }
    }
    
    /**
     * Update the ordered sections StateFlow
     */
    private fun updateOrderedSections() {
        val defaultOrder = listOf(
            "AI Workspace",
            "Enhance", 
            "Tone Changer",
            "Advanced",
            "Study",
            "Others"
        )
        
        val orderedList = getOrderedSections(defaultOrder)
        _orderedSections.value = orderedList
    }
    
    /**
     * Refresh the section ordering based on current usage stats
     * Call this when the MagicWand panel is opened to trigger reordering
     */
    fun refreshOrderedSections() {
        updateOrderedSections()
    }
    
    /**
     * Load usage stats from SharedPreferences
     */
    private fun loadUsageStats() {
        try {
            val statsJson = preferences.getString(USAGE_STATS_KEY, null)
            if (statsJson != null) {
                val statsList: List<SectionUsageStats> = json.decodeFromString(statsJson)
                usageStatsMap.clear()
                statsList.forEach { stats ->
                    usageStatsMap[stats.sectionTitle] = stats
                }
            }
        } catch (e: Exception) {
            // If loading fails, start with empty stats
            usageStatsMap.clear()
        }
        // Don't automatically update sections on initialization
        // They will be updated when refreshOrderedSections() is called
    }
    
    /**
     * Save usage stats to SharedPreferences
     */
    private fun saveUsageStats() {
        try {
            val statsList = usageStatsMap.values.toList()
            val statsJson = json.encodeToString(statsList)
            preferences.edit()
                .putString(USAGE_STATS_KEY, statsJson)
                .apply()
        } catch (e: Exception) {
            // Handle save errors gracefully
        }
    }
    
    /**
     * Clear all usage statistics (for testing or reset purposes)
     */
    fun clearUsageStats() {
        usageStatsMap.clear()
        preferences.edit().remove(USAGE_STATS_KEY).apply()
        refreshOrderedSections() // Use refresh to update immediately when clearing
    }
    
    /**
     * Get usage stats for debugging purposes
     */
    fun getUsageStats(): Map<String, SectionUsageStats> {
        return usageStatsMap.toMap()
    }
    
    /**
     * Toggle auto arrange mode on/off
     * Handles edge cases: reset to original order when switching modes
     */
    fun toggleAutoArrangeMode() {
        val currentSettings = _sectionSettings.value
        val wasAutoEnabled = currentSettings.isAutoArrangeEnabled
        
        val newSettings = if (wasAutoEnabled) {
            // Switching from Auto to Manual: reset to original order
            currentSettings.copy(
                isAutoArrangeEnabled = false,
                manualOrder = emptyList() // Clear manual order to use default
            )
        } else {
            // Switching from Manual to Auto: reset to original order first, then auto-arrange
            currentSettings.copy(
                isAutoArrangeEnabled = true,
                manualOrder = emptyList() // Clear manual order
            )
        }
        
        _sectionSettings.value = newSettings
        saveSectionSettings()
        refreshOrderedSections() // Update sections immediately when toggling
    }
    
    /**
     * Update manual order of sections
     */
    fun updateManualOrder(newOrder: List<String>) {
        val currentSettings = _sectionSettings.value
        val newSettings = currentSettings.copy(manualOrder = newOrder)
        _sectionSettings.value = newSettings
        saveSectionSettings()
        
        // Update sections immediately if manual mode is active
        if (!currentSettings.isAutoArrangeEnabled) {
            refreshOrderedSections()
        }
    }
    
    /**
     * Load section settings from SharedPreferences
     */
    private fun loadSectionSettings() {
        try {
            val settingsJson = preferences.getString(SECTION_SETTINGS_KEY, null)
            if (settingsJson != null) {
                val settings: SectionSettings = json.decodeFromString(settingsJson)
                _sectionSettings.value = settings
            }
        } catch (e: Exception) {
            // If loading fails, use default settings
            _sectionSettings.value = SectionSettings()
        }
    }
    
    /**
     * Save section settings to SharedPreferences
     */
    private fun saveSectionSettings() {
        try {
            val settingsJson = json.encodeToString(_sectionSettings.value)
            preferences.edit()
                .putString(SECTION_SETTINGS_KEY, settingsJson)
                .apply()
        } catch (e: Exception) {
            // Handle save errors gracefully
        }
    }
}