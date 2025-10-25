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

package com.vishruth.key1.ime.text.gestures

import android.content.Context
import com.vishruth.key1.app.FlorisPreferenceStore
import com.vishruth.key1.ime.nlp.WordSuggestionCandidate
import com.vishruth.key1.ime.text.keyboard.TextKey
import com.vishruth.key1.keyboardManager
import com.vishruth.key1.nlpManager
import com.vishruth.key1.subtypeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.AndroidVersion
import kotlin.math.min

/**
 * Handles the [GlideTypingClassifier]. Basically responsible for linking [GlideTypingGesture.Detector]
 * with [GlideTypingClassifier].
 */
class GlideTypingManager(context: Context) : GlideTypingGesture.Listener {
    companion object {
        private const val MAX_SUGGESTION_COUNT = 8
        // Balanced preview refresh for all devices
        private const val OPTIMIZED_PREVIEW_REFRESH_DELAY_OLD = 100L
        private const val OPTIMIZED_PREVIEW_REFRESH_DELAY_NEW = 75L
        // Minimum delay between preview updates to prevent flickering
        private const val MIN_PREVIEW_UPDATE_DELAY = 50L
    }

    private val prefs by FlorisPreferenceStore
    private val keyboardManager by context.keyboardManager()
    private val nlpManager by context.nlpManager()
    private val subtypeManager by context.subtypeManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var glideTypingClassifier = StatisticalGlideTypingClassifier(context)
    private var lastTime = System.currentTimeMillis()
    private var lastPreviewUpdateTime = System.currentTimeMillis()

    override fun onGlideComplete(data: GlideTypingGesture.Detector.PointerData) {
        // flogDebug { "onGlideComplete called" }
        updateSuggestionsAsync(MAX_SUGGESTION_COUNT, true) {
            glideTypingClassifier.clear()
        }
    }

    override fun onGlideCancelled() {
        glideTypingClassifier.clear()
    }

    override fun onGlideAddPoint(point: GlideTypingGesture.Detector.Position) {
        val normalized = GlideTypingGesture.Detector.Position(point.x, point.y)

        this.glideTypingClassifier.addGesturePoint(normalized)

        val time = System.currentTimeMillis()
        // Use balanced preview refresh for consistent experience
        val previewRefreshDelay = if (AndroidVersion.ATMOST_API29_Q) OPTIMIZED_PREVIEW_REFRESH_DELAY_OLD else OPTIMIZED_PREVIEW_REFRESH_DELAY_NEW
        val userPreviewDelay = prefs.glide.previewRefreshDelay.get().toLong()
        val actualPreviewDelay = if (AndroidVersion.ATMOST_API29_Q) previewRefreshDelay else userPreviewDelay
        
        // Add debounce to prevent flickering during gesture
        if (prefs.glide.showPreview.get() && time - lastTime > actualPreviewDelay && time - lastPreviewUpdateTime > MIN_PREVIEW_UPDATE_DELAY) {
            updateSuggestionsAsync(2, false) {}  // Balanced preview suggestions
            lastTime = time
            lastPreviewUpdateTime = time
        }
    }

    /**
     * Change the layout of the internal gesture classifier
     */
    fun setLayout(keys: List<TextKey>) {
        if (keys.isNotEmpty()) {
            glideTypingClassifier.setLayout(keys, subtypeManager.activeSubtype)
        }
    }

    /**
     * Refresh the word data in the internal gesture classifier
     */
    fun refreshWordData() {
        // flogDebug { "GlideTypingManager.refreshWordData() called" }
        glideTypingClassifier.setWordData(subtypeManager.activeSubtype, true)
        // flogDebug { "GlideTypingManager.refreshWordData() completed" }
    }

    /**
     * Asks gesture classifier for suggestions and then passes that on to the smartbar.
     * Also commits the most confident suggestion if [commit] is set. All happens on an async executor.
     * NB: only fetches [MAX_SUGGESTION_COUNT] suggestions.
     *
     * @param callback Called when this function completes. Takes a boolean, which indicates if suggestions
     * were successfully set.
     */
    private fun updateSuggestionsAsync(maxSuggestionsToShow: Int, commit: Boolean, callback: (Boolean) -> Unit) {
        // flogDebug { "updateSuggestionsAsync called with maxSuggestionsToShow: $maxSuggestionsToShow, commit: $commit" }
        if (!glideTypingClassifier.ready) {
            // flogDebug { "Glide typing classifier not ready" }
            callback.invoke(false)
            return
        }

        scope.launch(Dispatchers.Default) {
            // flogDebug { "Getting suggestions from glide typing classifier" }
            val suggestions = glideTypingClassifier.getSuggestions(MAX_SUGGESTION_COUNT, true)
            // flogDebug { "Got ${suggestions.size} suggestions from classifier" }

            withContext(Dispatchers.Main) {
                // Create the full suggestion list for smartbar
                val suggestionList = buildList {
                    suggestions.subList(
                        0,  // Start from index 0 for immediate suggestions
                        maxSuggestionsToShow.coerceAtMost(suggestions.size)
                    ).map { keyboardManager.fixCase(it) }.forEach {
                        add(WordSuggestionCandidate(it, confidence = 1.0, isFromUserDictionary = false))
                    }
                }
                // flogDebug { "Sending ${suggestionList.size} suggestions to NLP manager" }

                // Send all suggestions to smartbar
                nlpManager.suggestDirectly(suggestionList)
                
                // Commit the most confident suggestion if requested
                if (commit && suggestions.isNotEmpty()) {
                    keyboardManager.commitGesture(suggestions.first())
                }
                
                callback.invoke(true)
            }
        }
    }
}