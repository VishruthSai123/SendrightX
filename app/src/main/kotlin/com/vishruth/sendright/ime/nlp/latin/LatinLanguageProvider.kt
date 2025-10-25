/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

package com.vishruth.key1.ime.nlp.latin

import android.content.Context
import com.vishruth.key1.app.FlorisPreferenceStore
import com.vishruth.key1.appContext
import com.vishruth.key1.ime.core.Subtype
import com.vishruth.key1.ime.dictionary.DictionaryManager
import com.vishruth.key1.ime.dictionary.FREQUENCY_DEFAULT
import com.vishruth.key1.ime.dictionary.FREQUENCY_MAX
import com.vishruth.key1.ime.dictionary.UserDictionaryEntry
import com.vishruth.key1.ime.editor.EditorContent
import com.vishruth.key1.ime.nlp.SpellingProvider
import com.vishruth.key1.ime.nlp.SpellingResult
import com.vishruth.key1.ime.nlp.SuggestionCandidate
import com.vishruth.key1.ime.nlp.SuggestionProvider
import com.vishruth.key1.ime.nlp.WordSuggestionCandidate
import com.vishruth.key1.glideTypingManager
import com.vishruth.key1.ime.text.gestures.StatisticalGlideTypingClassifier
import com.vishruth.key1.lib.FlorisLocale
import com.vishruth.key1.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.readText
import org.florisboard.lib.kotlin.guardedByLock

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.latin"
    }

    private val appContext by context.appContext()
    private val prefs by FlorisPreferenceStore
    private val wordData = guardedByLock { mutableMapOf<String, Int>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())

    override val providerId = ProviderId

    override suspend fun create() {
        // Initialize provider
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        wordData.withLock { wordData ->
            if (wordData.isEmpty()) {
                val rawData = appContext.assets.readText("ime/dict/data.json")
                val jsonData = Json.decodeFromString(wordDataSerializer, rawData)
                wordData.putAll(jsonData)
            }
        }
    }

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        val wordLower = word.lowercase().trim()
        
        return wordData.withLock { wordData ->
            when {
                // Check for exact match first (case-insensitive)
                wordData.containsKey(wordLower) || wordData.containsKey(word) -> {
                    SpellingResult.validWord()
                }
                // Enhanced typo detection for better spell checking
                wordLower.length > 2 -> {
                    val suggestions = mutableListOf<Pair<String, Int>>()
                    
                    // Enhanced typo detection with better scoring
                    wordData.forEach { (dictWord, frequency) ->
                        val dictWordLower = dictWord.lowercase()
                        val distance = calculateEditDistance(wordLower, dictWordLower)
                        val lengthDiff = kotlin.math.abs(wordLower.length - dictWordLower.length)
                        
                        // More lenient criteria for spell checking suggestions
                        val isGoodMatch = when {
                            // Allow 1 edit distance for similar length words
                            distance == 1 && lengthDiff <= 1 -> true
                            // Allow 2 edit distance for longer words
                            distance == 2 && wordLower.length >= 5 && lengthDiff <= 2 -> true
                            // Special handling for transposition typos
                            distance == 2 && isTranspositionTypo(wordLower, dictWordLower) -> true
                            // Allow prefix matches for completion
                            dictWordLower.startsWith(wordLower) && dictWordLower.length <= wordLower.length + 3 -> true
                            else -> false
                        }
                        
                        if (isGoodMatch) {
                            // Score based on edit distance and frequency
                            val distanceScore = when (distance) {
                                0 -> 1000 // Exact match (shouldn't happen here but just in case)
                                1 -> 900  // Single character difference
                                2 -> 700  // Two character difference
                                else -> 500
                            }
                            val finalScore = distanceScore + (frequency / 10)
                            suggestions.add(dictWord to finalScore)
                        }
                    }
                    
                    val sortedSuggestions = suggestions
                        .sortedByDescending { it.second }
                        .take(maxSuggestionCount)
                        .map { it.first }
                        .toTypedArray()
                    
                    if (sortedSuggestions.isNotEmpty()) {
                        SpellingResult.typo(sortedSuggestions)
                    } else {
                        // For very short words or words that are likely valid but not in dictionary,
                        // we'll treat them as valid to avoid false positives
                        SpellingResult.validWord()
                    }
                }
                // For very short words (1-2 characters), be more lenient
                else -> SpellingResult.validWord()
            }
        }
    }
    
    private fun calculateEditDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        // Early exit for very different lengths
        if (kotlin.math.abs(len1 - len2) > 2) return 3 // Return a value > 2 to indicate too different
        
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
    
    /**
     * Detects common transposition typos where characters are swapped or inserted/deleted in common patterns
     * For example: "ipdate" -> "update", "recieve" -> "receive", "teh" -> "the"
     */
    private fun isTranspositionTypo(typed: String, correct: String): Boolean {
        if (kotlin.math.abs(typed.length - correct.length) > 2) return false
        
        // Check for adjacent character swaps (like "teh" -> "the")
        if (typed.length == correct.length) {
            var swapCount = 0
            for (i in 0 until typed.length - 1) {
                if (typed[i] == correct[i + 1] && typed[i + 1] == correct[i]) {
                    swapCount++
                    if (swapCount > 1) return false // Only allow one swap
                }
            }
            if (swapCount == 1) return true
        }
        
        // Check for common insertion/deletion patterns (like "ipdate" -> "update")
        // This checks if removing one character from the longer string makes them similar
        val longer = if (typed.length > correct.length) typed else correct
        val shorter = if (typed.length > correct.length) correct else typed
        
        if (longer.length - shorter.length == 1) {
            for (i in longer.indices) {
                val withoutChar = longer.removeRange(i, i + 1)
                if (calculateEditDistance(withoutChar, shorter) <= 1) {
                    return true
                }
            }
        }
        
        // Check for keyboard layout proximity (characters that are close on QWERTY keyboard)
        val proximityMap = mapOf(
            'i' to listOf('u', 'o', 'j', 'k', '8', '9'),
            'u' to listOf('i', 'y', 'h', 'j', '7', '8'),
            'o' to listOf('i', 'p', 'k', 'l', '9', '0'),
            'e' to listOf('w', 'r', 's', 'd', '3', '4'),
            'r' to listOf('e', 't', 'd', 'f', '4', '5'),
            't' to listOf('r', 'y', 'f', 'g', '5', '6')
        )
        
        // Check if the difference is just nearby keys
        if (typed.length == correct.length) {
            var proximityErrors = 0
            for (i in typed.indices) {
                if (typed[i] != correct[i]) {
                    val proximityChars = proximityMap[typed[i].lowercaseChar()]
                    if (proximityChars?.contains(correct[i].lowercaseChar()) != true) {
                        proximityErrors++
                        if (proximityErrors > 1) return false
                    }
                }
            }
            return proximityErrors <= 1
        }
        
        return false
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val composingText = content.composingText.toString() // Keep original case
        val composingTextLower = composingText.lowercase().trim() // For matching purposes
        
        flogDebug { "suggest called with composingText: '$composingText', maxCandidateCount: $maxCandidateCount" }
        
        // Get words from the static dictionary
        val staticWordData = wordData.withLock { it.toMap() }
        flogDebug { "Retrieved ${staticWordData.size} words from static dictionary" }
        
        // Get words from the user dictionary
        val userWordData = try {
            val dictionaryManager = DictionaryManager.default()
            dictionaryManager.loadUserDictionariesIfNecessary()
            val userDictionaryDao = dictionaryManager.florisUserDictionaryDao()
            
            flogDebug { "UserDictionaryDao: $userDictionaryDao" }
            
            if (userDictionaryDao != null) {
                // Always include user words for suggestions and glide typing
                val userData = userDictionaryDao.queryAll(subtype.primaryLocale).associate { 
                    it.word to kotlin.math.min(it.freq + 30, FREQUENCY_MAX)  // Increased boost for user words
                }
                flogDebug { "Retrieved ${userData.size} words from user dictionary" }
                flogDebug { "User dictionary words: ${userData.keys.take(20).toList()}" }  // Log first 20 user words for debugging
                userData
            } else {
                flogDebug { "User dictionary DAO is null" }
                emptyMap()
            }
        } catch (e: Exception) {
            flogDebug { "Failed to get user dictionary words for suggestions: ${e.message}" }
            e.printStackTrace()
            emptyMap()
        }
        
        // Always combine both dictionaries - user words will be available for glide typing and manual selection
        // Auto-commit filtering will be handled in NlpManager based on word source
        val combinedWordData = mutableMapOf<String, Int>()
        combinedWordData.putAll(staticWordData)
        if (userWordData.isNotEmpty()) {
            combinedWordData.putAll(userWordData)  // User words will overwrite static words with the same key
        }
        flogDebug { "Combined word data size: ${combinedWordData.size}" }
        flogDebug { "User word data keys: ${userWordData.keys.take(10)}" }  // Log first 10 user words for debugging
        
        if (composingText.isEmpty()) {
            // When there's no composing text, don't provide any suggestions
            // This ensures empty columns when user hasn't typed anything yet
            flogDebug { "No composing text - returning empty suggestions" }
            return emptyList()
        }
        
        // If composing text is very short (1 character), be more restrictive
        if (composingText.length == 1) {
            // Only show exact prefix matches for single characters
            val prefixCandidates = combinedWordData.entries
                .filter { (word, _) -> word.lowercase().startsWith(composingTextLower) }
                .sortedByDescending { it.value }
                .take(maxCandidateCount)
                .map { (word, frequency) ->
                    val isFromUserDict = userWordData.containsKey(word)
                    WordSuggestionCandidate(
                        text = word,
                        confidence = frequency / 255.0,
                        isEligibleForAutoCommit = false,
                        sourceProvider = this@LatinLanguageProvider,
                        isFromUserDictionary = isFromUserDict,
                    )
                }
            flogDebug { "Returning ${prefixCandidates.size} prefix candidates for single character '$composingText'" }
            return prefixCandidates
        }
        
        // For 2-character input, be less restrictive to help with early suggestions
        if (composingText.length == 2) {
            val candidates = combinedWordData.entries
                .filter { (word, _) -> 
                    val wordLower = word.lowercase()
                    when {
                        wordLower.startsWith(composingTextLower) -> true  // Prefix match
                        wordLower.length >= 3 && calculateEditDistance(composingTextLower, wordLower.take(2)) <= 1 -> true  // Early typo detection
                        else -> false
                    }
                }
                .sortedByDescending { it.value }
                .take(maxCandidateCount)
                .map { (word, frequency) ->
                    val wordLower = word.lowercase()
                    val confidence = when {
                        wordLower.startsWith(composingTextLower) -> frequency / 255.0
                        else -> (frequency / 255.0) * 0.7  // Lower confidence for fuzzy matches
                    }
                    val isFromUserDict = userWordData.containsKey(word)
                    
                    WordSuggestionCandidate(
                        text = word,
                        confidence = confidence,
                        isEligibleForAutoCommit = false,  // Don't auto-commit on short input
                        sourceProvider = this@LatinLanguageProvider,
                        isFromUserDictionary = isFromUserDict,
                    )
                }
            flogDebug { "Returning ${candidates.size} candidates for 2-character input '$composingText'" }
            return candidates
        }
        
        // Process words with much stricter similarity requirements
        val exactMatches = mutableListOf<WordSuggestionCandidate>()
        val prefixMatches = mutableListOf<WordSuggestionCandidate>()
        val fuzzyMatches = mutableListOf<WordSuggestionCandidate>()
        
        combinedWordData.forEach { (word, frequency) ->
            val wordLower = word.lowercase()
            val confidence = frequency / 255.0
            
            // Skip very short words unless they're exact matches or very common
            if (word.length < 2 && frequency < 200) return@forEach
            
            // Skip words that are too long compared to input (reduces noise)
            if (word.length > composingText.length + 5) return@forEach
            
            when {
                wordLower == composingTextLower -> {
                    // Preserve original case when matching exactly
                    val finalWord = if (composingText.firstOrNull()?.isUpperCase() == true) {
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(subtype.primaryLocale.base) else it.toString() }
                    } else {
                        word
                    }
                    val isFromUserDict = userWordData.containsKey(word)
                    
                    exactMatches.add(WordSuggestionCandidate(
                        text = finalWord,
                        confidence = 1.0, // Full confidence for exact matches
                        isEligibleForAutoCommit = true, // Always auto-commit exact matches
                        sourceProvider = this@LatinLanguageProvider,
                        isFromUserDictionary = isFromUserDict,
                    ))
                }
                wordLower.startsWith(composingTextLower) -> {
                    // Only accept prefix matches if the typed portion is meaningful
                    val prefixLengthRatio = composingTextLower.length.toDouble() / wordLower.length.toDouble()
                    
                    // Require at least 25% of the word to be typed for shorter words
                    // and adjust requirements based on word length
                    val minRatioRequired = when {
                        word.length <= 4 -> 0.5  // Half the word for very short words
                        word.length <= 6 -> 0.33 // Third of the word for short words  
                        else -> 0.25             // Quarter for longer words
                    }
                    
                    if (prefixLengthRatio >= minRatioRequired) {
                        val adjustedConfidence = when {
                            prefixLengthRatio >= 0.8 -> confidence * 0.95 // Very close match
                            prefixLengthRatio >= 0.6 -> confidence * 0.85 // Good match
                            prefixLengthRatio >= 0.4 -> confidence * 0.75 // Fair match
                            else -> confidence * 0.65 // Weak match
                        }
                        
                        // Preserve case for prefix matches
                        val finalWord = if (composingText.firstOrNull()?.isUpperCase() == true) {
                            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(subtype.primaryLocale.base) else it.toString() }
                        } else {
                            word
                        }
                        val isFromUserDict = userWordData.containsKey(word)
                        
                        prefixMatches.add(WordSuggestionCandidate(
                            text = finalWord,
                            confidence = adjustedConfidence.coerceAtMost(1.0),
                            isEligibleForAutoCommit = composingTextLower.length >= 3 && adjustedConfidence > 0.8,
                            sourceProvider = this@LatinLanguageProvider,
                            isFromUserDictionary = isFromUserDict,
                        ))
                    }
                }
                // Enhanced fuzzy matching for typos and similar words
                composingTextLower.length >= 3 && wordLower.length >= 3 -> {
                    val distance = calculateEditDistance(composingTextLower, wordLower)
                    val lengthDiff = kotlin.math.abs(composingTextLower.length - wordLower.length)
                    
                    // More lenient matching for common typo patterns
                    val isValidFuzzyMatch = when {
                        // Allow 1 edit distance for words of similar length (common typos)
                        distance == 1 && lengthDiff <= 1 -> true
                        // Allow 2 edit distance for longer words with small length difference
                        distance == 2 && composingTextLower.length >= 5 && lengthDiff <= 2 -> true
                        // Special case for transposition typos (like "ipdate" -> "update")
                        distance == 2 && isTranspositionTypo(composingTextLower, wordLower) -> true
                        else -> false
                    }
                    
                    if (isValidFuzzyMatch) {
                        // Calculate similarity score with better weighting
                        val maxLength = kotlin.math.max(composingTextLower.length, wordLower.length)
                        val baseSimilarity = 1.0 - (distance.toDouble() / maxLength.toDouble())
                        
                        // Boost similarity for common patterns
                        val boostedSimilarity = when {
                            // High boost for single character changes
                            distance == 1 -> baseSimilarity * 1.1
                            // Medium boost for transposition typos
                            isTranspositionTypo(composingTextLower, wordLower) -> baseSimilarity * 1.05
                            else -> baseSimilarity
                        }.coerceAtMost(1.0)
                        
                        val adjustedConfidence = (confidence * boostedSimilarity * 0.85).coerceAtMost(0.95)
                        
                        // Preserve case for typo corrections
                        val finalWord = if (composingText.firstOrNull()?.isUpperCase() == true) {
                            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(subtype.primaryLocale.base) else it.toString() }
                        } else {
                            word
                        }
                        val isFromUserDict = userWordData.containsKey(word)
                        
                        fuzzyMatches.add(WordSuggestionCandidate(
                            text = finalWord,
                            confidence = adjustedConfidence,
                            // More lenient auto-commit for typo corrections
                            isEligibleForAutoCommit = distance == 1 && adjustedConfidence > 0.7 && 
                                composingTextLower.length >= 3,
                            sourceProvider = this@LatinLanguageProvider,
                            isFromUserDictionary = isFromUserDict,
                        ))
                    }
                }
                // No fallback suggestions - keep columns empty if no relevant matches
            }
        }
        
        // Build final results with strict prioritization - no fallback suggestions
        val allMatches = buildList {
            // Prioritize exact matches
            addAll(exactMatches.sortedByDescending { it.confidence })
            
            // Then add best prefix matches
            addAll(prefixMatches.sortedByDescending { it.confidence })
            
            // Finally add fuzzy matches (typo corrections)
            addAll(fuzzyMatches.sortedByDescending { it.confidence })
        }
        
        // Return only high-quality matches, keeping columns empty if no good suggestions
        val result = allMatches.take(maxCandidateCount)
        flogDebug { "Returning ${result.size} high-quality candidates for composing text '$composingText'" }
        flogDebug { "Exact: ${exactMatches.size}, Prefix: ${prefixMatches.size}, Fuzzy: ${fuzzyMatches.size}" }
        return result
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { "notifySuggestionAccepted called with candidate: $candidate" }
        
        // Autosave user-typed words to the user dictionary (similar to Gboard)
        try {
            // Only save word suggestions, not clipboard or emoji suggestions
            if (candidate is WordSuggestionCandidate) {
                val word = candidate.text.toString().trim()
                flogDebug { "Processing WordSuggestionCandidate with word: '$word'" }
                
                // Only save non-empty words
                if (word.isNotEmpty()) {
                    // Check if the word is already in our static dictionary
                    val isAlreadyInStaticDict = wordData.withLock { wordData ->
                        wordData.containsKey(word) || wordData.containsKey(word.lowercase())
                    }
                    flogDebug { "Word '$word' isAlreadyInStaticDict: $isAlreadyInStaticDict" }
                    
                    // Only save words that are not already in our static dictionary
                    if (!isAlreadyInStaticDict) {
                        // Get the dictionary manager and save the word to the user dictionary
                        val dictionaryManager = DictionaryManager.default()
                        val userDictionaryDao = dictionaryManager.florisUserDictionaryDao()
                        flogDebug { "UserDictionaryDao: $userDictionaryDao" }
                        
                        // Check if the word already exists in the user dictionary
                        val existingEntries = userDictionaryDao?.queryExact(word, subtype.primaryLocale)
                        flogDebug { "Existing entries for '$word': $existingEntries" }
                        
                        if (existingEntries != null && existingEntries.isEmpty()) {
                            // Word doesn't exist in user dictionary, so add it with a higher frequency
                            // Increase frequency boost for accepted suggestions
                            val entry = UserDictionaryEntry(
                                id = 0, // 0 means auto-generate ID
                                word = word,
                                freq = kotlin.math.min(FREQUENCY_DEFAULT + 35, FREQUENCY_MAX), // Higher boost for accepted suggestions
                                locale = subtype.primaryLocale.localeTag(),
                                shortcut = null
                            )
                            userDictionaryDao.insert(entry)
                            flogDebug { "Autosaved new word to user dictionary: $word with boosted frequency" }
                            
                            // Force refresh the glide typing classifier to include the new word
                            try {
                                // Get the application context to access the glide typing manager
                                val context = appContext
                                val glideTypingManager = context.glideTypingManager
                                // Force refresh the word data in the classifier
                                glideTypingManager.value.refreshWordData()
                                flogDebug { "Forced refresh of glide typing classifier for word: $word" }
                            } catch (e: Exception) {
                                flogDebug { "Failed to force refresh glide typing classifier: ${e.message}" }
                            }
                        } else if (existingEntries != null && existingEntries.isNotEmpty()) {
                            // Word exists, potentially update frequency or other properties
                            // For now, we'll just log that the word already exists
                            flogDebug { "Word already exists in user dictionary: $word" }
                            
                            // Try to increase the frequency of existing words to improve suggestions
                            try {
                                val existingEntry = existingEntries.first()
                                val newFreq = kotlin.math.min(existingEntry.freq + 10, FREQUENCY_MAX)
                                if (newFreq > existingEntry.freq) {
                                    val updatedEntry = existingEntry.copy(freq = newFreq)
                                    userDictionaryDao.update(updatedEntry)
                                    flogDebug { "Updated frequency for existing word '$word' from ${existingEntry.freq} to $newFreq" }
                                    
                                    // Refresh glide typing data
                                    val context = appContext
                                    val glideTypingManager = context.glideTypingManager
                                    glideTypingManager.value.refreshWordData()
                                    flogDebug { "Refreshed glide typing classifier after frequency update for word: $word" }
                                }
                            } catch (e: Exception) {
                                flogDebug { "Failed to update frequency for existing word '$word': ${e.message}" }
                            }
                        } else {
                            flogDebug { "Failed to query user dictionary for word: $word" }
                        }
                    } else {
                        flogDebug { "Word already exists in static dictionary: $word" }
                    }
                } else {
                    flogDebug { "Word is empty, not saving" }
                }
            } else {
                flogDebug { "Candidate is not a WordSuggestionCandidate, not saving" }
            }
        } catch (e: Exception) {
            flogDebug { "Failed to autosave word to user dictionary: ${e.message}" }
            e.printStackTrace()
        }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        return false
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        // flogDebug { "LatinLanguageProvider.getListOfWords called for subtype: ${subtype.primaryLocale}" }
        // Get words from the static dictionary
        val staticWords = wordData.withLock { it.keys.toList() }
        // flogDebug { "Static dictionary size: ${staticWords.size}" }
        
        // Always include user dictionary words for suggestions and glide typing
        // Auto-commit filtering is handled separately in NlpManager
        val userWords = try {
            val dictionaryManager = DictionaryManager.default()
            dictionaryManager.loadUserDictionariesIfNecessary()
            val userDictionaryDao = dictionaryManager.florisUserDictionaryDao()
            
            // flogDebug { "UserDictionaryDao in getListOfWords: $userDictionaryDao" }
            
            if (userDictionaryDao != null) {
                // Query all words from the user dictionary for this subtype
                val userWordsList = userDictionaryDao.queryAll(subtype.primaryLocale).map { it.word }
                flogDebug { "Retrieved ${userWordsList.size} words from user dictionary for locale ${subtype.primaryLocale}" }
                // flogDebug { "User dictionary words in getListOfWords: ${userWordsList.take(20).toList()}" }  // Log first 20 user words for debugging
                userWordsList
            } else {
                // flogDebug { "User dictionary DAO is null in getListOfWords" }
                emptyList()
            }
        } catch (e: Exception) {
            // flogDebug { "Failed to get user dictionary words: ${e.message}" }
            e.printStackTrace()
            emptyList()
        }
        
        // Combine both lists, with user words first to take precedence
        // This ensures both suggestions and glide typing have access to user words
        // flogDebug { "Returning combined word list: ${userWords.size} user words + ${staticWords.size} static words" }
        return userWords + staticWords
    }
    
    /**
     * Get only static dictionary words (excluding user dictionary).
     * Used by auto-correct to prioritize proper dictionary words over user-saved words.
     */
    suspend fun getStaticWords(subtype: Subtype): List<String> {
        return wordData.withLock { it.keys.toList() }
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        flogDebug { "getFrequencyForWord called with word: '$word'" }
        
        // Check if auto-correct is enabled - if so, only use static dictionary
        val isAutoCorrectEnabled = prefs.correction.autoCorrectEnabled.get()
        
        if (!isAutoCorrectEnabled) {
            // Auto-correct disabled: check user dictionary first (higher priority)
            try {
                val dictionaryManager = DictionaryManager.default()
                dictionaryManager.loadUserDictionariesIfNecessary()
                val userDictionaryDao = dictionaryManager.florisUserDictionaryDao()
                
                flogDebug { "UserDictionaryDao in getFrequencyForWord: $userDictionaryDao" }
                
                if (userDictionaryDao != null) {
                    val entries = userDictionaryDao.queryExact(word, subtype.primaryLocale)
                    flogDebug { "Found ${entries.size} entries for word '$word' in user dictionary" }
                    if (entries.isNotEmpty()) {
                        // Give user words a slight boost in frequency
                        val boostedFreq = kotlin.math.min(entries.first().freq + 10, FREQUENCY_MAX)
                        val frequency = boostedFreq / 255.0
                        flogDebug { "Found word '$word' in user dictionary with boosted frequency $frequency" }
                        return frequency
                    }
                }
            } catch (e: Exception) {
                flogDebug { "Failed to get frequency for word '$word' from user dictionary: ${e.message}" }
                e.printStackTrace()
            }
        } else {
            flogDebug { "Auto-correct enabled: skipping user dictionary for frequency check" }
        }
        
        // If not in user dictionary (or auto-correct enabled), check static dictionary
        val staticFrequency = wordData.withLock { it.getOrDefault(word, 0) / 255.0 }
        flogDebug { "Word '$word' frequency from static dictionary: $staticFrequency" }
        return staticFrequency
    }
    
    /**
     * Get frequency for auto-correct purposes, prioritizing dictionary words over user words
     * when the user word might be a misspelling.
     */
    suspend fun getFrequencyForAutoCorrect(subtype: Subtype, word: String, composingText: String): Double {
        // Check static dictionary first for auto-correct purposes
        val staticFreq = wordData.withLock { it.getOrDefault(word, 0) }
        
        // If word exists in static dictionary, prefer it for auto-correct
        if (staticFreq > 0) {
            return staticFreq / 255.0
        }
        
        // Fallback to regular frequency (includes user dictionary)
        return getFrequencyForWord(subtype, word)
    }

    override suspend fun destroy() {
        // Cleanup
    }
}
