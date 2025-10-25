/*
 * Copyright (C) 2025 The SendrightX Contributors
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

package com.vishruth.sendright.ime.nlp

import android.content.Context
import com.vishruth.key1.app.FlorisPreferenceStore
import com.vishruth.key1.lib.devtools.flogDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Data class representing a text shortcut
 */
@Serializable
data class Shortcut(
    val trigger: String,
    val expansion: String,
    val enabled: Boolean = true
)

/**
 * Manager for text shortcuts/expansions functionality
 */
class ShortcutManager(private val context: Context) {
    private val prefs by FlorisPreferenceStore
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()
    
    private var shortcuts = mutableMapOf<String, String>()
    
    companion object {
        /**
         * Default shortcuts that come pre-installed with the keyboard
         */
        val DEFAULT_SHORTCUTS = listOf(
            Shortcut("hru", "How are you"),
            Shortcut("wht", "What"),
            Shortcut("dng", "Doing"),
            Shortcut("gm", "Good morning"),
            Shortcut("gn", "Good night"),
            Shortcut("ty", "Thank you"),
            Shortcut("ur", "Your"),
            Shortcut("bc", "Because"),
            Shortcut("rn", "Right now"),
            Shortcut("omg", "Oh my god"),
            Shortcut("lol", "Laugh out loud"),
            Shortcut("btw", "By the way"),
            Shortcut("imo", "In my opinion"),
            Shortcut("fyi", "For your information"),
            Shortcut("asap", "As soon as possible"),
            Shortcut("aka", "Also known as"),
            Shortcut("etc", "Et cetera"),
            Shortcut("tmrw", "Tomorrow"),
            Shortcut("pls", "Please"),
            Shortcut("thx", "Thanks")
        )
    }
    
    /**
     * Initialize the shortcut manager and load shortcuts
     */
    suspend fun initialize() {
        mutex.withLock {
            loadShortcuts()
        }
    }
    
    /**
     * Check if shortcuts are enabled
     */
    fun isEnabled(): Boolean {
        return prefs.shortcuts.enabled.get()
    }
    
    /**
     * Load shortcuts from preferences
     */
    private fun loadShortcuts() {
        shortcuts.clear()
        
        if (!isEnabled()) {
            flogDebug { "Shortcuts are disabled" }
            return
        }
        
        try {
            val shortcutsJson = prefs.shortcuts.customShortcuts.get()
            if (shortcutsJson.isNotEmpty()) {
                val loadedShortcuts = Json.decodeFromString<List<Shortcut>>(shortcutsJson)
                for (shortcut in loadedShortcuts) {
                    if (shortcut.enabled) {
                        shortcuts[shortcut.trigger.lowercase()] = shortcut.expansion
                    }
                }
                flogDebug { "Loaded ${shortcuts.size} custom shortcuts" }
            }
        } catch (e: Exception) {
            flogDebug { "Failed to load custom shortcuts: ${e.message}" }
        }
        
        // Load default shortcuts if enabled
        if (prefs.shortcuts.useDefaultShortcuts.get()) {
            for (shortcut in DEFAULT_SHORTCUTS) {
                if (shortcut.enabled) {
                    shortcuts[shortcut.trigger.lowercase()] = shortcut.expansion
                }
            }
            flogDebug { "Loaded ${DEFAULT_SHORTCUTS.size} default shortcuts" }
        }
        
        flogDebug { "Total shortcuts loaded: ${shortcuts.size}" }
    }
    
    /**
     * Check if the given text is a shortcut trigger and return the expansion
     * @param text The text to check
     * @return The expansion if found, null otherwise
     */
    suspend fun getExpansion(text: String): String? {
        if (!isEnabled()) return null
        
        return mutex.withLock {
            shortcuts[text.lowercase()]
        }
    }
    
    /**
     * Add a new custom shortcut
     */
    suspend fun addShortcut(trigger: String, expansion: String): Boolean {
        if (trigger.isBlank() || expansion.isBlank()) return false
        
        mutex.withLock {
            try {
                val currentShortcuts = getCurrentCustomShortcuts().toMutableList()
                
                // Remove existing shortcut with same trigger if exists
                currentShortcuts.removeAll { it.trigger.equals(trigger, ignoreCase = true) }
                
                // Add new shortcut
                currentShortcuts.add(Shortcut(trigger, expansion, true))
                
                // Save to preferences
                val json = Json.encodeToString(currentShortcuts)
                prefs.shortcuts.customShortcuts.set(json)
                
                // Reload shortcuts
                loadShortcuts()
                
                flogDebug { "Added shortcut: $trigger -> $expansion" }
                return true
            } catch (e: Exception) {
                flogDebug { "Failed to add shortcut: ${e.message}" }
                return false
            }
        }
    }
    
    /**
     * Remove a custom shortcut
     */
    suspend fun removeShortcut(trigger: String): Boolean {
        mutex.withLock {
            try {
                val currentShortcuts = getCurrentCustomShortcuts().toMutableList()
                val removed = currentShortcuts.removeAll { it.trigger.equals(trigger, ignoreCase = true) }
                
                if (removed) {
                    val json = Json.encodeToString(currentShortcuts)
                    prefs.shortcuts.customShortcuts.set(json)
                    
                    // Reload shortcuts
                    loadShortcuts()
                    
                    flogDebug { "Removed shortcut: $trigger" }
                }
                
                return removed
            } catch (e: Exception) {
                flogDebug { "Failed to remove shortcut: ${e.message}" }
                return false
            }
        }
    }
    
    /**
     * Get all custom shortcuts
     */
    private fun getCurrentCustomShortcuts(): List<Shortcut> {
        return try {
            val shortcutsJson = prefs.shortcuts.customShortcuts.get()
            if (shortcutsJson.isNotEmpty()) {
                Json.decodeFromString<List<Shortcut>>(shortcutsJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            flogDebug { "Failed to get custom shortcuts: ${e.message}" }
            emptyList()
        }
    }
    
    /**
     * Get all shortcuts (default + custom) for display in UI
     */
    suspend fun getAllShortcuts(): Pair<List<Shortcut>, List<Shortcut>> {
        mutex.withLock {
            val defaultShortcuts = if (prefs.shortcuts.useDefaultShortcuts.get()) {
                DEFAULT_SHORTCUTS
            } else {
                emptyList()
            }
            val customShortcuts = getCurrentCustomShortcuts()
            return Pair(defaultShortcuts, customShortcuts)
        }
    }
    
    /**
     * Refresh shortcuts when settings change
     */
    suspend fun refresh() {
        mutex.withLock {
            loadShortcuts()
        }
    }
    
    /**
     * Check if the text looks like it could be a word (for shortcut expansion)
     * This helps avoid expanding shortcuts in the middle of other words
     */
    fun isValidShortcutContext(textBeforeSelection: String): Boolean {
        if (textBeforeSelection.isEmpty()) return true
        
        val lastChar = textBeforeSelection.last()
        return lastChar.isWhitespace() || lastChar in ".,!?;:"
    }
}