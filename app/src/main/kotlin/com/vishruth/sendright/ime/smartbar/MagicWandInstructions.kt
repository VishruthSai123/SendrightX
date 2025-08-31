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
    
    // Advanced Section Instructions
    const val REWRITE = "Rewrite the given text with better clarity and flow. Only provide the rewritten text."

    const val SUMMARISE = "Summarise the given text briefly, keeping only the key points. Only provide the summary."

    const val LETTER = "Convert the given text into a properly formatted letter. Only provide the letter text."

    const val OPTIMISE = "Optimise the given text for conciseness and impact. Only provide the optimised text."

    const val FORMAL = "Rewrite the given text in a formal tone. Only provide the formal version."

    const val POST_READY = "Convert the given text into a social media post. Only provide the post text."
    
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
    
    // Other Section Instructions
    const val EMOJIE = "Add relevant emojis to the given text. Only provide the updated text."

    const val TRANSLATE = "Translate the given text into the target language. Only provide the translated text."

    const val ASK = "Answer the given question directly and clearly. Only provide the answer."
    
    fun getInstructionForButton(buttonTitle: String): String {
        return when (buttonTitle) {
            "Rewrite" -> REWRITE
            "Summarise" -> SUMMARISE
            "Letter" -> LETTER
            "Optimise" -> OPTIMISE
            "Formal" -> FORMAL
            "Post Ready" -> POST_READY
            "Explain" -> EXPLAIN
            "Equation" -> EQUATION
            "Solution" -> SOLUTION
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
