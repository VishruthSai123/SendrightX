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

object MagicWandInstructions {
    
    // Enhance Section Instructions
    const val REPHRASE = "Fix any grammar, spelling, and punctuation errors in the given text while preserving the original meaning, tone, and style. Improve sentence structure for better flow and readability without making the text more verbose or changing the language. Only correct mistakes and enhance clarity minimally. Only provide the corrected text."
    
    const val GRAMMAR_FIX = "Correct grammar, spelling, and punctuation errors in the given text while keeping the original meaning and tone unchanged. Only fix grammatical mistakes without changing the style or making it more verbose. Only provide the corrected text."
    
    const val REALISTIC = "Make the given text sound more human-like, natural, and conversational. Remove any robotic or artificial language patterns. Make it flow naturally as if spoken by a real person. Only provide the enhanced text."

    // Advanced Section Instructions
    const val REWRITE = "Fix any grammar, spelling, and punctuation errors in the given text while preserving the original meaning, tone, and style. Improve sentence structure for better flow and readability without making the text more verbose or changing the language. Only correct mistakes and enhance clarity minimally. Only provide the corrected text."

    const val SUMMARISE = "Summarise the given text briefly, keeping only the key points. Only provide the summary."

    const val LETTER = "Convert the given text into a properly formatted letter , it can be mostly as email formatted. Only provide the letter text."

    const val OPTIMISE = "Optimise the given text for conciseness and impact , espessially for AI's LLM's etc... , Only provide the optimised text."

    const val FORMAL = "Rewrite the given text in a formal tone. Only provide the formal version."

    const val POST_READY = "Convert the given text into a social media post , make it unique, attractive , even add emojies if needed. only give consisly , avoid being verbose , . Only provide the post text."
    
    // Study Section Instructions
    const val EXPLAIN = "Explain the given text in simple, easy-to-understand words. Only provide the explanation."

    const val EQUATION = "Convert mathematical expressions to proper Unicode format. Transform words to symbols: 'square' to '²', 'cube' to '³', 'power of 4' to '⁴', 'power of 5' to '⁵', 'sqrt' or 'square root' to '√', 'plus' to '+', 'minus' to '-', 'times' or 'multiply' to '×', 'divide' to '÷', 'equals' to '=', 'not equal' to '≠', 'less than or equal' to '≤', 'greater than or equal' to '≥', 'approximately' to '≈', 'infinity' to '∞', 'pi' to 'π', 'theta' to 'θ', 'alpha' to 'α', 'beta' to 'β', 'gamma' to 'γ', 'delta' to 'δ', 'sigma' to 'σ'. Only provide the formatted equation."

    const val SOLUTION = "Provide a detailed step-by-step solution for this academic problem. Identify the subject area (mathematics, physics, chemistry, biology, etc.) and solve accordingly. For math problems: show all calculation steps, formulas used, and intermediate results. For science problems: explain the concepts, apply relevant formulas, and show work. Use Unicode mathematical symbols (², ³, √, ±, ×, ÷, ≈, ≠, ≤, ≥, π, etc.) where appropriate. Format as clear text suitable for display in a text field. Only provide the solution."
    
    // Tone Changer Section Instructions
    const val CASUAL = "Rewrite the given text in a casual tone. Only provide the rewritten text."

    const val FRIENDLY = "Rewrite the given text in a friendly and approachable way. Only provide the rewritten text."

    const val PROFESSIONAL = "Rewrite the given text in a professional tone. Only provide the rewritten text."

    const val FLIRTY = "Rewrite the given text in a flirty and playful way. Only provide the rewritten text."

    const val ANGER = "Rewrite the given text expressing anger. Only provide the rewritten text."

    const val HAPPY = "Rewrite the given text in a cheerful and happy tone. Only provide the rewritten text."
    
    // Translation Section Instructions
    // Basic Languages
    const val TELUGU = "Translate the given text into Telugu language , also understand what the given text is while transforming. Only provide the translated text in Telugu script."
    const val TAMIL = "Translate the given text into Tamil language , also understand what the given text is while transforming. Only provide the translated text in Tamil script."
    const val HINDI = "Translate the given text into Hindi language , also understand what the given text is while transforming. Only provide the translated text in Devanagari script."
    const val ENGLISH = "Translate the given text into English language , also understand what the given text is while transforming. Only provide the translated text."

    // Indian Languages
    const val MALAYALAM = "Translate the given text into Malayalam language , also understand what the given text is while transforming. Only provide the translated text in Malayalam script."
    const val KANNADA = "Translate the given text into Kannada language , also understand what the given text is while transforming. Only provide the translated text in Kannada script."
    const val BENGALI = "Translate the given text into Bengali language , also understand what the given text is while transforming. Only provide the translated text in Bengali script."
    const val GUJARATI = "Translate the given text into Gujarati language , also understand what the given text is while transforming. Only provide the translated text in Gujarati script."
    const val MARATHI = "Translate the given text into Marathi language , also understand what the given text is while transforming. Only provide the translated text in Devanagari script."
    const val PUNJABI = "Translate the given text into Punjabi language , also understand what the given text is while transforming. Only provide the translated text in Gurmukhi script."
    const val URDU = "Translate the given text into Urdu language , also understand what the given text is while transforming. Only provide the translated text in Urdu script."
    const val ASSAMESE = "Translate the given text into Assamese language , also understand what the given text is while transforming. Only provide the translated text in Assamese script."

    // International Languages
    const val SPANISH = "Translate the given text into Spanish language , also understand what the given text is while transforming. Only provide the translated text."
    const val FRENCH = "Translate the given text into French language , also understand what the given text is while transforming. Only provide the translated text."
    const val GERMAN = "Translate the given text into German language , also understand what the given text is while transforming. Only provide the translated text."
    const val ITALIAN = "Translate the given text into Italian language , also understand what the given text is while transforming. Only provide the translated text."
    const val PORTUGUESE = "Translate the given text into Portuguese language , also understand what the given text is while transforming. Only provide the translated text."
    const val CHINESE = "Translate the given text into Simplified Chinese language , also understand what the given text is while transforming. Only provide the translated text in Chinese characters."
    const val JAPANESE = "Translate the given text into Japanese language , also understand what the given text is while transforming. Only provide the translated text in Japanese script."
    const val KOREAN = "Translate the given text into Korean language , also understand what the given text is while transforming. Only provide the translated text in Korean script."
    const val ARABIC = "Translate the given text into Arabic language , also understand what the given text is while transforming. Only provide the translated text in Arabic script."
    const val RUSSIAN = "Translate the given text into Russian language , also understand what the given text is while transforming. Only provide the translated text in Cyrillic script."
    const val MALAY = "Translate the given text into Malay language , also understand what the given text is while transforming. Only provide the translated text."
    const val DUTCH = "Translate the given text into Dutch language , also understand what the given text is while transforming. Only provide the translated text."
    const val SWEDISH = "Translate the given text into Swedish language , also understand what the given text is while transforming. Only provide the translated text."
    const val NORWEGIAN = "Translate the given text into Norwegian language , also understand what the given text is while transforming. Only provide the translated text."
    const val POLISH = "Translate the given text into Polish language , also understand what the given text is while transforming. Only provide the translated text."
    const val TURKISH = "Translate the given text into Turkish language , also understand what the given text is while transforming. Only provide the translated text."
    const val GREEK = "Translate the given text into Greek language , also understand what the given text is while transforming. Only provide the translated text in Greek script."
    const val HEBREW = "Translate the given text into Hebrew language , also understand what the given text is while transforming. Only provide the translated text in Hebrew script."
    const val THAI = "Translate the given text into Thai language , also understand what the given text is while transforming. Only provide the translated text in Thai script."
    const val VIETNAMESE = "Translate the given text into Vietnamese language , also understand what the given text is while transforming. Only provide the translated text."

    const val MULTI = "Translate the text as specified in the user's request. The user will provide the target language in their message (e.g., 'Translate Hello to Telugu'). Only provide the translated text in the requested language and script."
    
    // Other Section Instructions
    const val EMOJIE = "Add relevant emojis to the given text. Only provide the updated text."

    const val CHAT = "You are a helpful AI assistant integrated into a keyboard app. Respond naturally and directly to the user's input without any prefixes or extra text. Provide helpful, concise responses. The user is asking about or wants help with the selected text or current word. Be conversational but stay focused on the user's query."
    
    fun getInstructionForButton(buttonTitle: String): String {
        return when (buttonTitle) {
            // Enhance Section
            "Rephrase" -> REPHRASE
            "Grammar Fix" -> GRAMMAR_FIX
            "Realistic" -> REALISTIC
            
            // Advanced Section
            "Rewrite" -> REWRITE
            "Summarise" -> SUMMARISE
            "Letter" -> LETTER
            "Optimise" -> OPTIMISE
            "Formal" -> FORMAL
            "Post Ready" -> POST_READY
            
            // Study Section
            "Explain" -> EXPLAIN
            "Equation" -> EQUATION
            "Solution" -> SOLUTION
            
            // Tone Changer Section
            "Casual" -> CASUAL
            "Friendly" -> FRIENDLY
            "Professional" -> PROFESSIONAL
            "Flirty" -> FLIRTY
            "Anger" -> ANGER
            "Happy" -> HAPPY
            
            // Translation Section - Basic Languages
            "Telugu" -> TELUGU
            "Tamil" -> TAMIL
            "Hindi" -> HINDI
            "English" -> ENGLISH
            
            // Translation Section - Indian Languages
            "Malayalam" -> MALAYALAM
            "Kannada" -> KANNADA
            "Bengali" -> BENGALI
            "Gujarati" -> GUJARATI
            "Marathi" -> MARATHI
            "Punjabi" -> PUNJABI
            "Urdu" -> URDU
            "Assamese" -> ASSAMESE
            
            // Translation Section - International Languages
            "Spanish" -> SPANISH
            "French" -> FRENCH
            "German" -> GERMAN
            "Italian" -> ITALIAN
            "Portuguese" -> PORTUGUESE
            "Chinese" -> CHINESE
            "Japanese" -> JAPANESE
            "Korean" -> KOREAN
            "Arabic" -> ARABIC
            "Russian" -> RUSSIAN
            "Malay" -> MALAY
            "Dutch" -> DUTCH
            "Swedish" -> SWEDISH
            "Norwegian" -> NORWEGIAN
            "Polish" -> POLISH
            "Turkish" -> TURKISH
            "Greek" -> GREEK
            "Hebrew" -> HEBREW
            "Thai" -> THAI
            "Vietnamese" -> VIETNAMESE
            
            // Special Translation
            "Multi" -> MULTI
            
            // Other Section
            "Emojie" -> EMOJIE
            "Chat" -> CHAT
            
            else -> "Transform the following text appropriately."
        }
    }
}
