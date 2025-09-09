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
        const val ProviderId = "com.vishruth.key1.nlp.providers.latin"
    }

    private val appContext by context.appContext()
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
                // For longer words, check for typos using edit distance
                wordLower.length > 2 -> {
                    val suggestions = mutableListOf<String>()
                    
                    // More sophisticated typo detection
                    wordData.keys.forEach { dictWord ->
                        val distance = calculateEditDistance(wordLower, dictWord.lowercase())
                        // Only consider words with edit distance <= 2 and length difference <= 2
                        if (distance <= 2 && kotlin.math.abs(wordLower.length - dictWord.length) <= 2) {
                            suggestions.add(dictWord)
                        }
                    }
                    
                    val sortedSuggestions = suggestions
                        .sortedByDescending { wordData[it] ?: 0 }
                        .take(maxSuggestionCount)
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
                // Query all words from the user dictionary for this subtype
                // Give user words a significantly higher frequency to prioritize them
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
        
        // Combine both dictionaries, with user words taking precedence
        // Create a new map with static words first, then overwrite with user words
        val combinedWordData = mutableMapOf<String, Int>()
        combinedWordData.putAll(staticWordData)
        combinedWordData.putAll(userWordData)  // User words will overwrite static words with the same key
        flogDebug { "Combined word data size: ${combinedWordData.size}" }
        flogDebug { "User word data keys: ${userWordData.keys.take(10)}" }  // Log first 10 user words for debugging
        
        if (composingText.isEmpty()) {
            val candidates = combinedWordData.entries
                .sortedByDescending { it.value }
                .take(maxCandidateCount)
                .map { (word, frequency) ->
                    WordSuggestionCandidate(
                        text = word,
                        confidence = frequency / 255.0,
                        isEligibleForAutoCommit = false,
                        sourceProvider = this@LatinLanguageProvider,
                    )
                }
            flogDebug { "Returning ${candidates.size} candidates for empty composing text" }
            return candidates
        }
        
        val exactMatches = mutableListOf<WordSuggestionCandidate>()
        val prefixMatches = mutableListOf<WordSuggestionCandidate>()
        val fuzzyMatches = mutableListOf<WordSuggestionCandidate>()
        val contextMatches = mutableListOf<WordSuggestionCandidate>() // For context-aware suggestions
        
        combinedWordData.forEach { (word, frequency) ->
            val wordLower = word.lowercase()
            val confidence = frequency / 255.0
            
            when {
                wordLower == composingTextLower -> {
                    // Preserve original case when matching exactly
                    val finalWord = if (composingText.firstOrNull()?.isUpperCase() == true) {
                        // If the first character of the input is uppercase, capitalize the suggestion
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(subtype.primaryLocale.base) else it.toString() }
                    } else {
                        word
                    }
                    
                    exactMatches.add(WordSuggestionCandidate(
                        text = finalWord,
                        confidence = 1.0, // Full confidence for exact matches
                        isEligibleForAutoCommit = true,
                        sourceProvider = this@LatinLanguageProvider,
                    ))
                }
                wordLower.startsWith(composingTextLower) -> {
                    // More sophisticated prefix matching with adaptive confidence
                    val prefixLengthRatio = composingTextLower.length.toDouble() / wordLower.length.toDouble()
                    val adjustedConfidence = when {
                        prefixLengthRatio >= 0.8 -> confidence * 0.95 // Very close match
                        prefixLengthRatio >= 0.6 -> confidence * 0.85 // Good match
                        prefixLengthRatio >= 0.4 -> confidence * 0.75 // Fair match
                        else -> confidence * 0.65 // Weak match
                    }
                    
                    // Longer composing texts make matches more confident
                    val lengthBoost = when {
                        composingTextLower.length >= 5 -> 1.1
                        composingTextLower.length >= 3 -> 1.05
                        else -> 1.0
                    }
                    
                    val finalConfidence = (adjustedConfidence * lengthBoost).coerceAtMost(1.0)
                    
                    // Preserve case for prefix matches
                    val finalWord = if (composingText.firstOrNull()?.isUpperCase() == true) {
                        // If the first character of the input is uppercase, capitalize the suggestion
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(subtype.primaryLocale.base) else it.toString() }
                    } else {
                        word
                    }
                    
                    prefixMatches.add(WordSuggestionCandidate(
                        text = finalWord,
                        confidence = finalConfidence,
                        // Make prefix matches eligible for auto-commit based on confidence and length
                        isEligibleForAutoCommit = composingTextLower.length >= 3 && finalConfidence > 0.8,
                        sourceProvider = this@LatinLanguageProvider,
                    ))
                }
                // More lenient fuzzy matching for better correction suggestions
                wordLower.contains(composingTextLower) && composingTextLower.length > 1 -> {
                    // Calculate position-based confidence boost
                    val position = wordLower.indexOf(composingTextLower)
                    val positionBoost = if (position == 0) 1.1 else 1.0 // Beginning match gets boost
                    
                    // Preserve case for fuzzy matches
                    val finalWord = if (composingText.firstOrNull()?.isUpperCase() == true) {
                        // If the first character of the input is uppercase, capitalize the suggestion
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(subtype.primaryLocale.base) else it.toString() }
                    } else {
                        word
                    }
                    
                    fuzzyMatches.add(WordSuggestionCandidate(
                        text = finalWord,
                        confidence = (confidence * 0.7 * positionBoost).coerceAtMost(0.9),
                        isEligibleForAutoCommit = false,
                        sourceProvider = this@LatinLanguageProvider,
                    ))
                }
                // Levenshtein distance-based fuzzy matching for typo correction
                composingTextLower.length > 2 && wordLower.length > 2 -> {
                    val distance = calculateEditDistance(composingTextLower, wordLower)
                    val maxLength = kotlin.math.max(composingTextLower.length, wordLower.length)
                    val similarity = 1.0 - (distance.toDouble() / maxLength.toDouble())
                    
                    // Only consider words with high similarity (80% or better)
                    if (similarity > 0.8) {
                        val adjustedConfidence = (confidence * similarity * 0.8).coerceAtMost(0.95)
                        
                        // Preserve case for typo corrections
                        val finalWord = if (composingText.firstOrNull()?.isUpperCase() == true) {
                            // If the first character of the input is uppercase, capitalize the suggestion
                            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(subtype.primaryLocale.base) else it.toString() }
                        } else {
                            word
                        }
                        
                        fuzzyMatches.add(WordSuggestionCandidate(
                            text = finalWord,
                            confidence = adjustedConfidence,
                            isEligibleForAutoCommit = adjustedConfidence > 0.9, // High confidence typos can auto-commit
                            sourceProvider = this@LatinLanguageProvider,
                        ))
                    }
                }
            }
        }
        
        // Sort matches by confidence
        val allMatches = buildList {
            addAll(exactMatches.sortedByDescending { it.confidence })
            addAll(prefixMatches.sortedByDescending { it.confidence })
            addAll(fuzzyMatches.sortedByDescending { it.confidence })
        }
        
        val result = allMatches.take(maxCandidateCount)
        flogDebug { "Returning ${result.size} candidates for composing text '$composingText'" }
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
        
        // Get words from the user dictionary
        val userWords = try {
            val dictionaryManager = DictionaryManager.default()
            dictionaryManager.loadUserDictionariesIfNecessary()
            val userDictionaryDao = dictionaryManager.florisUserDictionaryDao()
            
            // flogDebug { "UserDictionaryDao in getListOfWords: $userDictionaryDao" }
            
            if (userDictionaryDao != null) {
                // Query all words from the user dictionary for this subtype
                val userWordsList = userDictionaryDao.queryAll(subtype.primaryLocale).map { it.word }
                // flogDebug { "Retrieved ${userWordsList.size} words from user dictionary for locale ${subtype.primaryLocale}" }
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
        // flogDebug { "Returning combined word list: ${userWords.size} user words + ${staticWords.size} static words" }
        return userWords + staticWords
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        flogDebug { "getFrequencyForWord called with word: '$word'" }
        
        // First check if it's in the user dictionary (higher priority)
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
        
        // If not in user dictionary, check static dictionary
        val staticFrequency = wordData.withLock { it.getOrDefault(word, 0) / 255.0 }
        flogDebug { "Word '$word' frequency from static dictionary: $staticFrequency" }
        return staticFrequency
    }

    override suspend fun destroy() {
        // Cleanup
    }
}
