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

package com.vishruth.sendright.ime.smartbar

object MagicWandInstructions {
    
    // Advanced Section Instructions
    const val REWRITE = "Rewrite the following text to make it clearer, more engaging, and better structured while maintaining the original meaning and tone."
    
    const val SUMMARISE = "Summarize the following text into a concise version that captures the main points and key information."
    
    const val EXPLAIN = "Explain the following text in simpler terms that are easy to understand, breaking down complex concepts if necessary."
    
    const val LETTER = "Transform the following text into a formal letter format with proper greeting, body, and closing."
    
    const val OPTIMISE = "Optimize the following text for clarity, conciseness, and impact while preserving the original message."
    
    const val FORMAL = "Rewrite the following text in a formal, professional tone suitable for business or academic contexts."
    
    const val POST_READY = "Transform the following text into an engaging social media post with appropriate hashtags and emojis."
    
    // Tone Changer Section Instructions
    const val CASUAL = "Rewrite the following text in a casual, friendly, and conversational tone."
    
    const val FRIENDLY = "Rewrite the following text in a warm, welcoming, and friendly tone."
    
    const val PROFESSIONAL = "Rewrite the following text in a professional, business-appropriate tone."
    
    const val FLIRTY = "Rewrite the following text in a playful, charming, and flirtatious tone."
    
    const val ANGER = "Rewrite the following text to express frustration or displeasure in a strong but appropriate way."
    
    const val HAPPY = "Rewrite the following text in an enthusiastic, positive, and joyful tone."
    
    // Other Section Instructions
    const val EMOJIE = "Add relevant emojis to the following text to make it more expressive and engaging."
    
    const val TRANSLATE = "Translate the following text to English if it's in another language, or to the most appropriate language if it's already in English."
    
    const val ASK = "Transform the following text into a polite question or inquiry."
    
    fun getInstructionForButton(buttonTitle: String): String {
        return when (buttonTitle) {
            "Rewrite" -> REWRITE
            "Summarise" -> SUMMARISE
            "Explain" -> EXPLAIN
            "Letter" -> LETTER
            "Optimise" -> OPTIMISE
            "Formal" -> FORMAL
            "Post Ready" -> POST_READY
            "Casual" -> CASUAL
            "Friendly" -> FRIENDLY
            "Professional" -> PROFESSIONAL
            "Flirty" -> FLIRTY
            "Anger" -> ANGER
            "Happy" -> HAPPY
            "Emojie" -> EMOJIE
            "Translate" -> TRANSLATE
            "Ask" -> ASK
            else -> "Transform the following text appropriately."
        }
    }
}
