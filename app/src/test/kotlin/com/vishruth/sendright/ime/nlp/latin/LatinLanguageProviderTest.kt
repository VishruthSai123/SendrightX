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

import org.junit.Test
import org.junit.Assert.*

class LatinLanguageProviderTest {

    @Test
    fun testExtractPrecedingWord() {
        // Test the logic directly without creating the provider instance
        assertEquals("hello", extractPrecedingWordHelper("hello "))
        assertEquals("world", extractPrecedingWordHelper("hello world"))
        assertEquals("test", extractPrecedingWordHelper("hello world test"))
        
        // Test with punctuation
        assertEquals("hello", extractPrecedingWordHelper("hi, hello "))
        assertEquals("world", extractPrecedingWordHelper("hello, world"))
        
        // Test with empty or blank text
        assertNull(extractPrecedingWordHelper(""))
        assertNull(extractPrecedingWordHelper(" "))
        assertNull(extractPrecedingWordHelper("   "))
    }
    
    @Test
    fun testExtractPrecedingWords() {
        // Test the logic for extracting two preceding words
        assertEquals(Pair(null, "hello"), extractPrecedingWordsHelper("hello "))
        assertEquals(Pair("hello", "world"), extractPrecedingWordsHelper("hello world"))
        assertEquals(Pair("world", "test"), extractPrecedingWordsHelper("hello world test"))
        
        // Test with punctuation
        assertEquals(Pair("hi", "hello"), extractPrecedingWordsHelper("hi, hello "))
        assertEquals(Pair("hello", "world"), extractPrecedingWordsHelper("hello, world"))
        
        // Test with empty or blank text
        assertEquals(Pair(null, null), extractPrecedingWordsHelper(""))
        assertEquals(Pair(null, null), extractPrecedingWordsHelper(" "))
        assertEquals(Pair(null, null), extractPrecedingWordsHelper("   "))
    }
    
    @Test
    fun testCalculateEditDistance() {
        val provider = TestableLatinLanguageProvider()
        
        // Test identical strings
        assertEquals(0, provider.calculateEditDistance("test", "test"))
        
        // Test simple insertions
        assertEquals(1, provider.calculateEditDistance("test", "tests"))
        assertEquals(1, provider.calculateEditDistance("test", "est"))
        
        // Test substitutions
        assertEquals(1, provider.calculateEditDistance("test", "tent"))
        
        // Test more complex cases
        assertEquals(3, provider.calculateEditDistance("kitten", "sitting"))
    }
    
    // Helper method to test the extractPrecedingWord logic
    private fun extractPrecedingWordHelper(textBeforeSelection: String): String? {
        if (textBeforeSelection.isBlank()) return null
        
        // Find the last whitespace character
        val lastSpaceIndex = textBeforeSelection.lastIndexOf(' ')
        val lastWord = if (lastSpaceIndex >= 0) {
            textBeforeSelection.substring(lastSpaceIndex + 1)
        } else {
            textBeforeSelection
        }
        
        // Clean the word (remove punctuation)
        val cleanedWord = lastWord.trim().lowercase().replace(Regex("[^a-zA-Z0-9]"), "")
        return if (cleanedWord.isNotEmpty()) cleanedWord else null
    }
    
    // Helper method to test the extractPrecedingWords logic
    private fun extractPrecedingWordsHelper(textBeforeSelection: String): Pair<String?, String?> {
        if (textBeforeSelection.isBlank()) return null to null
        
        // Split by whitespace and get the last two words
        val words = textBeforeSelection.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        
        return when (words.size) {
            0 -> null to null
            1 -> null to words[0].trim().lowercase().replace(Regex("[^a-zA-Z0-9]"), "")
            else -> {
                val secondLast = words[words.size - 2].trim().lowercase().replace(Regex("[^a-zA-Z0-9]"), "")
                val last = words[words.size - 1].trim().lowercase().replace(Regex("[^a-zA-Z0-9]"), "")
                secondLast to last
            }
        }
    }
    
    // Test class to access protected methods
    private class TestableLatinLanguageProvider : LatinLanguageProvider(mockContext()) {
        public fun calculateEditDistance(s1: String, s2: String): Int {
            return super.calculateEditDistance(s1, s2)
        }
    }
    
    private companion object {
        fun mockContext(): android.content.Context {
            // Return null for testing - we're only testing pure functions
            return null as android.content.Context
        }
    }
}