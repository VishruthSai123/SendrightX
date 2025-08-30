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
import com.vishruth.key1.ime.editor.EditorContent
import com.vishruth.key1.ime.nlp.SpellingProvider
import com.vishruth.key1.ime.nlp.SpellingResult
import com.vishruth.key1.ime.nlp.SuggestionCandidate
import com.vishruth.key1.ime.nlp.SuggestionProvider
import com.vishruth.key1.ime.nlp.WordSuggestionCandidate
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
                wordData.containsKey(wordLower) || wordData.containsKey(word) -> {
                    SpellingResult.validWord()
                }
                wordLower.length > 2 -> {
                    val suggestions = mutableListOf<String>()
                    
                    wordData.keys.forEach { dictWord ->
                        val distance = calculateEditDistance(wordLower, dictWord.lowercase())
                        if (distance <= 2 && distance > 0) {
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
                        SpellingResult.validWord()
                    }
                }
                else -> SpellingResult.validWord()
            }
        }
    }
    
    private fun calculateEditDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        val matrix = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) matrix[i][0] = i
        for (j in 0..len2) matrix[0][j] = j
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,
                    matrix[i][j - 1] + 1,
                    matrix[i - 1][j - 1] + cost
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
        
        return wordData.withLock { wordData ->
            val exactMatches = mutableListOf<WordSuggestionCandidate>()
            val prefixMatches = mutableListOf<WordSuggestionCandidate>()
            val fuzzyMatches = mutableListOf<WordSuggestionCandidate>()
            
            wordData.forEach { (word, frequency) ->
                val wordLower = word.lowercase()
                val confidence = frequency / 255.0
                
                when {
                    wordLower == composingText -> {
                        exactMatches.add(WordSuggestionCandidate(
                            text = word,
                            confidence = confidence,
                            isEligibleForAutoCommit = true,
                            sourceProvider = this@LatinLanguageProvider,
                        ))
                    }
                    wordLower.startsWith(composingText) -> {
                        prefixMatches.add(WordSuggestionCandidate(
                            text = word,
                            confidence = confidence * 0.9,
                            isEligibleForAutoCommit = false,
                            sourceProvider = this@LatinLanguageProvider,
                        ))
                    }
                    wordLower.contains(composingText) && composingText.length > 2 -> {
                        fuzzyMatches.add(WordSuggestionCandidate(
                            text = word,
                            confidence = confidence * 0.7,
                            isEligibleForAutoCommit = false,
                            sourceProvider = this@LatinLanguageProvider,
                        ))
                    }
                }
            }
            
            val allMatches = buildList {
                addAll(exactMatches.sortedByDescending { it.confidence })
                addAll(prefixMatches.sortedByDescending { it.confidence })
                addAll(fuzzyMatches.sortedByDescending { it.confidence })
            }
            
            allMatches.take(maxCandidateCount)
        }
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
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
        // Cleanup
    }
}
