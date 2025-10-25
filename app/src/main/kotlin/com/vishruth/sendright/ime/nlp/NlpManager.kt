/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package com.vishruth.key1.ime.nlp

import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import androidx.lifecycle.MutableLiveData
import com.vishruth.key1.app.FlorisPreferenceStore
import com.vishruth.key1.clipboardManager
import com.vishruth.key1.editorInstance
import com.vishruth.key1.ime.clipboard.provider.ClipboardItem
import com.vishruth.key1.ime.clipboard.provider.ItemType
import com.vishruth.key1.ime.core.Subtype
import com.vishruth.key1.ime.editor.EditorContent
import com.vishruth.key1.ime.editor.EditorRange
import com.vishruth.key1.ime.media.emoji.EmojiSuggestionProvider
import com.vishruth.key1.ime.nlp.han.HanShapeBasedLanguageProvider
import com.vishruth.key1.ime.nlp.latin.LatinLanguageProvider
import com.vishruth.key1.ime.smartbar.CandidatesDisplayMode
import com.vishruth.key1.keyboardManager
import com.vishruth.key1.lib.util.NetworkUtils
import com.vishruth.key1.subtypeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.florisboard.lib.kotlin.guardedByLock
import org.florisboard.lib.kotlin.collectLatestIn
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.properties.Delegates

private const val BLANK_STR_PATTERN = "^\\s*$"

class NlpManager(context: Context) {
    private val blankStrRegex = Regex(BLANK_STR_PATTERN)

    private val prefs by FlorisPreferenceStore
    private val clipboardManager by context.clipboardManager()
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
    private val subtypeManager by context.subtypeManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val clipboardSuggestionProvider = ClipboardSuggestionProvider(context)
    private val emojiSuggestionProvider = EmojiSuggestionProvider(context)
    private val providers = guardedByLock {
        mapOf(
            LatinLanguageProvider.ProviderId to ProviderInstanceWrapper(LatinLanguageProvider(context)),
            HanShapeBasedLanguageProvider.ProviderId to ProviderInstanceWrapper(HanShapeBasedLanguageProvider(context)),
        )
    }
    // lock unnecessary because values constant
    private val providersForceSuggestionOn = mutableMapOf<String, Boolean>()

    private val internalSuggestionsGuard = Mutex()
    private var internalSuggestions by Delegates.observable(SystemClock.uptimeMillis() to listOf<SuggestionCandidate>()) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            scope.launch { assembleCandidates() }
        }
    }

    private val _activeCandidatesFlow = MutableStateFlow(listOf<SuggestionCandidate>())
    val activeCandidatesFlow = _activeCandidatesFlow.asStateFlow()
    inline var activeCandidates
        get() = activeCandidatesFlow.value
        private set(v) {
            _activeCandidatesFlow.value = v
        }

    val debugOverlaySuggestionsInfos = LruCache<Long, Pair<String, SpellingResult>>(10)
    var debugOverlayVersion = MutableLiveData(0)
    private val debugOverlayVersionSource = AtomicInteger(0)

    init {
        clipboardManager.primaryClipFlow.collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.suggestion.enabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.clipboard.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.emoji.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        subtypeManager.activeSubtypeFlow.collectLatestIn(scope) { subtype ->
            preload(subtype)
        }
    }

    /**
     * Gets the punctuation rule from the currently active subtype and returns it. Falls back to a default one if the
     * subtype does not exist or defines an invalid punctuation rule.
     *
     * @return The punctuation rule or a fallback.
     */
    fun getActivePunctuationRule(): PunctuationRule {
        return getPunctuationRule(subtypeManager.activeSubtype)
    }

    /**
     * Gets the punctuation rule from the given subtype and returns it. Falls back to a default one if the subtype does
     * not exist or defines an invalid punctuation rule.
     *
     * @return The punctuation rule or a fallback.
     */
    fun getPunctuationRule(subtype: Subtype): PunctuationRule {
        return keyboardManager.resources.punctuationRules.value
            ?.get(subtype.punctuationRule) ?: PunctuationRule.Fallback
    }

    private suspend fun getSpellingProvider(subtype: Subtype): SpellingProvider {
        return providers.withLock { it[subtype.nlpProviders.spelling] }?.provider as? SpellingProvider
            ?: FallbackNlpProvider
    }

    private suspend fun getSuggestionProvider(subtype: Subtype): SuggestionProvider {
        return providers.withLock { it[subtype.nlpProviders.suggestion] }?.provider as? SuggestionProvider
            ?: FallbackNlpProvider
    }

    fun preload(subtype: Subtype) {
        scope.launch {
            emojiSuggestionProvider.preload(subtype)
            providers.withLock { providers ->
                subtype.nlpProviders.forEach { _, providerId ->
                    providers[providerId]?.let { provider ->
                        provider.createIfNecessary()
                        provider.preload(subtype)
                    }
                }
            }
        }
    }

    /**
     * Spell wrapper helper which calls the spelling provider and returns the result. Coroutine management must be done
     * by the source spell checker service.
     */
    suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
    ): SpellingResult {
        return getSpellingProvider(subtype).spell(
            subtype = subtype,
            word = word,
            precedingWords = precedingWords,
            followingWords = followingWords,
            maxSuggestionCount = maxSuggestionCount,
            allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
            isPrivateSession = keyboardManager.activeState.isIncognitoMode,
        )
    }

    suspend fun determineLocalComposing(
        textBeforeSelection: CharSequence, breakIterators: BreakIteratorGroup, localLastCommitPosition: Int
    ): EditorRange {
        return getSuggestionProvider(subtypeManager.activeSubtype).determineLocalComposing(
            subtypeManager.activeSubtype, textBeforeSelection, breakIterators, localLastCommitPosition
        )
    }

    fun providerForcesSuggestionOn(subtype: Subtype): Boolean {
        // Using a cache because I have no idea how fast the runBlocking is
        return providersForceSuggestionOn.getOrPut(subtype.nlpProviders.suggestion) {
            runBlocking {
                getSuggestionProvider(subtype).forcesSuggestionOn
            }
        }
    }

    fun isSuggestionOn(): Boolean =
        prefs.suggestion.enabled.get()
            || prefs.emoji.suggestionEnabled.get()
            || providerForcesSuggestionOn(subtypeManager.activeSubtype)

    fun suggest(subtype: Subtype, content: EditorContent) {
        val reqTime = SystemClock.uptimeMillis()
        scope.launch {
            val emojiSuggestions = when {
                prefs.emoji.suggestionEnabled.get() -> {
                    emojiSuggestionProvider.suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = prefs.emoji.suggestionCandidateMaxCount.get(),
                        allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    )
                }
                else -> emptyList()
            }
            val suggestions = when {
                emojiSuggestions.isNotEmpty() && prefs.emoji.suggestionType.get().prefix.isNotEmpty() -> {
                    // When emoji suggestions are available with prefix (like ":smile"), 
                    // don't show word suggestions to avoid confusion
                    emptyList()
                }
                else -> {
                    // Allow word suggestions when no emoji suggestions or when using inline text mode
                    // Respect classic mode limit when determining max candidate count
                    val isClassicMode = prefs.suggestion.displayMode.get() == CandidatesDisplayMode.CLASSIC
                    val maxWordSuggestions = if (isClassicMode) {
                        // In classic mode, limit to 3 suggestions total
                        3
                    } else {
                        // In other modes, leave space for emoji suggestions if needed
                        if (emojiSuggestions.isNotEmpty()) 5 else 8
                    }
                    
                    val wordSuggestions = getSuggestionProvider(subtype).suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = maxWordSuggestions,
                        allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    )
                    
                    // Add fallback character-by-character suggestions if no word suggestions found
                    // and user is actively typing (composing text exists)
                    if (wordSuggestions.isEmpty() && content.composingText.isNotBlank()) {
                        createCharacterFallbackSuggestions(content.composingText.toString())
                    } else {
                        wordSuggestions
                    }
                }
            }
            internalSuggestionsGuard.withLock {
                if (internalSuggestions.first < reqTime) {
                    // Build suggestions list considering classic mode constraints
                    val finalSuggestions = buildSuggestionsForDisplayMode(emojiSuggestions, suggestions)
                    internalSuggestions = reqTime to finalSuggestions
                }
            }
        }
    }

    fun suggestDirectly(suggestions: List<SuggestionCandidate>) {
        val reqTime = SystemClock.uptimeMillis()
        runBlocking {
            internalSuggestions = reqTime to suggestions
        }
    }

    /**
     * Builds the final suggestions list considering display mode constraints.
     * In classic mode, when emojis are present, limit to 1 emoji + 2 word suggestions.
     */
    private fun buildSuggestionsForDisplayMode(
        emojiSuggestions: List<SuggestionCandidate>,
        wordSuggestions: List<SuggestionCandidate>
    ): List<SuggestionCandidate> {
        val isClassicMode = prefs.suggestion.displayMode.get() == CandidatesDisplayMode.CLASSIC
        
        return when {
            !isClassicMode -> {
                // For non-classic modes, use original behavior
                buildList {
                    addAll(emojiSuggestions)
                    addAll(wordSuggestions)
                }
            }
            emojiSuggestions.isNotEmpty() -> {
                // Classic mode with emoji suggestions: limit to 1 emoji + 2 words
                buildList {
                    // Add up to 1 emoji suggestion (changed from 2 to 1)
                    addAll(emojiSuggestions.take(1))
                    // Add up to 2 word suggestions if available
                    addAll(wordSuggestions.take(2))
                }
            }
            else -> {
                // Classic mode without emoji suggestions: show up to 3 word suggestions
                wordSuggestions.take(3)
            }
        }
    }

    fun clearSuggestions() {
        val reqTime = SystemClock.uptimeMillis()
        runBlocking {
            internalSuggestions = reqTime to emptyList()
        }
    }

    fun getAutoCommitCandidate(): SuggestionCandidate? {
        // Check if auto-correct is enabled in preferences
        if (!prefs.correction.autoCorrectEnabled.get()) {
            return null
        }
        
        // Get current composing text for context analysis
        val composingText = editorInstance.activeContent.composingText.toString().trim()
        
        // First try to find a candidate that's explicitly eligible for auto-commit
        val explicitCandidate = activeCandidates.firstOrNull { it.isEligibleForAutoCommit }
        if (explicitCandidate != null) {
            // Filter out emojis from auto-commit
            if (containsEmoji(explicitCandidate.text.toString())) {
                return null
            }
            return explicitCandidate
        }
        
        // Enhanced auto-commit logic with aggressiveness levels
        // Since user words are already filtered out when auto-correct is enabled,
        // we can use the normal candidate selection logic
        val bestCandidate = activeCandidates.firstOrNull()
        if (bestCandidate != null && composingText.isNotEmpty()) {
            // Filter out emojis from auto-commit
            if (containsEmoji(bestCandidate.text.toString())) {
                return null
            }
            
            val candidateText = bestCandidate.text.toString().lowercase()
            val composingLower = composingText.lowercase()
            
            // Calculate edit distance for typo detection
            val editDistance = calculateLevenshteinDistance(composingLower, candidateText)
            val lengthDiff = kotlin.math.abs(composingText.length - candidateText.length)
            
            // Get aggressiveness level from preferences
            val aggressiveness = prefs.correction.autoCorrectAggressiveness.get()
            
            // Determine auto-commit thresholds based on aggressiveness level
            val (minConfidence, maxEditDistance, minWordLength) = when (aggressiveness) {
                AutoCorrectAggressiveness.CONSERVATIVE -> Triple(0.95, 1, 4)
                AutoCorrectAggressiveness.MODERATE -> Triple(0.75, 2, 3)
                AutoCorrectAggressiveness.AGGRESSIVE -> Triple(0.6, 2, 2)
            }
            
            // Improved auto-commit criteria based on aggressiveness
            val shouldAutoCommit = when {
                // High confidence exact or very close matches
                bestCandidate.confidence > minConfidence && editDistance <= 1 -> true
                
                // Common typo patterns (single character mistakes)
                bestCandidate.confidence > (minConfidence - 0.1) && editDistance <= maxEditDistance && 
                lengthDiff <= 1 && composingText.length >= minWordLength -> true
                
                // Prefix completions with good confidence and meaningful length
                bestCandidate.confidence > (minConfidence - 0.15) && candidateText.startsWith(composingLower) && 
                composingLower.length >= minWordLength && candidateText.length <= composingLower.length + 4 -> true
                
                // Conservative mode: only very high confidence exact matches
                aggressiveness == AutoCorrectAggressiveness.CONSERVATIVE && 
                bestCandidate.confidence > 0.95 && editDistance == 0 -> true
                
                // Aggressive mode: allow more corrections
                aggressiveness == AutoCorrectAggressiveness.AGGRESSIVE && 
                bestCandidate.confidence > 0.6 && editDistance <= 2 && composingText.length >= 2 -> true
                
                else -> false
            }
            
            if (shouldAutoCommit) {
                return bestCandidate
            }
        }
        
        // Final fallback: very high confidence candidates only
        return activeCandidates.firstOrNull { it.confidence > 0.95 }
    }
    
    /**
     * Calculate Levenshtein distance between two strings for typo detection
     */
    private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        if (len1 == 0) return len2
        if (len2 == 0) return len1
        
        val matrix = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) matrix[i][0] = i
        for (j in 0..len2) matrix[0][j] = j
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                matrix[i][j] = kotlin.math.min(
                    matrix[i - 1][j] + 1,
                    kotlin.math.min(
                        matrix[i][j - 1] + 1,
                        matrix[i - 1][j - 1] + cost
                    )
                )
            }
        }
        
        return matrix[len1][len2]
    }

    fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        return runBlocking { candidate.sourceProvider?.removeSuggestion(subtype, candidate) == true }.also { result ->
            if (result) {
                scope.launch {
                    // Need to re-trigger the suggestions algorithm
                    if (candidate is ClipboardSuggestionCandidate) {
                        assembleCandidates()
                    } else {
                        suggest(subtypeManager.activeSubtype, editorInstance.activeContent)
                    }
                }
            }
        }
    }

    fun getListOfWords(subtype: Subtype): List<String> {
        // flogDebug { "NlpManager.getListOfWords called for subtype: ${subtype.primaryLocale}" }
        return runBlocking { getSuggestionProvider(subtype).getListOfWords(subtype) }
    }

    fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return runBlocking { getSuggestionProvider(subtype).getFrequencyForWord(subtype, word) }
    }

    private fun assembleCandidates() {
        runBlocking {
            val candidates = when {
                isSuggestionOn() -> {
                    // Respect classic mode limit for clipboard suggestions
                    val isClassicMode = prefs.suggestion.displayMode.get() == CandidatesDisplayMode.CLASSIC
                    val maxClipboardSuggestions = if (isClassicMode) 3 else 8
                    
                    clipboardSuggestionProvider.suggest(
                        subtype = Subtype.DEFAULT,
                        content = editorInstance.activeContent,
                        maxCandidateCount = maxClipboardSuggestions,
                        allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    ).ifEmpty {
                        buildList {
                            internalSuggestionsGuard.withLock {
                                addAll(internalSuggestions.second)
                            }
                        }
                    }
                }
                else -> emptyList()
            }
            // Only update active candidates if they actually changed
            // This prevents unnecessary UI updates and improves performance
            // Also check if the content has actually changed to prevent flickering
            if (activeCandidates != candidates) {
                activeCandidates = candidates
            }
            autoExpandCollapseSmartbarActions(candidates, NlpInlineAutofill.suggestions.value)
        }
    }

    fun autoExpandCollapseSmartbarActions(list1: List<*>?, list2: List<*>?) {
        if (!prefs.smartbar.enabled.get()) {// || !prefs.smartbar.sharedActionsAutoExpandCollapse.get()) {
            return
        }
        // TODO: this is a mess and needs to be cleaned up in v0.5 with the NLP development
        /*if (keyboardManager.inputEventDispatcher.isRepeatableCodeLastDown()
            && !keyboardManager.inputEventDispatcher.isPressed(KeyCode.DELETE)
            && !keyboardManager.inputEventDispatcher.isPressed(KeyCode.FORWARD_DELETE)
            || keyboardManager.activeState.isActionsOverflowVisible
        ) {
            return // We do not auto switch if a repeatable action key was last pressed or if the actions overflow
                   // menu is visible to prevent annoying UI changes
        }*/
        val isSelection = editorInstance.activeContent.selection.isSelectionMode
        val isExpanded = list1.isNullOrEmpty() && list2.isNullOrEmpty() || isSelection
        scope.launch {
            prefs.smartbar.sharedActionsExpandWithAnimation.set(false)
            prefs.smartbar.sharedActionsExpanded.set(isExpanded)
        }
    }

    fun addToDebugOverlay(word: String, info: SpellingResult) {
        val version = debugOverlayVersionSource.incrementAndGet()
        debugOverlaySuggestionsInfos.put(System.currentTimeMillis(), word to info)
        debugOverlayVersion.postValue(version)
    }

    fun clearDebugOverlay() {
        val version = debugOverlayVersionSource.incrementAndGet()
        debugOverlaySuggestionsInfos.evictAll()
        debugOverlayVersion.postValue(version)
    }

    /**
     * Creates character-by-character fallback suggestions when no word matches are found.
     * This shows the user's typed text letter by letter as suggestion candidates.
     */
    private fun createCharacterFallbackSuggestions(composingText: String): List<SuggestionCandidate> {
        if (composingText.length < 2) {
            // For very short text, don't provide fallback suggestions
            return emptyList()
        }
        
        return buildList<SuggestionCandidate> {
            // Only show the complete typed text as a single suggestion in the first column
            // The other columns will remain empty for cleaner appearance
            add(WordSuggestionCandidate(
                text = composingText,
                confidence = 0.2, // Low confidence so it doesn't interfere with real suggestions
                isEligibleForAutoCommit = false,
                isEligibleForUserRemoval = false,
                sourceProvider = null
            ))
        }
    }

    private class ProviderInstanceWrapper(val provider: NlpProvider) {
        private var isInstanceAlive = AtomicBoolean(false)

        suspend fun createIfNecessary() {
            if (!isInstanceAlive.getAndSet(true)) provider.create()
        }

        suspend fun preload(subtype: Subtype) {
            provider.preload(subtype)
        }

        suspend fun destroyIfNecessary() {
            if (isInstanceAlive.getAndSet(true)) provider.destroy()
        }
    }

    inner class ClipboardSuggestionProvider internal constructor(private val context: Context) : SuggestionProvider {
        private var lastClipboardItemId: Long = -1

        override val providerId = "org.florisboard.nlp.providers.clipboard"

        override suspend fun create() {
            // Do nothing
        }

        override suspend fun preload(subtype: Subtype) {
            // Do nothing
        }

        override suspend fun suggest(
            subtype: Subtype,
            content: EditorContent,
            maxCandidateCount: Int,
            allowPossiblyOffensive: Boolean,
            isPrivateSession: Boolean,
        ): List<SuggestionCandidate> {
            // Check if enabled
            if (!prefs.clipboard.suggestionEnabled.get()) return emptyList()

            val currentItem = validateClipboardItem(clipboardManager.primaryClip, lastClipboardItemId, content.text)
                ?: return emptyList()

            return buildList {
                val now = System.currentTimeMillis()
                if ((now - currentItem.creationTimestampMs) < prefs.clipboard.suggestionTimeout.get() * 1000) {
                    add(ClipboardSuggestionCandidate(currentItem, sourceProvider = this@ClipboardSuggestionProvider, context = context))
                    if (currentItem.isSensitive) {
                        return@buildList
                    }
                    if (currentItem.type == ItemType.TEXT) {
                        val text = currentItem.stringRepresentation()
                        val matches = buildList {
                            addAll(NetworkUtils.getEmailAddresses(text))
                            addAll(NetworkUtils.getUrls(text))
                            addAll(NetworkUtils.getPhoneNumbers(text))
                        }
                        matches.forEachIndexed { i, match ->
                            val isUniqueMatch = matches.subList(0, i).all { prevMatch ->
                                prevMatch.value != match.value && prevMatch.range.intersect(match.range).isEmpty()
                            }
                            if (match.value != text && isUniqueMatch) {
                                add(ClipboardSuggestionCandidate(
                                    clipboardItem = currentItem.copy(
                                        // TODO: adjust regex of phone number so we don't need to manually strip the
                                        //  parentheses from the match results
                                        text = if (match.value.startsWith("(") && match.value.endsWith(")")) {
                                            match.value.substring(1, match.value.length - 1)
                                        } else {
                                            match.value
                                        }
                                    ),
                                    sourceProvider = this@ClipboardSuggestionProvider,
                                    context = context,
                                ))
                            }
                        }
                    }
                }
            }
        }

        override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
            }
        }

        override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
            // Do nothing
        }

        override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
                return true
            }
            return false
        }

        override suspend fun getListOfWords(subtype: Subtype): List<String> {
            return emptyList()
        }

        override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
            return 0.0
        }

        override suspend fun destroy() {
            // Do nothing
        }

        private fun validateClipboardItem(currentItem: ClipboardItem?, lastItemId: Long, contentText: String) =
            currentItem?.takeIf {
                // Check if already used
                it.id != lastItemId
                    // Check if content is empty
                    && contentText.isBlank()
                    // Check if clipboard content has any valid characters
                    && !currentItem.text.isNullOrBlank()
                    && !blankStrRegex.matches(currentItem.text)
            }
    }
    
    /**
     * Checks if the given text contains any emoji characters.
     * This prevents emojis from being auto-corrected/auto-committed.
     */
    private fun containsEmoji(text: String): Boolean {
        return text.any { char ->
            val type = Character.getType(char)
            // Check for emoji unicode ranges
            when {
                // Basic emoji blocks
                char.code in 0x1F600..0x1F64F || // Emoticons
                char.code in 0x1F300..0x1F5FF || // Misc Symbols and Pictographs
                char.code in 0x1F680..0x1F6FF || // Transport and Map
                char.code in 0x1F1E6..0x1F1FF || // Regional indicators (flags)
                char.code in 0x2600..0x26FF ||   // Misc symbols
                char.code in 0x2700..0x27BF ||   // Dingbats
                char.code in 0xFE00..0xFE0F ||   // Variation selectors
                char.code in 0x1F900..0x1F9FF || // Supplemental Symbols
                char.code in 0x1F018..0x1F270 || // Various symbols
                type == Character.SURROGATE.toInt() -> true
                else -> false
            }
        }
    }
}
