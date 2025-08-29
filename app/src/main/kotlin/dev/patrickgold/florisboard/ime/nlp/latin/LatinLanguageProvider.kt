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

package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.Context
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.nlp.SpellingProvider
import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.readText
import org.florisboard.lib.kotlin.guardedByLock

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.florisboard.nlp.providers.latin"
    }

    private val appContext by context.appContext()

    private val wordData = guardedByLock { mutableMapOf<String, Int>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())

    override val providerId = ProviderId

    override suspend fun create() {
        // Here we initialize our provider, set up all things which are not language dependent.
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        // Here we have the chance to preload dictionaries and prepare a neural network for a specific language.
        // Is kept in sync with the active keyboard subtype of the user, however a new preload does not necessary mean
        // the previous language is not needed anymore (e.g. if the user constantly switches between two subtypes)

        // To read a file from the APK assets the following methods can be used:
        // appContext.assets.open()
        // appContext.assets.reader()
        // appContext.assets.bufferedReader()
        // appContext.assets.readText()
        // To copy an APK file/dir to the file system cache (appContext.cacheDir), the following methods are available:
        // appContext.assets.copy()
        // appContext.assets.copyRecursively()

        // The subtype we get here contains a lot of data, however we are only interested in subtype.primaryLocale and
        // subtype.secondaryLocales.

        wordData.withLock { wordData ->
            if (wordData.isEmpty()) {
                // Here we use readText() because the test dictionary is a json dictionary
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
        
        // Check if word exists in our dictionary
        return wordData.withLock { wordData ->
            when {
                // Word exists in dictionary - it's valid
                wordData.containsKey(wordLower) || wordData.containsKey(word) -> {
                    SpellingResult.validWord()
                }
                // Check for common typos and provide corrections
                wordLower.length > 2 -> {
                    val suggestions = mutableListOf<String>()
                    
                    // Find similar words by edit distance
                    wordData.keys.forEach { dictWord ->
                        val distance = calculateEditDistance(wordLower, dictWord.lowercase())
                        if (distance <= 2 && distance > 0) { // Allow up to 2 character differences
                            suggestions.add(dictWord)
                        }
                    }
                    
                    // Sort suggestions by frequency and limit count
                    val sortedSuggestions = suggestions
                        .sortedByDescending { wordData[it] ?: 0 }
                        .take(maxSuggestionCount)
                        .toTypedArray()
                    
                    if (sortedSuggestions.isNotEmpty()) {
                        SpellingResult.typo(sortedSuggestions)
                    } else {
                        SpellingResult.validWord() // No suggestions found, assume it's valid
                    }
                }
                // Short words or empty - assume valid
                else -> SpellingResult.validWord()
            }
        }
    }
    
    /**
     * Calculate edit distance (Levenshtein distance) between two strings
     */
    private fun calculateEditDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        // Create a matrix to store the distances
        val matrix = Array(len1 + 1) { IntArray(len2 + 1) }
        
        // Initialize first row and column
        for (i in 0..len1) matrix[i][0] = i
        for (j in 0..len2) matrix[0][j] = j
        
        // Fill the matrix
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,      // deletion
                    matrix[i][j - 1] + 1,      // insertion
                    matrix[i - 1][j - 1] + cost // substitution
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
        val composingText = content.composingText.toString().lowercase().trim()
        
        // If there's no composing text, suggest most common words
        if (composingText.isEmpty()) {
            return wordData.withLock { wordData ->
                wordData.entries
                    .sortedByDescending { it.value }
                    .take(maxCandidateCount.coerceAtMost(3))
                    .map { (word, frequency) ->
                        WordSuggestionCandidate(
                            text = word,
                            confidence = frequency / 255.0,
                            isEligibleForAutoCommit = false,
                            sourceProvider = this@LatinLanguageProvider,
                        )
                    }
            }
        }
        
        // Find words that start with the composing text
        return wordData.withLock { wordData ->
            val exactMatches = mutableListOf<WordSuggestionCandidate>()
            val prefixMatches = mutableListOf<WordSuggestionCandidate>()
            val fuzzyMatches = mutableListOf<WordSuggestionCandidate>()
            
            wordData.forEach { (word, frequency) ->
                val wordLower = word.lowercase()
                val confidence = frequency / 255.0
                
                when {
                    // Exact match
                    wordLower == composingText -> {
                        exactMatches.add(WordSuggestionCandidate(
                            text = word,
                            confidence = confidence,
                            isEligibleForAutoCommit = true,
                            sourceProvider = this@LatinLanguageProvider,
                        ))
                    }
                    // Prefix match
                    wordLower.startsWith(composingText) -> {
                        prefixMatches.add(WordSuggestionCandidate(
                            text = word,
                            confidence = confidence * 0.9, // Slightly lower confidence for prefix
                            isEligibleForAutoCommit = false,
                            sourceProvider = this@LatinLanguageProvider,
                        ))
                    }
                    // Fuzzy match (contains the text)
                    wordLower.contains(composingText) && composingText.length > 2 -> {
                        fuzzyMatches.add(WordSuggestionCandidate(
                            text = word,
                            confidence = confidence * 0.7, // Lower confidence for fuzzy match
                            isEligibleForAutoCommit = false,
                            sourceProvider = this@LatinLanguageProvider,
                        ))
                    }
                }
            }
            
            // Sort by confidence and combine results
            val allMatches = buildList {
                addAll(exactMatches.sortedByDescending { it.confidence })
                addAll(prefixMatches.sortedByDescending { it.confidence })
                addAll(fuzzyMatches.sortedByDescending { it.confidence })
            }
            
            allMatches.take(maxCandidateCount)
        }
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        // We can use flogDebug, flogInfo, flogWarning and flogError for debug logging, which is a wrapper for Logcat
        flogDebug { candidate.toString() }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        return false
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return wordData.withLock { it.keys.toList() }
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return wordData.withLock { it.getOrDefault(word, 0) / 255.0 }
    }

    override suspend fun destroy() {
        // Here we have the chance to de-allocate memory and finish our work. However this might never be called if
        // the app process is killed (which will most likely always be the case).
    }
}
